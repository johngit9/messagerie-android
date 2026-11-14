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
        private val filtreVeille = """{"room":{"timeline":{"limit":1,"types":["m.room.message","m.room.redaction"]},"state":{"types":[]},"ephemeral":{"types":["m.receipt"]},"account_data":{"not_types":["*"]}},"presence":{"not_types":["*"]},"account_data":{"not_types":["*"]}}"""
    }

    var base = ""
        private set
    val utilisateur get() = cfg.matrixUtilisateur
    private val jeton get() = cfg.matrixJeton

    /** Essaie la dernière adresse qui a marché, puis les autres dans l'ordre saisi. */
    fun connecter() {
        if (!cfg.matrixConfigure) throw Injoignable("serveur Matrix non configuré")
        val toutes = (listOfNotNull(derniereBonne) + Config.liste(cfg.matrixUrls)).distinct()
        for (u in toutes) {
            try {
                if (Http.requete("$u/_matrix/client/v3/account/whoami", jeton = jeton, delaiMs = 5_000).code == 200) {
                    base = u; derniereBonne = u; return
                }
            } catch (_: Exception) {}
        }
        derniereBonne = null
        throw Injoignable("serveur Matrix injoignable : le serveur est-il allumé et joignable depuis ce téléphone ?")
    }

    fun get(chemin: String, delaiMs: Int = 8_000): JSONObject {
        val r = Http.requete(base + chemin, jeton = jeton, delaiMs = delaiMs)
        if (r.code != 200) throw ErreurHttp(r.code, chemin.substringBefore('?'))
        return JSONObject(r.texte)
    }

    fun telecharger(chemin: String): ByteArray {
        val r = Http.requete(base + chemin, jeton = jeton, delaiMs = 30_000)
        if (r.code != 200) throw ErreurHttp(r.code, "média")
        return r.corps
    }

    /** Envoie un message — UNIQUEMENT sur le bouton « Envoyer ». */
    fun envoyer(salon: String, texte: String, reponseA: String?) {
        connecter()
        val txn = "messagerie-" + UUID.randomUUID()
        val contenu = JSONObject().put("msgtype", "m.text").put("body", texte)
        if (reponseA != null) contenu.put("m.relates_to", JSONObject().put("m.in_reply_to", JSONObject().put("event_id", reponseA)))
        val r = Http.requete("$base/_matrix/client/v3/rooms/${Http.enc(salon)}/send/m.room.message/${Http.enc(txn)}",
            "PUT", jeton, contenu.toString(), 15_000)
        if (r.code != 200) throw ErreurHttp(r.code, "envoi")
    }

    /** Attente longue (30 s max) ; lecture seule. Renvoie le nouveau jeton et s'il y a du nouveau. */
    fun attendreNouveautes(depuis: String?): Pair<String, Boolean> {
        connecter()
        var chemin = "/_matrix/client/v3/sync?filter=${Http.enc(filtreVeille)}"
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
        fun pour(s: Source) = when (s) { Source.TELEGRAM -> TELEGRAM; Source.LINKEDIN -> LINKEDIN; Source.WHATSAPP -> WHATSAPP; else -> SMS }
    }
}

