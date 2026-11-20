package fr.messagerie.unifiee

import java.net.HttpURLConnection
import java.net.URL

/**
 * Aperçu d'un lien (titre, description, image) lu dans les balises Open Graph de la page.
 * Simple lecture de la page (sans cookie ni identifiant), gardée en mémoire pour ne pas la relire.
 */
object ApercusLiens {
    class Apercu(val url: String, val site: String, val titre: String?, val description: String?, val image: ByteArray?)

    private val cache = android.util.LruCache<String, Any>(200)   // Apercu, ou AUCUN si la page n'a rien donné
    private val AUCUN = Any()

    fun enCache(url: String): Apercu? = cache.get(url) as? Apercu
    fun dejaEssaye(url: String) = cache.get(url) != null

    /** À appeler hors du fil principal. */
    fun charger(url: String): Apercu? {
        cache.get(url)?.let { return it as? Apercu }
        val r = runCatching { lire(url) }.getOrNull()
        cache.put(url, r ?: AUCUN)
        return r
    }

    private fun ouvrir(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
            setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
        }

    private fun lire(url: String): Apercu? {
        var cx = ouvrir(url)
        // Redirections http → https (non suivies automatiquement par Android)
        repeat(5) {
            val code = cx.responseCode
            if (code !in 300..399) return@repeat
            val suite = cx.getHeaderField("Location") ?: return@repeat
            val cible = URL(cx.url, suite).toString()
            cx.disconnect(); cx = ouvrir(cible)
        }
        try {
            if (cx.responseCode !in 200..299) return null
            val type = cx.contentType.orEmpty()
            val finale = cx.url.toString()
            val site = cx.url.host.removePrefix("www.")
            if (!type.contains("html", true)) return null
            // Les balises utiles sont dans l'en-tête : 300 Ko suffisent
            val html = cx.inputStream.use { s ->
                val tampon = ByteArray(300_000); var n = 0
                while (n < tampon.size) { val k = s.read(tampon, n, tampon.size - n); if (k < 0) break; n += k }
                String(tampon, 0, n, charset(Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(type)?.groupValues?.get(1)?.let {
                    runCatching { charset(it); it }.getOrNull() } ?: "UTF-8"))
            }
            fun meta(vararg noms: String): String? = noms.firstNotNullOfOrNull { nom ->
                Regex("""<meta[^>]+(?:property|name)\s*=\s*["']$nom["'][^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value?.let { balise ->
                    Regex("""content\s*=\s*(["'])(.*?)\1""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(balise)?.groupValues?.get(2)
                }?.let { decoder(it) }?.takeIf { it.isNotBlank() }
            }
            val titre = meta("og:title", "twitter:title")
                ?: Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)?.let { decoder(it) }
            val description = meta("og:description", "twitter:description", "description")
            val image = meta("og:image", "og:image:url", "twitter:image")?.let { runCatching { URL(URL(finale), it).toString() }.getOrNull() }
                ?.let { telechargerImage(it) }
            if (titre.isNullOrBlank() && description == null && image == null) return null
            return Apercu(finale, site, titre?.trim()?.take(200), description?.trim()?.take(300), image)
        } finally { cx.disconnect() }
    }

    private fun telechargerImage(url: String): ByteArray? = runCatching {
        val cx = ouvrir(url)
        try {
            if (cx.responseCode !in 200..299 || !cx.contentType.orEmpty().startsWith("image/")) return null
            if (cx.contentLengthLong > 3_000_000) return null
            cx.inputStream.use { it.readBytes() }.takeIf { it.size <= 3_000_000 }
        } finally { cx.disconnect() }
    }.getOrNull()

    private fun decoder(t: String): String =
        android.text.Html.fromHtml(t, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
}
