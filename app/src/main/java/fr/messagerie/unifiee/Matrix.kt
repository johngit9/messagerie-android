package fr.messagerie.unifiee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client Matrix minimal (API Client-Server de Synapse), en appels HTTP directs.
 * Volontairement : aucun accusé de lecture, aucune présence, aucun indicateur de frappe.
 * Les seules écritures (envoyer, réagir) ne sont appelées que sur un clic de l'utilisateur.
 */
class Matrix(private val cfg: Config) {
    companion object {
        @Volatile var derniereBonne: String? = null
        @Volatile private var verifieLe = 0L
        private val filtreVeille = """{"room":{"timeline":{"limit":1,"types":["m.room.message","m.room.redaction","m.reaction"]},"state":{"types":[]},"ephemeral":{"types":["m.receipt"]},"account_data":{"not_types":["*"]}},"presence":{"not_types":["*"]},"account_data":{"not_types":["*"]}}"""
    }

    var base = ""
        private set
    val utilisateur get() = cfg.matrixUtilisateur
    /** Statut de présence choisi (paramètre set_presence des /sync). */
    val presence get() = cfg.presence
    private val jeton get() = cfg.matrixJeton

    /** Essaie la dernière adresse qui a marché, puis les autres dans l'ordre saisi. */
    fun connecter() {
        if (!cfg.matrixConfigure) throw Injoignable("serveur Matrix non configuré")
        // Adresse vérifiée il y a moins de 2 min : pas de nouvelle sonde (plus rapide).
        derniereBonne?.let { if (System.currentTimeMillis() - verifieLe < 120_000) { base = it; return } }
        val toutes = (listOfNotNull(derniereBonne) + Config.liste(cfg.matrixUrls)).distinct()
        for (u in toutes) {
            try {
                if (Http.requete("$u/_matrix/client/v3/account/whoami", jeton = jeton, delaiMs = 5_000).code == 200) {
                    base = u; derniereBonne = u; verifieLe = System.currentTimeMillis(); return
                }
            } catch (_: Exception) {}
        }
        derniereBonne = null
        throw Injoignable("serveur Matrix injoignable : le serveur est-il allumé et joignable depuis ce téléphone ?")
    }

    fun get(chemin: String, delaiMs: Int = 8_000): JSONObject {
        val r = try { Http.requete(base + chemin, jeton = jeton, delaiMs = delaiMs) }
            catch (e: java.io.IOException) { verifieLe = 0; throw e }
        if (r.code != 200) throw ErreurHttp(r.code, chemin.substringBefore('?'))
        return JSONObject(r.texte)
    }

    fun telecharger(chemin: String): ByteArray {
        val r = Http.requete(base + chemin, jeton = jeton, delaiMs = 30_000)
        if (r.code != 200) throw ErreurHttp(r.code, "média")
        return r.corps
    }

