package fr.messagerie.unifiee

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rapport de plantage gardé sur le téléphone (fichier privé de l'appli, jamais envoyé).
 * Contient seulement la trace technique, pas le contenu des messages.
 */
object Plantages {
    private var fichier: File? = null
    private var version = "?"
    private const val TAILLE_MAX = 60_000

    fun installer(ctx: Context) {
        fichier = File(ctx.filesDir, "plantage.txt")
        version = runCatching { MiseAJour.nomInstalle(ctx) }.getOrDefault("?")
        val precedent = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { fil, t ->
            runCatching { noter(t, fatal = true, fil = fil.name) }
            precedent?.uncaughtException(fil, t)
        }
    }

    /** Ajoute une erreur au rapport (fatal = l'appli s'est arrêtée). */
    @Synchronized
    fun noter(t: Throwable, fatal: Boolean, fil: String = Thread.currentThread().name) {
        val f = fichier ?: return
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date())
        val entete = "=== ${if (fatal) "ARRÊT DE L'APPLI" else "Erreur rattrapée"} — $date — fil $fil — " +
            "v$version — Android ${Build.VERSION.RELEASE} (${Build.MODEL})\n"
        val ancien = if (f.exists()) f.readText() else ""
        val nouveau = (entete + sw.toString().lines().take(60).joinToString("\n") + "\n\n" + ancien).take(TAILLE_MAX)
        f.writeText(nouveau)
    }

    fun rapport(): String? = fichier?.takeIf { it.exists() && it.length() > 0 }?.readText()
    fun aUnArret(): Boolean = rapport()?.contains("ARRÊT DE L'APPLI") == true
    fun effacer() { fichier?.delete() }
}
