package fr.messagerie.unifiee

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ErreurHttp(val code: Int, val chemin: String) : Exception("erreur $code sur $chemin")
class Injoignable(message: String) : Exception(message)

object Http {
    private val sondes = java.util.concurrent.Executors.newCachedThreadPool()

    /**
     * Essaie toutes les adresses EN MÊME TEMPS et renvoie la première qui répond (test vrai), sinon null.
     * Au démarrage, une adresse injoignable (réseau local hors de chez vous…) ne fait plus attendre les autres.
     */
    fun premiereQuiRepond(adresses: List<String>, test: (String) -> Boolean): String? {
        if (adresses.isEmpty()) return null
        if (adresses.size == 1) return adresses[0].takeIf { runCatching { test(it) }.getOrDefault(false) }
        val file = java.util.concurrent.LinkedBlockingQueue<Pair<String, Boolean>>()
        adresses.forEach { u -> sondes.execute { file.put(u to runCatching { test(u) }.getOrDefault(false)) } }
        repeat(adresses.size) {
            val (u, ok) = file.poll(15, java.util.concurrent.TimeUnit.SECONDS) ?: return null
            if (ok) return u
        }
        return null
    }

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
        // Pas de disconnect() en cas de succès : la connexion reste ouverte et resservira (plus rapide).
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
        } catch (e: Exception) {
            c.disconnect(); throw e
        }
    }

    /** Envoie des octets bruts (téléversement d'un média). */
    fun envoyerOctets(url: String, jeton: String?, octets: ByteArray, typeMime: String, delaiMs: Int = 60_000): Reponse {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8_000; c.readTimeout = delaiMs; c.useCaches = false
            if (jeton != null) c.setRequestProperty("Authorization", "Bearer $jeton")
            c.doOutput = true
            c.setRequestProperty("Content-Type", typeMime)
            c.setFixedLengthStreamingMode(octets.size)
            c.outputStream.use { it.write(octets) }
            val code = c.responseCode
            val flux = if (code in 200..299) c.inputStream else c.errorStream
            return Reponse(code, flux?.use { it.readBytes() } ?: ByteArray(0))
        } catch (e: Exception) { c.disconnect(); throw e }
    }

    /** Encode tout sauf les caractères alphanumériques (identifiants Matrix dans un chemin). */
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
