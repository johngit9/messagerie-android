package fr.messagerie.unifiee

import org.json.JSONObject

/**
 * Relais Element (sur votre serveur) : garde la session chiffrée et sert l'historique déchiffré.
 * Ici on le recopie par morceaux (curseur « seq »). Le relais n'envoie jamais d'accusé de lecture.
 */
class Relais(private val cfg: Config) {
    companion object {
        const val MAX_PAR_PASSAGE = 6_000
        @Volatile var derniereBonne: String? = null
        @Volatile private var verifieLe = 0L
    }

    private var base = ""
    private val jeton get() = cfg.relaisJeton

    fun connecter() {
        if (!cfg.relaisConfigure) throw Injoignable("relais non configuré")
        derniereBonne?.let { if (System.currentTimeMillis() - verifieLe < 120_000) { base = it; return } }
        for (u in (listOfNotNull(derniereBonne) + Config.liste(cfg.relaisUrls)).distinct()) {
            try {
                if (Http.requete("$u/etat", jeton = jeton, delaiMs = 5_000).code == 200) { base = u; derniereBonne = u; verifieLe = System.currentTimeMillis(); return }
            } catch (_: Exception) {}
        }
        derniereBonne = null
        throw Injoignable("relais injoignable : le serveur est-il allumé ?")
    }

    private fun get(chemin: String, delaiMs: Int = 15_000): JSONObject {
        val r = Http.requete(base + chemin, jeton = jeton, delaiMs = delaiMs)
        if (r.code != 200) throw ErreurHttp(r.code, chemin.substringBefore('?'))
        return JSONObject(r.texte)
    }

    /** Recopie dans l'archive tout ce que le relais a de nouveau. */
    fun synchroniser(archive: Archive): Int {
        connecter()
        runCatching { archive.purgerEchosElement() }
        var apres = archive.curseur("element.seq")?.toLongOrNull() ?: 0
        var total = 0
        while (true) {
            val j = get("/messages?apres=$apres&limite=2000", 30_000)
            val lignes = j.optJSONArray("messages")?.objets() ?: break
            if (lignes.isEmpty()) break
            val convs = HashMap<String, Conversation>()
            val lot = ArrayList<Message>()
            val supprimes = ArrayList<String>()
            for (l in lignes) {
                // « ~… » = écho provisoire d'un message envoyé : le vrai message arrive ensuite
                val eid = l.optString("event_id").takeIf { it.isNotEmpty() && !it.startsWith("~") } ?: continue
                val salon = l.optString("salon").takeIf { it.isNotEmpty() } ?: continue
                val cle = "Element:$eid"
                if (l.optInt("supprime") == 1) { supprimes.add(cle); continue }
                val conv = Conversation(cleConv(Source.ELEMENT, salon), Source.ELEMENT, salon,
                    l.optString("nom_salon").takeIf { it.isNotEmpty() && it != "null" } ?: "Element",
                    l.optInt("est_groupe") == 1, 0, emptyList())
                convs[conv.cle] = conv
                val type = l.optString("type").ifEmpty { "texte" }.let { if (it == "position") "autre" else it }
                var duree: Double? = null
                var media: String? = null
                l.optString("media").takeIf { it.isNotEmpty() && it != "null" }?.let { brut ->
                    runCatching { JSONObject(brut) }.getOrNull()?.let { md ->
                        duree = md.ts("duree")?.let { it / 1000.0 }
                        val mime = md.optString("mimetype").ifEmpty { if (type == "audio") "audio/ogg" else "application/octet-stream" }
                        media = "element:$eid#$mime#" + md.optString("nom").replace("#", "")
                    }
                }
                val deMoi = l.optInt("de_moi") == 1
                lot.add(Message(cle, conv.cle, l.ts("ts") ?: 0, deMoi,
                    if (deMoi) "Vous" else l.optString("auteur").ifEmpty { "?" }, l.optString("texte"), type, media, duree))
            }
            archive.enregistrerConversations(convs.values.toList())
            archive.enregistrerMessages(lot)
            archive.supprimerMessages(supprimes)
            total += lot.size
            apres = j.optLong("dernier", apres)
            archive.definirCurseur("element.seq", apres.toString())
            if (lignes.size < 2000 || total >= MAX_PAR_PASSAGE) break
        }
        return total
    }

    /** Position de lecture sur vos autres appareils Element : conversation → date. */
    fun lus(): Pair<Int, Map<String, Long>> {
        connecter()
        val j = get("/lus")
        val r = HashMap<String, Long>()
        for (l in j.optJSONArray("salons")?.objets() ?: emptyList()) {
            val salon = l.optString("salon").takeIf { it.isNotEmpty() } ?: continue
            r[cleConv(Source.ELEMENT, salon)] = l.ts("ts") ?: continue
        }
        return j.optInt("lus_version") to r
    }

    /** Attente longue : le relais répond dès qu'un message arrive ou que vos lectures changent. */
    fun attendre(apres: Long, lusVersion: Int?): Pair<Boolean, Int> {
        connecter()
        var chemin = "/messages?apres=$apres&attendre=25&limite=1"
        if (lusVersion != null) chemin += "&lus_version=$lusVersion"
        val j = get(chemin, 45_000)
        val v = if (j.has("lus_version")) j.optInt("lus_version") else (lusVersion ?: 0)
        val msgs = j.optJSONArray("messages")?.length() ?: 0
        return (msgs > 0 || (lusVersion != null && v != lusVersion)) to v
    }

