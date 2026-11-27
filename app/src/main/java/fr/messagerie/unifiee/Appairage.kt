package fr.messagerie.unifiee

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Connexion d'un nouvel appareil par QR code.
 *
 * L'appareil déjà configuré chiffre sa configuration (AES-256-GCM, clé aléatoire) et la dépose
 * sur le relais (POST /appairage, avec le jeton du relais). Le QR code contient les adresses
 * du relais, le code de retrait et la clé : le relais ne voit jamais la configuration en clair.
 * Le nouvel appareil retire le paquet (GET /appairage/<code>) : usage unique, valable 3 minutes,
 * gardé en mémoire seulement par le relais.
 *
 * Format du QR : messagerie://appairage?v=1&c=<code>&k=<clé base64url>&r=<adresse>&r=<adresse>…
 * Paquet : base64url( nonce 12 octets ‖ texte chiffré ‖ étiquette 16 octets ), même format que
 * AES.GCM.SealedBox.combined de l'app Mac. Contenu : messagerie-config.json + "appareil".
 */
object Appairage {
    const val DUREE_MS = 180_000L
    private const val B64 = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    class Session(val base: String, val code: String, val expire: Long, val lien: String)
    class Lien(val relais: List<String>, val code: String, val cle: ByteArray)
    class Recu(val appareil: String, val config: String, val contenu: List<String>)

    /** Adresses utilisables depuis un autre appareil (jamais 127.0.0.1 ni localhost). */
    private fun adressesDistantes(cfg: Config) = Config.liste(cfg.relaisUrls).filter { u ->
        val h = runCatching { java.net.URI(u).host?.lowercase() }.getOrNull() ?: return@filter false
        h != "localhost" && !h.startsWith("127.") && h != "::1" && h != "[::1]"
    }

    /** Dépose la configuration chiffrée sur le relais et renvoie le lien à mettre dans le QR code. Réseau : hors du fil principal. */
    fun creer(cfg: Config, appareil: String): Session {
        if (!cfg.relaisConfigure) throw Injoignable("Le relais n'est pas configuré sur cet appareil : il sert à transmettre la configuration.")
        val adresses = adressesDistantes(cfg)
        if (adresses.isEmpty()) throw Injoignable("Aucune adresse du relais joignable depuis un autre appareil.")
        val essais = (listOfNotNull(Relais.derniereBonne) + adresses).distinct()
        val base = Http.premiereQuiRepond(essais) { Http.requete("$it/etat", jeton = cfg.relaisJeton, delaiMs = 5_000).code == 200 }
            ?: throw Injoignable("Relais injoignable : le serveur est-il allumé ?")

        val cle = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val clair = JSONObject(cfg.exporterJson()).put("appareil", appareil).toString().toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(128, nonce))
        val paquet = Base64.encodeToString(nonce + c.doFinal(clair), B64)

        val r = Http.requete("$base/appairage", "POST", cfg.relaisJeton, JSONObject().put("donnees", paquet).toString(), 10_000)
        if (r.code == 404) throw Exception("Le relais ne connaît pas encore l'appairage : il doit être mis à jour.")
        if (r.code != 200) throw ErreurHttp(r.code, "/appairage")
        val j = JSONObject(r.texte)
        val code = j.getString("code")
        val lien = Uri.Builder().scheme("messagerie").authority("appairage")
            .appendQueryParameter("v", "1").appendQueryParameter("c", code)
            .appendQueryParameter("k", Base64.encodeToString(cle, B64))
            .apply { adresses.forEach { appendQueryParameter("r", it) } }
            .build().toString()
        return Session(base, code, j.optLong("expire", System.currentTimeMillis() + DUREE_MS), lien)
    }

    /** Le nouvel appareil a-t-il retiré la configuration ? (recupere, expire) */
    fun etat(cfg: Config, s: Session): Pair<Boolean, Boolean> {
        val r = Http.requete("${s.base}/appairage-etat?code=${Http.enc(s.code)}", jeton = cfg.relaisJeton, delaiMs = 5_000)
        if (r.code != 200) return false to (System.currentTimeMillis() > s.expire)
        val j = JSONObject(r.texte)
        return j.optBoolean("recupere") to j.optBoolean("expire")
    }

    /** Annule un code non utilisé (en quittant l'écran du QR code). */
    fun annuler(cfg: Config, s: Session) {
        runCatching { Http.requete("${s.base}/appairage/${Http.enc(s.code)}", "DELETE", cfg.relaisJeton, delaiMs = 5_000) }
    }

    /** Lit le texte d'un QR code ; null si ce n'est pas un QR de connexion de Messagerie. */
    fun lire(texte: String): Lien? = runCatching {
        val u = Uri.parse(texte.trim())
        if (u.scheme != "messagerie" || u.host != "appairage") return null
        val code = u.getQueryParameter("c")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{20,64}")) } ?: return null
        val cle = Base64.decode(u.getQueryParameter("k") ?: return null, B64).takeIf { it.size == 32 } ?: return null
        val relais = u.getQueryParameters("r").map { it.trim().trimEnd('/') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }.distinct()
        if (relais.isEmpty()) return null
        Lien(relais, code, cle)
    }.getOrNull()

    /** Retire la configuration sur le relais (usage unique) et la déchiffre. Réseau : hors du fil principal. */
    fun recuperer(l: Lien): Recu {
        // Le relais répond 401 à /etat sans jeton : ça suffit pour savoir qu'il est joignable
        val base = Http.premiereQuiRepond(l.relais) { Http.requete("$it/etat", delaiMs = 5_000).code in listOf(200, 401) }
            ?: throw Injoignable("Relais injoignable depuis cet appareil. Il doit être sur le même réseau que le serveur (ou sur le VPN).")
        val r = Http.requete("$base/appairage/${Http.enc(l.code)}", delaiMs = 10_000)
        if (r.code == 404) throw Exception("Ce QR code a expiré ou a déjà servi. Affichez-en un nouveau sur l'autre appareil.")
        if (r.code != 200) throw ErreurHttp(r.code, "/appairage")
        val octets = Base64.decode(JSONObject(r.texte).getString("donnees"), B64)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(l.cle, "AES"), GCMParameterSpec(128, octets, 0, 12))
        val clair = try { String(c.doFinal(octets, 12, octets.size - 12), Charsets.UTF_8) }
            catch (_: Exception) { throw Exception("Configuration illisible : le QR code ne correspond pas.") }
        val j = JSONObject(clair)
        val contenu = buildList {
            if (j.has("matrix")) add("serveur Matrix")
            if (j.has("relais")) add("relais Element")
            if (j.optJSONObject("ollama")?.optString("adresses").orEmpty().isNotEmpty()) add("IA (Ollama)")
        }
        return Recu(j.optString("appareil").ifEmpty { "un autre appareil" }, clair, contenu)
    }

    /** Nom de cet appareil, montré sur le nouvel appareil (« Configuration reçue de … »). */
    fun nomAppareil(): String =
        listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL).filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }.joinToString(" ").replaceFirstChar { it.uppercase() }
}