class InfoSalon(val membres: Map<String, String>, val nom: String?)

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

        private const val FILTRE_SYNC = """{"room":{"timeline":{"limit":100,"types":["m.room.message","m.room.redaction"]},"state":{"types":["m.room.name"]},"ephemeral":{"types":["m.receipt"]},"account_data":{"not_types":["*"]}},"presence":{"not_types":["*"]},"account_data":{"not_types":["*"]}}"""
        private const val FILTRE_DEPART = """{"room":{"timeline":{"limit":20,"types":["m.room.message"]},"state":{"types":["m.bridge"]},"ephemeral":{"types":["m.receipt"]},"account_data":{"not_types":["*"]}},"presence":{"not_types":["*"]},"account_data":{"not_types":["*"]}}"""
        private const val FILTRE_MESSAGES = """{"types":["m.room.message","m.room.redaction"]}"""
    }

    /** Lectures faites sur vos autres appareils : conversation → date. Simplement lues, jamais envoyées. */
    val lectures = HashMap<String, Long>()
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
        val rep = m.get("/_matrix/client/v3/sync?timeout=0&since=${Http.enc(jeton)}&filter=${Http.enc(FILTRE_SYNC)}")
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
                    val j = runCatching { m.get("/_matrix/client/v3/rooms/${Http.enc(id)}/joined_members") }.getOrNull()
                    j?.optJSONObject("joined")?.let { o -> for (u in o.keys()) membres[u] = o.optJSONObject(u)?.optString("display_name")?.takeIf { it.isNotEmpty() && it != "null" } ?: u }
                    if (membres.keys.none { it.startsWith(pont.prefixe) }) return@async null
                    if (correspondance != null && correspondance[id] == null) return@async null
                    val nom = runCatching { m.get("/_matrix/client/v3/rooms/${Http.enc(id)}/state/m.room.name/").optString("name") }.getOrNull()?.takeIf { it.isNotEmpty() }
                    id to InfoSalon(membres, nom)
                }
            }.awaitAll().filterNotNull().forEach { (id, i) -> infos[id] = i }
        }

        // Jeton de départ (pris AVANT la remontée), derniers messages et accusés de lecture.
        val depart = runCatching { m.get("/_matrix/client/v3/sync?timeout=0&filter=${Http.enc(FILTRE_DEPART)}", 30_000) }.getOrNull()
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
            total += lot.map { (id, i) -> async(Dispatchers.IO) { remonter(id, cle(id), null, i, moi) } }.awaitAll().sum()
        }

        depart?.optString("next_batch")?.takeIf { it.isNotEmpty() }?.let { archive.definirCurseur(pont.curseur, it) }
        if (pont.source == Source.SMS) rcs = enRcs
        cache[pont.curseur] = Cache(infos, moi, System.currentTimeMillis())
        autres[pont.curseur] = ids.toSet() - infos.keys
        total
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
                        if (u != m.utilisateur && u != moi) continue
                        val ts = users.optJSONObject(u)?.ts("ts") ?: continue
                        if (ts > (lectures[conv] ?: 0)) lectures[conv] = ts
                    }
                }
            }
        }
    }

    /** Remonte l'historique d'un salon jusqu'à retomber sur des messages déjà archivés (ou le début). */
    private fun remonter(salon: String, conv: String, debut: String?, info: InfoSalon, moi: String?): Int {
        var depuis = debut
        var total = 0
        repeat(5000) {
            var chemin = "/_matrix/client/v3/rooms/${Http.enc(salon)}/messages?dir=b&limit=100&filter=${Http.enc(FILTRE_MESSAGES)}"
            if (depuis != null) chemin += "&from=${Http.enc(depuis!!)}"
            val rep = runCatching { m.get(chemin, 20_000) }.getOrNull() ?: return total
            val evs = rep.optJSONArray("chunk")?.objets() ?: emptyList()
            val (lus, nouveaux) = enregistrer(evs, conv, info, moi)
            total += nouveaux
            if ((lus > 0 && nouveaux < lus) || evs.isEmpty()) return total
            depuis = rep.optString("end").takeIf { it.isNotEmpty() } ?: return total
        }
        return total
    }

    /** Archive des événements ; renvoie (messages lus, messages nouveaux). */
    private fun enregistrer(evenements: List<JSONObject>, conv: String, info: InfoSalon, moi: String?): Pair<Int, Int> {
        val lot = ArrayList<Message>()
        val supprimes = ArrayList<String>()
        for (e in evenements) {
            val eid = e.optString("event_id").takeIf { it.isNotEmpty() } ?: continue
            if (e.optString("type") == "m.room.redaction") {
                val r = e.optString("redacts").ifEmpty { e.optJSONObject("content")?.optString("redacts") ?: "" }
                if (r.isNotEmpty()) supprimes.add("${pont.source.id}:$r")
                continue
            }
            messageMatrix(e, info.membres, moi, "${pont.source.id}:$eid", conv)?.let { lot.add(it) }
        }
        val deja = archive.existantes(lot.map { it.cle })
        val nouveaux = lot.filter { it.cle !in deja }
        archive.enregistrerMessages(nouveaux)
        archive.supprimerMessages(supprimes)
        return lot.size to nouveaux.size
    }

    /** Convertit un événement Matrix en message (texte, vocal, photo…). */
    private fun messageMatrix(e: JSONObject, membres: Map<String, String>, moi: String?, cle: String, conv: String): Message? {
        val ts = e.ts("origin_server_ts") ?: return null
        val c = e.optJSONObject("content") ?: return null
        val exp = e.optString("sender").takeIf { it.isNotEmpty() } ?: return null
        if (c.optJSONObject("m.relates_to")?.optString("rel_type") == "m.replace") return null
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
            }
            "m.location" -> { type = "autre"; texte = "[position]" }
            else -> if (corps.isEmpty()) return null
        }
        val deMoi = exp == m.utilisateur || exp == moi
        val auteur = if (deMoi) "Vous" else membres[exp]?.replace(" (WA)", "") ?: "Quelqu'un"
        return Message(cle, conv, ts, deMoi, auteur, texte, type, media, duree)
    }
}
