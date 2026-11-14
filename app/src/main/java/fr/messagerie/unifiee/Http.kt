package fr.messagerie.unifiee

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ErreurHttp(val code: Int, val chemin: String) : Exception("erreur $code sur $chemin")
class Injoignable(message: String) : Exception(message)

object Http {
    data class Reponse(val code: Int, val corps: ByteArray) {
        val texte get() = String(corps, Charsets.UTF_8)
    }

    fun requete(
        url: String,
        methode: String = "GET",
        jeton: String? = null,
        corps: String? = null,
        delaiMs: Int = 8_000,
    ): Reponse {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = methode
            c.connectTimeout = minOf(delaiMs, 8_000)
            c.readTimeout = delaiMs
            c.useCaches = false
            if (jeton != null) c.setRequestProperty("Authorization", "Bearer $jeton")
            if (corps != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(corps.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val flux = if (code in 200..299) c.inputStream else c.errorStream
            val data = flux?.use { it.readBytes() } ?: ByteArray(0)
            return Reponse(code, data)
        } finally {
            c.disconnect()
        }
    }

    /** Encode tout sauf les caractères alphanumériques (identifiants Matrix dans un chemin). */
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