    /** Salons de la passerelle WhatsApp → identifiant WhatsApp (numéro@s.whatsapp.net ou groupe@g.us). */
    fun salonsWhatsApp(): Map<String, String> {
        connecter()
        val r = HashMap<String, String>()
        for (s in get("/whatsapp/salons").optJSONArray("salons")?.objets() ?: emptyList()) {
            val salon = s.optString("salon"); val jid = s.optString("jid")
            if (salon.isNotEmpty() && jid.isNotEmpty()) r[salon] = jid
        }
        return r
    }

    /** Télécharge (et déchiffre côté relais) le média d'un message Element — lecture seule. */
    fun media(eventId: String): ByteArray {
        connecter()
        val r = Http.requete("$base/media?event=${Http.enc(eventId)}", jeton = jeton, delaiMs = 30_000)
        if (r.code != 200) throw ErreurHttp(r.code, "média")
        return r.corps
    }

    /** Envoie une réponse Element — UNIQUEMENT sur le bouton « Envoyer ». */
    fun envoyer(salon: String, texte: String, reponseA: String?) {
        connecter()
        val d = JSONObject().put("salon", salon).put("texte", texte)
        if (reponseA != null) d.put("reponse_a", reponseA)
        val r = Http.requete("$base/envoyer", "POST", jeton, d.toString(), 20_000)
        if (r.code != 200) throw ErreurHttp(r.code, "envoi")
    }

    /**
     * Marque lu dans Element, en PRIVÉ seulement (m.read.private / m.fully_read) :
     * vos contacts ne voient pas que vous avez lu. Appelé quand vous lisez dans Messagerie.
     */
    fun luPrive(salon: String, eventId: String) {
        connecter()
        val d = JSONObject().put("salon", salon).put("event_id", eventId)
        val r = Http.requete("$base/lu-prive", "POST", jeton, d.toString(), 10_000)
        if (r.code != 200) throw ErreurHttp(r.code, "lu privé")
    }

    /** Qui écrit dans ce salon Element (lecture seule ; rien n'est envoyé). null si le relais ne le sait pas. */
    fun saisie(salon: String): List<String>? {
        connecter()
        val r = Http.requete("$base/saisie?salon=${Http.enc(salon)}", jeton = jeton, delaiMs = 8_000)
        if (r.code != 200) return null
        return JSONObject(r.texte).optJSONArray("ecrivent")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("nom")?.ifEmpty { null } }
        }
    }

    /** Votre statut de présence Element (offline / unavailable / online), choisi dans les Réglages. */
    fun presence(etat: String) {
        connecter()
        val r = Http.requete("$base/presence", "POST", jeton, JSONObject().put("presence", etat).toString(), 10_000)
        if (r.code != 200) throw ErreurHttp(r.code, "présence")
    }

    /** Supprime pour tout le monde un de VOS messages Element — UNIQUEMENT après votre confirmation. */
    fun supprimer(salon: String, eventId: String) {
        connecter()
        val r = Http.requete("$base/supprimer", "POST", jeton, JSONObject().put("salon", salon).put("event_id", eventId).toString(), 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "suppression")
    }

    /** Pose votre réaction (emoji) sur un message Element — UNIQUEMENT sur votre appui. Renvoie l'identifiant de la réaction. */
    fun reagir(salon: String, eventId: String, emoji: String): String {
        connecter()
        val d = JSONObject().put("salon", salon).put("event_id", eventId).put("cle", emoji)
        val r = Http.requete("$base/reagir", "POST", jeton, d.toString(), 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "réaction")
        return runCatching { JSONObject(r.texte).optString("event_id") }.getOrDefault("")
    }

    /** Retire votre réaction Element (identifiant de la réaction) — UNIQUEMENT sur votre appui. */
    fun retirerReaction(salon: String, idReaction: String) {
        connecter()
        val r = Http.requete("$base/retirer-reaction", "POST", jeton, JSONObject().put("salon", salon).put("event_id", idReaction).toString(), 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "retrait de réaction")
    }

    /** Présence de l'autre membre de chaque salon Element à deux (lecture seule) : salon → online / unavailable / offline. */
    fun presences(): Map<String, String> {
        connecter()
        val o = get("/presences", 10_000).optJSONObject("presences") ?: return emptyMap()
        return o.keys().asSequence().associateWith { k -> o.optJSONObject(k)?.optString("presence").orEmpty() }
    }

    /** État d'une passerelle vu par le serveur : ok (true / false / null si inconnu), détail, depuis quand, vérifié quand. */
    data class EtatServeur(val ok: Boolean?, val detail: String, val depuis: Long?, val verifie: Long?)

    /**
     * Surveillance des passerelles par le serveur (lecture seule : rien n'est envoyé aux robots).
     * Renvoie l'état par messagerie et la date de la dernière sauvegarde de la configuration.
     */
    fun passerelles(): Pair<Map<Source, EtatServeur>, Long?> {
        connecter()
        val j = get("/passerelles", 10_000)
        val o = j.optJSONObject("passerelles") ?: JSONObject()
        val res = HashMap<Source, EtatServeur>()
        for (k in o.keys()) {
            val s = when (k.lowercase()) {
                "whatsapp" -> Source.WHATSAPP; "telegram" -> Source.TELEGRAM; "linkedin" -> Source.LINKEDIN; "signal" -> Source.SIGNAL
                "gmessages", "sms" -> Source.SMS; "element" -> Source.ELEMENT; else -> null
            } ?: continue
            val e = o.optJSONObject(k) ?: continue
            val ok = if (e.isNull("ok") || !e.has("ok")) null else e.optBoolean("ok")
            res[s] = EtatServeur(ok, e.optString("detail"), e.optLong("depuis").takeIf { it > 0 }, e.optLong("verifie").takeIf { it > 0 })
        }
        return res to j.optLong("derniere_sauvegarde").takeIf { it > 0 }
    }
}
