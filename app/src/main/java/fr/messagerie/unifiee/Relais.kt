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
    /** « supprime » vaut 1 ou true selon les lignes. */
    private fun retire(l: JSONObject) = l.optBoolean("supprime", false) || l.optInt("supprime", 0) == 1

    fun synchroniser(archive: Archive): Int {
        connecter()
        runCatching { archive.purgerEchosElement() }
        var apres = archive.curseur("element.seq")?.toLongOrNull() ?: 0
        // Une seule fois : relire depuis le début des réactions reprises par le relais (le 17/11, à partir du n° ~746 000)
        if (archive.curseur("element.reactions") == null) {
            if (apres > 745_000) apres = 745_000
            archive.definirCurseur("element.reactions", "1")
        }
        var total = 0
        while (true) {
            val j = get("/messages?apres=$apres&limite=2000&reactions=1", 30_000)
            val lignes = j.optJSONArray("messages")?.objets() ?: break
            if (lignes.isEmpty()) break
            val convs = HashMap<String, Conversation>()
            val lot = ArrayList<Message>()
            val supprimes = ArrayList<String>()
            val reactions = ArrayList<Reaction>()
            val reactionsRetirees = ArrayList<String>()
            for (l in lignes) {
                // « ~… » = écho provisoire d'un message envoyé : le vrai message arrive ensuite
                val eid = l.optString("event_id").takeIf { it.isNotEmpty() && !it.startsWith("~") } ?: continue
                val salon = l.optString("salon").takeIf { it.isNotEmpty() } ?: continue
                val cle = "Element:$eid"
                // Réaction reçue (contact, ou vous depuis Element) : { cible, cle = emoji, auteur, de_moi, supprime }
                if (l.optString("type") == "reaction") {
                    if (retire(l)) { reactionsRetirees.add(cle); continue }
                    val cible = l.optString("cible").takeIf { it.isNotEmpty() } ?: continue
                    val emoji = l.optString("cle").takeIf { it.isNotEmpty() } ?: continue
                    val moi = l.optInt("de_moi") == 1
                    reactions.add(Reaction(cle, "Element:$cible", cleConv(Source.ELEMENT, salon), emoji,
                        if (moi) "Vous" else l.optString("auteur").ifEmpty { "Quelqu'un" }, moi))
                    continue
                }
                if (retire(l)) { supprimes.add(cle); continue }
                val conv = Conversation(cleConv(Source.ELEMENT, salon), Source.ELEMENT, salon,
                    l.optString("nom_salon").takeIf { it.isNotEmpty() && it != "null" } ?: "Element",
                    l.optInt("est_groupe") == 1, 0, emptyList())
                convs[conv.cle] = conv
                val type = l.optString("type").ifEmpty { "texte" }
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
                // Réponse : message cité, si le relais le fournit (champ reponse_a)
                val cite = l.optString("reponse_a").takeIf { it.startsWith("$") }?.let { "Element:$it" }
                val geo = l.optString("geo").takeIf { it.startsWith("geo:") }
                lot.add(Message(cle, conv.cle, l.ts("ts") ?: 0, deMoi,
                    if (deMoi) "Vous" else l.optString("auteur").ifEmpty { "?" },
                    if (cite != null) sansCitation(l.optString("texte")) else l.optString("texte"), type, media, duree,
                    Extra.de("cite" to cite, "geo" to geo)))
            }
            archive.enregistrerConversations(convs.values.toList())
            archive.enregistrerMessages(lot)
            archive.supprimerMessages(supprimes)
            archive.enregistrerReactions(reactions)
            reactionsRetirees.forEach { archive.oublierReactionCle(it) }
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
    fun envoyer(salon: String, texte: String, reponseA: String?): String {
        connecter()
        val d = JSONObject().put("salon", salon).put("texte", texte)
        if (reponseA != null) d.put("reponse_a", reponseA)
        val r = Http.requete("$base/envoyer", "POST", jeton, d.toString(), 20_000)
        if (r.code != 200) throw ErreurHttp(r.code, "envoi")
        return runCatching { JSONObject(r.texte).optString("event_id") }.getOrDefault("")
    }

    /**
     * Envoie une photo, une vidéo, un vocal ou un fichier dans Element — UNIQUEMENT sur « Envoyer » de l'aperçu.
     * type : image, video, audio, fichier. Renvoie l'identifiant du message.
     */
    fun envoyerMedia(salon: String, octets: ByteArray, nom: String, mimetype: String, type: String,
                     dureeMs: Long? = null, largeur: Int? = null, hauteur: Int? = null): String {
        connecter()
        val d = JSONObject().put("salon", salon).put("nom", nom).put("mimetype", mimetype).put("type", type)
            .put("donnees", android.util.Base64.encodeToString(octets, android.util.Base64.NO_WRAP))
        if (dureeMs != null) d.put("duree", dureeMs)
        if (largeur != null && largeur > 0) d.put("largeur", largeur)
        if (hauteur != null && hauteur > 0) d.put("hauteur", hauteur)
        val r = Http.requete("$base/envoyer-media", "POST", jeton, d.toString(), 120_000)
        if (r.code == 413) throw Exception("fichier trop lourd pour le relais (50 Mo au plus)")
        if (r.code == 404) throw Exception("le relais Element ne sait pas encore envoyer de fichiers")
        if (r.code != 200) throw ErreurHttp(r.code, "envoi du fichier")
        return runCatching { JSONObject(r.texte).optString("event_id") }.getOrDefault("")
    }

    /** Modifie un de VOS messages texte Element (m.replace) — UNIQUEMENT sur « Enregistrer la modification ». */
    fun modifier(salon: String, eventId: String, texte: String) {
        connecter()
        val d = JSONObject().put("salon", salon).put("event_id", eventId).put("texte", texte)
        val r = Http.requete("$base/modifier", "POST", jeton, d.toString(), 20_000)
        when (r.code) {
            200 -> {}
            403 -> throw Exception("seuls vos propres messages peuvent être modifiés")
            404 -> throw Exception("message introuvable (ou relais pas encore à jour)")
            else -> throw ErreurHttp(r.code, "modification")
        }
    }

    /**
     * Marque lu dans Element, en PRIVÉ seulement (m.read.private / m.fully_read) :
     * vos contacts ne voient pas que vous avez lu. Appelé quand vous lisez dans Messagerie.
     */
    // MARK: Mon profil (nom affiché sur chaque réseau)

    data class Profil(val reseau: String, val nom: String, val identifiant: String, val connecte: Boolean, val modifiable: Boolean, val raison: String)

    fun profils(): Pair<List<Profil>, Int> {
        connecter()
        val j = get("/profil", 15_000)
        val o = j.optJSONObject("profils") ?: JSONObject()
        val l = o.keys().asSequence().map { k ->
            val p = o.optJSONObject(k) ?: JSONObject()
            Profil(k, p.optString("nom"), p.optString("identifiant"), p.optBoolean("connecte"), p.optBoolean("modifiable"), p.optString("raison"))
        }.toList()
        return l to j.optInt("nom_max", 64)
    }

    /** Change VOTRE nom affiché sur ce réseau — uniquement sur appui explicite. */
    fun changerNomProfil(reseau: String, nom: String) {
        connecter()
        val r = Http.requete("$base/profil", "POST", jeton, JSONObject().put("reseau", reseau).put("nom", nom).toString(), 30_000)
        if (r.code != 200) {
            val j = runCatching { JSONObject(r.texte) }.getOrNull()
            throw Exception(j?.optString("erreur")?.ifBlank { null } ?: j?.optString("detail")?.ifBlank { null } ?: when (r.code) {
                409 -> "réseau non connecté"; 501 -> "non pris en charge"; 502 -> "refusé par la passerelle"; else -> "erreur ${r.code}"
            })
        }
    }

    // MARK: Reconnexion WhatsApp par code d'association (le relais parle à la passerelle du serveur)

    data class Association(val id: String, val code: String)

    /** Demande un code d'association pour ce numéro (à saisir dans WhatsApp > Appareils connectés > Lier avec le numéro). */
    fun demarrerConnexionWhatsApp(telephone: String): Association {
        connecter()
        val r = Http.requete("$base/whatsapp/connexion/demarrer", "POST", jeton, JSONObject().put("telephone", telephone).toString(), 100_000)   // jusqu'à ~60 s côté WhatsApp
        val j = runCatching { JSONObject(r.texte) }.getOrNull()
        if (r.code != 200 || j == null) throw Exception(listOfNotNull(j?.optString("erreur")?.ifBlank { null }, j?.optString("detail")?.ifBlank { null }).joinToString(" : ").ifBlank { null }
            ?: if (r.code == 404) "le relais du serveur ne gère pas encore la reconnexion" else "erreur ${r.code}")
        val code = j.optString("code")
        if (code.isBlank()) throw Exception(j.optString("detail").ifBlank { "aucun code reçu" })
        return Association(j.optString("id"), code)
    }

    /** « attente », « connecte » ou « erreur », avec un détail. Attend au plus ~25 s côté serveur. */
    fun etatConnexionWhatsApp(id: String): Pair<String, String> {
        connecter()
        val r = Http.requete("$base/whatsapp/connexion/etat?attendre=20&id=" + Http.enc(id), jeton = jeton, delaiMs = 40_000)
        if (r.code == 404) return "erreur" to "Demande expirée sur le serveur. Demandez un nouveau code."
        val j = runCatching { JSONObject(r.texte) }.getOrNull() ?: return "erreur" to "réponse illisible (${r.code})"
        return j.optString("etat", "erreur") to j.optString("detail")
    }

    fun annulerConnexionWhatsApp(id: String) {
        connecter()
        runCatching { Http.requete("$base/whatsapp/connexion/annuler", "POST", jeton, JSONObject().put("id", id).toString(), 10_000) }
    }

    /** Lu Element PUBLIC (m.read + m.fully_read) : choix d'Alain du 24/11, le contact voit que c'est lu. */
    fun luPublic(salon: String, eventId: String) {
        connecter()
        val d = JSONObject().put("salon", salon).put("event_id", eventId)
        val r = Http.requete("$base/lu", "POST", jeton, d.toString(), 10_000)
        if (r.code != 200) throw ErreurHttp(r.code, "lu")
    }

    /** Accusés de lecture des contacts dans un salon Element : (identifiant, date de lecture). */
    fun accuses(salon: String): List<Pair<String, Long>> {
        connecter()
        val a = get("/accuses?salon=" + Http.enc(salon), 10_000).optJSONArray("lecteurs") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { it.optString("id") to it.optLong("ts") }?.takeIf { it.second > 0 } }
    }

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