    /** Envoie un message — UNIQUEMENT sur le bouton « Envoyer ». */
    /** Envoie un texte — UNIQUEMENT sur le bouton « Envoyer ». Renvoie l'identifiant de l'événement. */
    fun envoyer(salon: String, texte: String, reponseA: String?, mentions: Map<String, String> = emptyMap()): String {
        connecter()
        val txn = "messagerie-" + UUID.randomUUID()
        val contenu = JSONObject().put("msgtype", "m.text").put("body", texte)
        // Mentions « @Nom » : lien vers le membre (la passerelle en fait une vraie mention @ dans WhatsApp, Telegram…)
        val presentes = mentions.filterKeys { texte.contains("@$it") }
        if (presentes.isNotEmpty()) {
            var html = android.text.TextUtils.htmlEncode(texte)
            for ((nom, id) in presentes.entries.sortedByDescending { it.key.length }) {
                html = html.replace("@" + android.text.TextUtils.htmlEncode(nom),
                    "<a href=\"https://matrix.to/#/" + id + "\">" + android.text.TextUtils.htmlEncode(nom) + "</a>")
            }
            contenu.put("format", "org.matrix.custom.html").put("formatted_body", html.replace("\n", "<br>"))
            contenu.put("m.mentions", JSONObject().put("user_ids", org.json.JSONArray(presentes.values.distinct())))
        }
        if (reponseA != null) contenu.put("m.relates_to", JSONObject().put("m.in_reply_to", JSONObject().put("event_id", reponseA)))
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/send/m.room.message/${Http.enc(txn)}",
            "PUT", jeton, contenu.toString(), 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "envoi")
        return runCatching { JSONObject(r.texte).optString("event_id") }.getOrDefault("")
    }

    /** Envoie un message vocal — UNIQUEMENT sur le bouton « Envoyer » de l'aperçu. Renvoie (événement, mxc). */
    fun envoyerVocal(salon: String, octets: ByteArray, typeMime: String, nom: String, dureeMs: Long): Pair<String, String> {
        val mxc = televerser(octets, typeMime, nom)
        val contenu = JSONObject().put("msgtype", "m.audio").put("body", nom).put("url", mxc)
            .put("info", JSONObject().put("mimetype", typeMime).put("size", octets.size).put("duration", dureeMs))
            .put("org.matrix.msc1767.audio", JSONObject().put("duration", dureeMs))
            .put("org.matrix.msc3245.voice", JSONObject())
        val txn = "messagerie-" + UUID.randomUUID()
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/send/m.room.message/${Http.enc(txn)}",
            "PUT", jeton, contenu.toString(), 20_000)
        if (r.code != 200) throw ErreurHttp(r.code, "envoi du vocal")
        return runCatching { JSONObject(r.texte).optString("event_id") }.getOrDefault("") to mxc
    }

    /** Pose une réaction — UNIQUEMENT sur un appui de l'utilisateur. Renvoie l'identifiant de la réaction. */
    fun reagir(salon: String, cible: String, emoji: String): String {
        connecter()
        val txn = "messagerie-" + UUID.randomUUID()
        val contenu = JSONObject().put("m.relates_to",
            JSONObject().put("rel_type", "m.annotation").put("event_id", cible).put("key", emoji))
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/send/m.reaction/${Http.enc(txn)}",
            "PUT", jeton, contenu.toString(), 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "réaction")
        return JSONObject(r.texte).optString("event_id")
    }

    /** Retire une réaction envoyée depuis l'appli — UNIQUEMENT sur un appui de l'utilisateur. */
    fun retirerReaction(salon: String, idReaction: String) {
        connecter()
        val txn = "messagerie-" + UUID.randomUUID()
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/redact/${Http.enc(idReaction)}/${Http.enc(txn)}",
            "PUT", jeton, "{}", 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "réaction")
    }

    /** Téléverse un fichier sur le serveur Matrix (avant un envoi demandé par l'utilisateur). Renvoie l'adresse mxc. */
    fun televerser(octets: ByteArray, typeMime: String, nom: String): String {
        connecter()
        val u = Http.envoyerOctets("$base/_matrix/media/v3/upload?filename=${Http.enc(nom)}", jeton, octets, typeMime)
        if (u.code != 200) throw ErreurHttp(u.code, "téléversement")
        return JSONObject(u.texte).optString("content_uri").ifEmpty { throw Exception("téléversement sans adresse") }
    }

    /** Définit votre statut de présence — UNIQUEMENT quand vous le choisissez dans les Réglages. */
    fun definirPresence(etat: String) {
        connecter()
        val r = Http.requete("$base/_matrix/client/v3/presence/${Http.enc(utilisateur)}/status", "PUT", jeton,
            JSONObject().put("presence", etat).toString(), 10_000)
        if (r.code != 200) throw ErreurHttp(r.code, "présence")
    }

    /** Supprime un message pour tout le monde (rédaction Matrix, reprise par la passerelle) — UNIQUEMENT après confirmation. */
    fun supprimerPourTous(salon: String, eventId: String) {
        connecter()
        val txn = "messagerie-" + UUID.randomUUID()
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/redact/${Http.enc(eventId)}/${Http.enc(txn)}",
            "PUT", jeton, JSONObject().put("reason", "Supprimé par l'expéditeur").toString(), 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "suppression")
    }

    /** Transfère un message (texte ou pièce jointe déjà sur le serveur) — UNIQUEMENT après confirmation de l'utilisateur. */
    fun envoyerContenu(salon: String, contenu: JSONObject): String {
        connecter()
        val txn = "messagerie-" + UUID.randomUUID()
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/send/m.room.message/${Http.enc(txn)}",
            "PUT", jeton, contenu.toString(), 20_000)
        if (r.code != 200) throw ErreurHttp(r.code, "transfert")
        return runCatching { JSONObject(r.texte).optString("event_id") }.getOrDefault("")
    }

    /** Membres d'un salon (lecture seule) : identifiant → nom affiché. */
    fun membres(salon: String): Map<String, String> {
        connecter()
        val o = get("/_matrix/client/v3/rooms/${Http.enc(salon)}/joined_members").optJSONObject("joined") ?: return emptyMap()
        return o.keys().asSequence().associateWith { u ->
            o.optJSONObject(u)?.optString("display_name")?.takeIf { it.isNotEmpty() && it != "null" } ?: u
        }
    }

    /** Quitte le salon — UNIQUEMENT après la confirmation « Quitter le groupe ». */
    fun quitter(salon: String) {
        connecter()
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/leave", "POST", jeton, "{}", 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "quitter le groupe")
    }

    /** Télécharge un média mxc://serveur/id (lecture seule). */
    fun media(mxc: String): ByteArray {
        connecter()
        val chemin = mxc.removePrefix("mxc://")
        return try { telecharger("/_matrix/client/v1/media/download/$chemin") }
        catch (_: Exception) { telecharger("/_matrix/media/v3/download/$chemin") }
    }

    /** Miniature carrée d'une image mxc:// (photos de contact). */
    fun miniature(mxc: String, taille: Int = 128): ByteArray {
        connecter()
        val chemin = mxc.removePrefix("mxc://") + "?width=$taille&height=$taille&method=crop"
        return try { telecharger("/_matrix/client/v1/media/thumbnail/$chemin") }
        catch (_: Exception) { try { telecharger("/_matrix/media/v3/thumbnail/$chemin") } catch (_: Exception) { media(mxc) } }
    }

    /** Attente longue (30 s max) ; lecture seule. Renvoie le nouveau jeton et s'il y a du nouveau. */
    /**
     * « En train d'écrire » dans un salon : attente longue (25 s max), RÉCEPTION seulement.
     * set_presence=${cfg.presence} : votre présence n'est jamais signalée ; aucun indicateur n'est envoyé.
     * Renvoie (jeton suivant, identifiants qui écrivent, ou null si rien n'a changé).
     */
    fun attendreSaisie(salon: String, depuis: String?): Pair<String, List<String>?> {
        connecter()
        val filtre = JSONObject()
            .put("room", JSONObject().put("rooms", org.json.JSONArray().put(salon))
                .put("timeline", JSONObject().put("limit", 0)).put("state", JSONObject().put("types", org.json.JSONArray()))
                .put("ephemeral", JSONObject().put("types", org.json.JSONArray().put("m.typing")))
                .put("account_data", JSONObject().put("not_types", org.json.JSONArray().put("*"))))
            .put("presence", JSONObject().put("not_types", org.json.JSONArray().put("*")))
            .put("account_data", JSONObject().put("not_types", org.json.JSONArray().put("*")))
        var chemin = "/_matrix/client/v3/sync?set_presence=${cfg.presence}&filter=${Http.enc(filtre.toString())}"
        chemin += if (depuis != null) "&timeout=25000&since=${Http.enc(depuis)}" else "&timeout=0"
        val j = get(chemin, 40_000)
        val ev = j.optJSONObject("rooms")?.optJSONObject("join")?.optJSONObject(salon)
            ?.optJSONObject("ephemeral")?.optJSONArray("events")?.objets()
            ?.lastOrNull { it.optString("type") == "m.typing" }
        val ids = ev?.optJSONObject("content")?.optJSONArray("user_ids")?.let { a -> (0 until a.length()).map { a.optString(it) } }
        return j.getString("next_batch") to ids
    }

    fun attendreNouveautes(depuis: String?): Pair<String, Boolean> {
        connecter()
        var chemin = "/_matrix/client/v3/sync?set_presence=${cfg.presence}&filter=${Http.enc(filtreVeille)}"
        chemin += if (depuis != null) "&timeout=30000&since=${Http.enc(depuis)}" else "&timeout=0"
        val j = get(chemin, 45_000)
        val suivant = j.getString("next_batch")
        val salons = j.optJSONObject("rooms")?.optJSONObject("join")
        var nouveau = false
        if (depuis != null && salons != null) {
            for (k in salons.keys()) {
                val r = salons.getJSONObject(k)
                if ((r.optJSONObject("timeline")?.optJSONArray("events")?.length() ?: 0) > 0 ||
                    (r.optJSONObject("ephemeral")?.optJSONArray("events")?.length() ?: 0) > 0) { nouveau = true; break }
            }
        }
        return suivant to nouveau
    }
}

