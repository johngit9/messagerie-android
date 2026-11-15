package fr.messagerie.unifiee

import org.json.JSONObject

/**
 * Teams, en LECTURE SEULE, via le service Teams du serveur (même jeton que le relais Element).
 * Recopie par morceaux avec le curseur « seq ». Aucune route d'envoi : on répond depuis l'appli Teams.
 */
class Teams(private val cfg: Config) {
    companion object { @Volatile var derniereBonne: String? = null }

    private var base = ""
    private val jeton get() = cfg.relaisJeton

    fun connecter() {
        if (!cfg.teamsConfigure) throw Injoignable("Teams non configuré")
        for (u in (listOfNotNull(derniereBonne) + Config.liste(cfg.teamsUrls)).distinct()) {
            try {
                if (Http.requete("$u/teams/etat", jeton = jeton, delaiMs = 5_000).code == 200) { base = u; derniereBonne = u; return }
            } catch (_: Exception) {}
        }
        derniereBonne = null
        throw Injoignable("service Teams injoignable")
    }

    private fun get(chemin: String, delaiMs: Int = 20_000): JSONObject {
        val r = Http.requete(base + chemin, jeton = jeton, delaiMs = delaiMs)
        if (r.code != 200) throw ErreurHttp(r.code, chemin.substringBefore('?'))
        return JSONObject(r.texte)
    }

    /** Recopie dans l'archive tout ce que le service a de nouveau (nouveaux, modifiés, supprimés). */
    fun synchroniser(archive: Archive): Int {
        connecter()
        var apres = archive.curseur("teams.seq")?.toLongOrNull() ?: 0
        var total = 0
        repeat(50) {
            val j = get("/teams/messages?apres=$apres&limite=2000", 30_000)
            val lignes = j.optJSONArray("messages")?.objets() ?: emptyList()
            val convs = HashMap<String, Conversation>()
            val lot = ArrayList<Message>()
            val supprimes = ArrayList<String>()
            for (l in lignes) {
                val id = l.optString("id").takeIf { it.isNotEmpty() } ?: continue
                val conv = l.optString("conversation").takeIf { it.isNotEmpty() } ?: continue
                val cle = "Teams:$id"
                if (l.optBoolean("supprime") || l.optInt("supprime") == 1) { supprimes.add(cle); continue }
                val c = Conversation(cleConv(Source.TEAMS, conv), Source.TEAMS, conv,
                    l.optString("nom_conversation").takeIf { it.isNotEmpty() && it != "null" } ?: "Teams",
                    l.optBoolean("est_groupe"), 0, emptyList())
                convs[c.cle] = c
                val deMoi = l.optBoolean("de_moi")
                val type = if (l.optString("type") == "appel") "autre" else "texte"
                lot.add(Message(cle, c.cle, l.ts("ts") ?: 0, deMoi,
                    if (deMoi) "Vous" else l.optString("auteur").ifEmpty { "?" }, l.optString("texte"), type))
            }
            archive.enregistrerConversations(convs.values.toList())
            archive.enregistrerMessages(lot)
            archive.supprimerMessages(supprimes)
            total += lot.size
            apres = j.optLong("dernier", apres)
            archive.definirCurseur("teams.seq", apres.toString())
            if (!j.optBoolean("encore")) return total
        }
        return total
    }
}
