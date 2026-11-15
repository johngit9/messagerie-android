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
        val erreurs = ArrayList<String>()
        for (u in (listOfNotNull(derniereBonne) + cfg.teamsAdresses).distinct()) {
            try {
                val code = Http.requete("$u/teams/etat", jeton = jeton, delaiMs = 5_000).code
                if (code == 200) { base = u; derniereBonne = u; return }
                erreurs += "$u : " + when (code) { 401 -> "jeton refusé (401)"; 404 -> "pas de service Teams (404)"; else -> "erreur $code" }
            } catch (e: Exception) { erreurs += "$u : " + (e.message ?: e.javaClass.simpleName) }
        }
        derniereBonne = null
        throw Injoignable("service Teams injoignable — " + erreurs.joinToString(" ; "))
    }

    /** Test depuis les Réglages : résultat lisible pour chaque adresse essayée. */
    fun tester(): String = try {
        connecter()
        val j = get("/teams/etat", 8_000)
        val l = j.optJSONObject("lecture")
        "✅ $base : ${l?.optInt("conversations") ?: "?"} conversations, ${l?.optInt("messages") ?: "?"} messages"
    } catch (e: Exception) { "⚠️ " + (e.message ?: e.toString()) }

    private fun get(chemin: String, delaiMs: Int = 20_000): JSONObject {
        val r = Http.requete(base + chemin, jeton = jeton, delaiMs = delaiMs)
        if (r.code != 200) throw ErreurHttp(r.code, chemin.substringBefore('?'))
        return JSONObject(r.texte)
    }

    /** Recopie dans l'archive tout ce que le service a de nouveau (nouveaux, modifiés, supprimés). */
    fun synchroniser(archive: Archive): Int {
        connecter()
        // Archive sans Teams (premier chargement ou numérotation refaite côté serveur) : on repart de zéro.
        var apres = if (archive.aDesConversations(Source.TEAMS)) archive.curseur("teams.seq")?.toLongOrNull() ?: 0 else 0
        var total = 0
        repeat(200) {
            val j = get("/teams/messages?apres=$apres&limite=500", 30_000)
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
            val suivant = j.optLong("dernier", apres)
            // Curseur plus loin que le serveur (renumérotation) : on recommence depuis le début au prochain passage.
            if (suivant < apres) { archive.definirCurseur("teams.seq", "0"); return total }
            val avance = suivant > apres
            apres = suivant
            archive.definirCurseur("teams.seq", apres.toString())
            if (!j.optBoolean("encore") || !avance) return total
        }
        return total
    }
}