fun JSONArray.objets(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.ts(cle: String): Long? = if (has(cle)) optDouble(cle).takeIf { !it.isNaN() }?.toLong() else null

/** Une passerelle mautrix : ses utilisateurs « fantômes » ont un préfixe propre. */
data class Pont(val prefixe: String, val source: Source, val curseur: String) {
    companion object {
        val SMS = Pont("@gmessages_", Source.SMS, "sms.sync")
        val TELEGRAM = Pont("@telegram_", Source.TELEGRAM, "telegram.sync")
        val LINKEDIN = Pont("@linkedin_", Source.LINKEDIN, "linkedin.sync")
        val WHATSAPP = Pont("@whatsapp_", Source.WHATSAPP, "whatsapp.sync")
        val SIGNAL = Pont("@signal_", Source.SIGNAL, "signal.sync")
        fun pour(s: Source) = when (s) { Source.TELEGRAM -> TELEGRAM; Source.LINKEDIN -> LINKEDIN; Source.WHATSAPP -> WHATSAPP; Source.SIGNAL -> SIGNAL; else -> SMS }
    }
}

class InfoSalon(val membres: Map<String, String>, val nom: String?, val avatars: Map<String, String> = emptyMap(), val avatarSalon: String? = null)

/**
 * Synchronisation d'une passerelle vers l'archive. Lecture seule (GET).
 * Premier passage : tout l'historique ; ensuite une requête /sync qui ne renvoie que les nouveautés.
 */
class SyncPont(
    private val m: Matrix,
    private val pont: Pont,
    private val archive: Archive,
    /** WhatsApp : salon Matrix → identifiant WhatsApp (numéro ou groupe). */
    private val correspondance: Map<String, String>? = null,
) {
    companion object {
        private class Cache(val infos: Map<String, InfoSalon>, val moi: String?, val date: Long)
        private val cache = ConcurrentHashMap<String, Cache>()
        private val autres = ConcurrentHashMap<String, Set<String>>()
        fun viderCache() { cache.clear() }
        /** Votre fantôme repéré sur cette passerelle (vos réactions posées depuis l'appli officielle). */
        fun moiDetecte(p: Pont): String? = cache[p.curseur]?.moi
        /** Messages déjà archivés et encore « Waiting for attachment » : clé → dernière tentative. */
        private val essais = ConcurrentHashMap<String, Long>()

        private const val FILTRE_SYNC = """{"room":{"timeline":{"limit":100,"types":["m.room.message","m.room.redaction","m.reaction"]},"state":{"types":["m.room.name"]},"ephemeral":{"types":["m.receipt"]},"account_data":{"not_types":["*"]}},"presence":{"not_types":["*"]},"account_data":{"not_types":["*"]}}"""
        private const val FILTRE_DEPART = """{"room":{"timeline":{"limit":20,"types":["m.room.message"]},"state":{"types":["m.bridge"]},"ephemeral":{"types":["m.receipt"]},"account_data":{"not_types":["*"]}},"presence":{"not_types":["*"]},"account_data":{"not_types":["*"]}}"""
        /** Pages (de 100 messages) remontées par salon au premier passage : la liste s'affiche vite. */
        private const val PAGES_RAPIDES = 3
        private const val FILTRE_MESSAGES = """{"types":["m.room.message","m.room.redaction","m.reaction"]}"""
    }

    /** Lectures faites sur vos autres appareils : conversation → date. Simplement lues, jamais envoyées. */
    val lectures = HashMap<String, Long>()
    /** Jusqu'où vos contacts ont lu (accusés de lecture reçus, jamais envoyés) : conversation → date. */
    val lusParContacts = HashMap<String, Long>()
    /** Dernière mention de vous (date du message) par conversation. */
    val mentionsMoi = HashMap<String, Long>()
    /** Conversations SMS passant par RCS. */
    var rcs: Set<String>? = null

    private fun jid(salon: String) = correspondance?.get(salon) ?: salon
    private fun cle(salon: String) = cleConv(pont.source, jid(salon))

    suspend fun synchroniser(): Int {
        m.connecter()
        val jeton = archive.curseur(pont.curseur)
        val c = cache[pont.curseur]
        if (jeton != null && c != null && System.currentTimeMillis() - c.date < 900_000) {
            return incremental(jeton, c.infos, c.moi)
        }
        return complet()
    }

    private suspend fun incremental(jeton: String, infos: Map<String, InfoSalon>, moi: String?): Int {
        val rep = m.get("/_matrix/client/v3/sync?set_presence=${m.presence}&timeout=0&since=${Http.enc(jeton)}&filter=${Http.enc(FILTRE_SYNC)}")
        var total = 0
        val salons = rep.optJSONObject("rooms")?.optJSONObject("join")
        if (salons != null) for (id in salons.keys()) {
            val r = salons.getJSONObject(id)
            val info = infos[id]
            if (info == null) {   // nouvelle conversation : passage complet au prochain tour
                if (autres[pont.curseur]?.contains(id) != true) cache.remove(pont.curseur)
                continue
            }
            noterLectures(r, cle(id), moi)
            val tl = r.optJSONObject("timeline")
            total += enregistrer(tl?.optJSONArray("events")?.objets() ?: emptyList(), cle(id), info, moi).second
            if (tl?.optBoolean("limited") == true) {
                tl.optString("prev_batch").takeIf { it.isNotEmpty() }?.let { total += remonter(id, cle(id), it, info, moi) }
            }
        }
        rep.optString("next_batch").takeIf { it.isNotEmpty() }?.let { archive.definirCurseur(pont.curseur, it) }
        return total
    }

    private suspend fun complet(): Int = coroutineScope {
        val ids = m.get("/_matrix/client/v3/joined_rooms").optJSONArray("joined_rooms")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

        // Membres et noms des salons relayés par cette passerelle (12 à la fois).
        val infos = HashMap<String, InfoSalon>()
        for (lot in ids.chunked(12)) {
            lot.map { id ->
                async(Dispatchers.IO) {
                    val membres = HashMap<String, String>()
                    val avatars = HashMap<String, String>()
                    val j = runCatching { m.get("/_matrix/client/v3/rooms/${Http.enc(id)}/joined_members") }.getOrNull()
                    j?.optJSONObject("joined")?.let { o -> for (u in o.keys()) {
                        membres[u] = o.optJSONObject(u)?.optString("display_name")?.takeIf { it.isNotEmpty() && it != "null" } ?: u
                        o.optJSONObject(u)?.optString("avatar_url")?.takeIf { it.startsWith("mxc://") }?.let { avatars[u] = it }
                    } }
                    if (membres.keys.none { it.startsWith(pont.prefixe) }) return@async null
                    if (correspondance != null && correspondance[id] == null) return@async null
                    val nom = runCatching { m.get("/_matrix/client/v3/rooms/${Http.enc(id)}/state/m.room.name/").optString("name") }.getOrNull()?.takeIf { it.isNotEmpty() }
                    val avatarSalon = runCatching { m.get("/_matrix/client/v3/rooms/${Http.enc(id)}/state/m.room.avatar/").optString("url") }.getOrNull()?.takeIf { it.startsWith("mxc://") }
                    id to InfoSalon(membres, nom, avatars, avatarSalon)
                }
            }.awaitAll().filterNotNull().forEach { (id, i) -> infos[id] = i }
        }

        // Jeton de départ (pris AVANT la remontée), derniers messages et accusés de lecture.
        val depart = runCatching { m.get("/_matrix/client/v3/sync?set_presence=${m.presence}&timeout=0&filter=${Http.enc(FILTRE_DEPART)}", 30_000) }.getOrNull()
        val salonsDepart = depart?.optJSONObject("rooms")?.optJSONObject("join")

        // Le fantôme qui vous représente : présent dans bien plus de conversations que les autres.
        fun elire(freq: Map<String, Int>): String? {
            val cl = freq.entries.sortedByDescending { it.value }
            val p = cl.firstOrNull() ?: return null
            return if (p.value >= 3 && p.value > (cl.getOrNull(1)?.value ?: 0) * 2) p.key else null
        }
        val freq = HashMap<String, Int>()
        infos.values.forEach { i -> i.membres.keys.filter { it.startsWith(pont.prefixe) }.forEach { freq[it] = (freq[it] ?: 0) + 1 } }
        val freqExp = HashMap<String, Int>()
        for ((id, i) in infos) {
            val evs = salonsDepart?.optJSONObject(id)?.optJSONObject("timeline")?.optJSONArray("events")?.objets() ?: continue
            evs.mapNotNull { it.optString("sender").takeIf { s -> s.isNotEmpty() } }.toSet()
                .filter { it.startsWith(pont.prefixe) && i.membres[it] == null }.forEach { freqExp[it] = (freqExp[it] ?: 0) + 1 }
        }
        val moi = cfg_moi() ?: elire(freq) ?: elire(freqExp)

        val convs = infos.map { (id, i) ->
            val contacts = i.membres.filter { it.key.startsWith(pont.prefixe) && it.key != moi }
            val nom = (i.nom ?: contacts.values.sorted().joinToString(", ")).replace(" (WA)", "")
            val j = jid(id)
            Conversation(cleConv(pont.source, j), pont.source, j, nom.ifEmpty { pont.source.id },
                if (correspondance != null) j.endsWith("@g.us") else contacts.size > 1, 0, emptyList())
        }
        archive.enregistrerConversations(convs, garderNoms = correspondance != null)

        // Photos de contact : celle du salon (groupes), sinon celle du contact (discussions).
        val photos = HashMap<String, String>()
        for ((id, i) in infos) {
            val contacts = i.avatars.filterKeys { it.startsWith(pont.prefixe) && it != moi }
            (i.avatarSalon ?: contacts.values.singleOrNull() ?: contacts.values.firstOrNull().takeIf { contacts.size == 1 })?.let { photos[cle(id)] = it }
        }
        archive.definirAvatars(photos)

        val enRcs = HashSet<String>()
        if (salonsDepart != null) for (id in salonsDepart.keys()) {
            if (infos[id] == null) continue
            val r = salonsDepart.getJSONObject(id)
            val etats = r.optJSONObject("state")?.optJSONArray("events")?.objets() ?: emptyList()
            if (etats.any { it.optJSONObject("content")?.optJSONObject("protocol")?.optString("id") == "gmessages-rcs" }) enRcs.add(cle(id))
            noterLectures(r, cle(id), moi)
        }

        // Remontée de l'historique, 6 conversations à la fois.
        var total = 0
        for (lot in infos.entries.chunked(6)) {
            total += lot.map { (id, i) -> async(Dispatchers.IO) { remonter(id, cle(id), null, i, moi, PAGES_RAPIDES) } }.awaitAll().sum()
        }

        depart?.optString("next_batch")?.takeIf { it.isNotEmpty() }?.let { archive.definirCurseur(pont.curseur, it) }
        if (pont.source == Source.SMS) rcs = enRcs
        cache[pont.curseur] = Cache(infos, moi, System.currentTimeMillis())
        autres[pont.curseur] = ids.toSet() - infos.keys
        total
    }

    /** Complète l'historique ancien des salons, quelques pages à chaque passage. */
    fun completerHistorique(budget: Int = 30) {
        val c = cache[pont.curseur] ?: return
        var reste = budget
        for ((id, i) in c.infos) {
            if (reste <= 0) return
            val jeton = archive.curseur("remontee.${pont.curseur}.$id") ?: continue
            remonter(id, cle(id), jeton, i, c.moi, minOf(10, reste), suite = true)
            reste -= 10
        }
    }

    private var moiConfig: String? = null
    fun avecMoi(moi: String?) = apply { moiConfig = moi?.takeIf { it.isNotEmpty() } }
    private fun cfg_moi() = moiConfig

    /** Accusés de lecture posés par VOUS depuis un autre appareil. */
    private fun noterLectures(salon: JSONObject, conv: String, moi: String?) {
        for (e in salon.optJSONObject("ephemeral")?.optJSONArray("events")?.objets() ?: emptyList()) {
            if (e.optString("type") != "m.receipt") continue
            val contenu = e.optJSONObject("content") ?: continue
            for (ev in contenu.keys()) {
                val types = contenu.optJSONObject(ev) ?: continue
                for (t in listOf("m.read", "m.read.private")) {
                    val users = types.optJSONObject(t) ?: continue
                    for (u in users.keys()) {
                        val ts = users.optJSONObject(u)?.ts("ts") ?: continue
                        if (u != m.utilisateur && u != moi) {
                            // Accusé d'un contact (« lu » sur vos messages) ; le robot de la passerelle est ignoré
                            if (t == "m.read" && !u.contains("bot:") && ts > (lusParContacts[conv] ?: 0)) lusParContacts[conv] = ts
                            continue
                        }
                        if (ts > (lectures[conv] ?: 0)) lectures[conv] = ts
                    }
                }
            }
        }
    }

    /** Remonte l'historique d'un salon jusqu'à retomber sur des messages déjà archivés (ou le début). */
    /**
     * Remonte l'historique d'un salon (au plus `pages` pages de 100). Si la limite est atteinte, le point
     * d'arrêt est gardé et la suite est remontée petit à petit, en arrière-plan (voir completerHistorique).
     */
    private fun remonter(salon: String, conv: String, debut: String?, info: InfoSalon, moi: String?, pages: Int = 5000, suite: Boolean = false): Int {
        var depuis = debut
        var total = 0
        val reprise = "remontee.${pont.curseur}.$salon"
        var page = 0
        while (true) {
            if (page++ >= pages) { depuis?.let { archive.definirCurseur(reprise, it) }; return total }
            var chemin = "/_matrix/client/v3/rooms/${Http.enc(salon)}/messages?dir=b&limit=100&filter=${Http.enc(FILTRE_MESSAGES)}"
            if (depuis != null) chemin += "&from=${Http.enc(depuis!!)}"
            val rep = runCatching { m.get(chemin, 20_000) }.getOrNull() ?: return total
            val evs = rep.optJSONArray("chunk")?.objets() ?: emptyList()
            val (lus, nouveaux) = enregistrer(evs, conv, info, moi)
            total += nouveaux
            if (evs.isEmpty()) { if (suite) archive.definirCurseur(reprise, null); return total }
            if (!suite && lus > 0 && nouveaux < lus) return total   // retombé sur des messages déjà archivés
            depuis = rep.optString("end").takeIf { it.isNotEmpty() } ?: run { if (suite) archive.definirCurseur(reprise, null); return total }
        }
    }

    /** Archive des événements ; renvoie (messages lus, messages nouveaux). */
    private fun enregistrer(evenements: List<JSONObject>, conv: String, info: InfoSalon, moi: String?): Pair<Int, Int> {
        val lot = ArrayList<Message>()
        val supprimes = ArrayList<String>()
        val reactions = ArrayList<Reaction>()
        val modifs = HashMap<String, JSONObject>()
        for (e in evenements) {
            val eid = e.optString("event_id").takeIf { it.isNotEmpty() } ?: continue
            if (e.optString("type") == "m.reaction") {
                val rel = e.optJSONObject("content")?.optJSONObject("m.relates_to") ?: continue
                val cible = rel.optString("event_id"); val cle = rel.optString("key")
                val exp = e.optString("sender")
                if (rel.optString("rel_type") != "m.annotation" || cible.isEmpty() || cle.isEmpty()) continue
                val deMoi = exp == m.utilisateur || exp == moi
                reactions.add(Reaction("${pont.source.id}:$eid", "${pont.source.id}:$cible", conv, cle,
                    if (deMoi) "Vous" else info.membres[exp]?.replace(" (WA)", "") ?: "Quelqu'un", deMoi))
                continue
            }
            if (e.optString("type") == "m.room.redaction") {
                val r = e.optString("redacts").ifEmpty { e.optJSONObject("content")?.optString("redacts") ?: "" }
                if (r.isNotEmpty()) supprimes.add("${pont.source.id}:$r")
                continue
            }
            val rel = e.optJSONObject("content")?.optJSONObject("m.relates_to")
            if (rel?.optString("rel_type") == "m.replace") {
                // Modification : la passerelle SMS remplace « Waiting for attachment … » par la photo une fois téléchargée.
                val cible = rel.optString("event_id").takeIf { it.isNotEmpty() } ?: continue
                val prec = modifs[cible]
                if (prec == null || (e.ts("origin_server_ts") ?: 0L) >= (prec.ts("origin_server_ts") ?: 0L)) modifs[cible] = e
                continue
            }
            messageMatrix(e, info.membres, moi, "${pont.source.id}:$eid", conv)?.let { lot.add(it) }
        }
        // Modifications : appliquées au message du même lot, sinon au message déjà archivé.
        val maj = ArrayList<Message>()
        for ((cible, modif) in modifs) {
            val cleCible = "${pont.source.id}:$cible"
            val i = lot.indexOfFirst { it.cle == cleCible }
            val origine = if (i >= 0) lot[i] else archive.messageParCle(cleCible) ?: continue
            val remplace = messageModifie(modif, origine, info.membres, moi) ?: continue
            if (i >= 0) lot[i] = remplace else maj.add(remplace)
        }
        archive.enregistrerMessages(maj)
        val deja = archive.existantes(lot.map { it.cle })
        val nouveaux = lot.filter { it.cle !in deja }
        archive.enregistrerMessages(nouveaux)
        archive.supprimerMessages(supprimes)
        archive.enregistrerReactions(reactions)
        return lot.size to nouveaux.size
    }

    /** Message d'origine avec le contenu de sa modification (même clé, même date). */
    private fun messageModifie(modif: JSONObject, origine: Message, membres: Map<String, String>, moi: String?): Message? {
        val contenu = modif.optJSONObject("content")?.optJSONObject("m.new_content") ?: return null
        val ev = JSONObject().put("event_id", origine.cle.substringAfter(':')).put("sender", modif.optString("sender"))
            .put("origin_server_ts", origine.date).put("content", contenu)
        return messageMatrix(ev, membres, moi, origine.cle, origine.conv)
            ?.copy(date = origine.date, deMoi = origine.deMoi, auteur = origine.auteur)
    }

    /**
     * Rattrape les pièces jointes SMS archivées avant leur téléchargement par la passerelle :
     * demande à Synapse la dernière modification du message (lecture seule, GET).
     */
    fun reparerPiecesJointes(limite: Int = 10) {
        if (pont.source != Source.SMS) return
        val c = cache[pont.curseur] ?: return
        val salons = c.infos.keys.associateBy { cle(it) }
        val maintenant = System.currentTimeMillis()
        val enAttente = archive.messagesEnAttenteDePieceJointe(pont.source.id, maintenant - 30L * 86_400_000L)
            .filter { maintenant - (essais[it.cle] ?: 0L) > 600_000 }.take(limite)
        for (msg in enAttente) {
            essais[msg.cle] = maintenant
            val salon = salons[msg.conv] ?: continue
            val eid = msg.cle.substringAfter(':')
            val rep = runCatching {
                m.get("/_matrix/client/v1/rooms/${Http.enc(salon)}/relations/${Http.enc(eid)}/m.replace?limit=1&dir=b", 15_000)
            }.getOrNull() ?: continue
            val modif = rep.optJSONArray("chunk")?.objets()?.firstOrNull() ?: continue
            val remplace = messageModifie(modif, msg, c.infos[salon]?.membres ?: emptyMap(), c.moi) ?: continue
            archive.enregistrerMessages(listOf(remplace))
            essais.remove(msg.cle)
        }
    }

    /** Convertit un événement Matrix en message (texte, vocal, photo…). */
    private fun messageMatrix(e: JSONObject, membres: Map<String, String>, moi: String?, cle: String, conv: String): Message? {
        val ts = e.ts("origin_server_ts") ?: return null
        // Heure d'arrivée sur le serveur (Synapse donne l'âge de l'événement) : sert à découper la latence.
        e.optJSONObject("unsigned")?.optLong("age", -1L)?.takeIf { it >= 0 }?.let { age ->
            val surServeur = System.currentTimeMillis() - age
            if (surServeur - ts in 0..3_600_000L) ArriveesServeur.noter(cle, surServeur)
        }
        val brut = e.optJSONObject("content") ?: return null
        val exp = e.optString("sender").takeIf { it.isNotEmpty() } ?: return null
        if (brut.optJSONObject("m.relates_to")?.optString("rel_type") == "m.replace") return null
        // Synapse joint la dernière modification du message : on affiche son contenu (ex. la photo
        // qui remplace « Waiting for attachment … » côté SMS).
        val c = e.optJSONObject("unsigned")?.optJSONObject("m.relations")?.optJSONObject("m.replace")
            ?.optJSONObject("content")?.optJSONObject("m.new_content") ?: brut
        val corps = c.optString("body")
        var type = "texte"; var texte = corps; var media: String? = null; var duree: Double? = null
        val info = c.optJSONObject("info")
        when (val mt = c.optString("msgtype")) {
            "m.text", "m.notice", "m.emote" -> if (corps.isEmpty()) return null
            "m.audio" -> {
                type = "audio"; texte = ""
                c.optString("url").takeIf { it.isNotEmpty() }?.let { media = it + "#" + (info?.optString("mimetype")?.ifEmpty { null } ?: "audio/mpeg") }
                duree = info?.ts("duration")?.let { it / 1000.0 }
            }
            "m.image", "m.video", "m.file" -> {
                type = when (mt) { "m.image" -> "image"; "m.video" -> "video"; else -> "fichier" }
                texte = when (type) { "image" -> "[photo]"; "video" -> "[vidéo]"; else -> "[fichier] $corps" }
                val nom = c.optString("filename").ifEmpty { corps }.replace("#", "")
                c.optString("url").takeIf { it.isNotEmpty() }?.let { media = it + "#" + (info?.optString("mimetype")?.ifEmpty { null } ?: "application/octet-stream") + "#" + nom }
                // Vidéo : vignette fournie par la passerelle (4e champ) et durée
                // Vignette fournie par la passerelle (4e champ) : vidéos et pièces jointes
                info?.optString("thumbnail_url")?.takeIf { it.startsWith("mxc://") }?.let { v -> media = media?.plus("#" + v) }
                if (type == "video") duree = info?.ts("duration")?.let { it / 1000.0 }
            }
            "m.location" -> { type = "autre"; texte = "[position]" }
            else -> if (corps.isEmpty()) return null
        }
        val deMoi = exp == m.utilisateur || exp == moi
        // Vous êtes mentionné (@) : m.mentions ou lien vers votre identifiant dans le texte mis en forme
        if (!deMoi) {
            val ids = listOfNotNull(m.utilisateur.takeIf { it.isNotEmpty() }, moi)
            val cites = c.optJSONObject("m.mentions")?.optJSONArray("user_ids")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val html = c.optString("formatted_body")
            if (ids.any { it in cites || html.contains(it) || html.contains(java.net.URLEncoder.encode(it, "UTF-8")) } &&
                ts > (mentionsMoi[conv] ?: 0)) mentionsMoi[conv] = ts
        }
        val auteur = if (deMoi) "Vous" else membres[exp]?.replace(" (WA)", "") ?: "Quelqu'un"
        return Message(cle, conv, ts, deMoi, auteur, texte, type, media, duree)
    }
}

/** Heure d'arrivée de chaque nouveau message sur le serveur (mémoire seulement, quelques centaines au plus). */
object ArriveesServeur {
    private val heures = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun noter(cle: String, heure: Long) { if (heures.size > 2000) heures.clear(); heures[cle] = heure }
    fun prendre(cle: String): Long? = heures.remove(cle)
}
