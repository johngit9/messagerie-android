package fr.messagerie.unifiee

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mise à jour de l'appli depuis les « Releases » publiques du dépôt GitHub.
 * Seule la page publique des versions est consultée : aucune donnée personnelle n'est envoyée.
 */
object MiseAJour {
    private const val DERNIERE = "https://api.github.com/repos/johngit9/messagerie-android/releases/latest"

    data class Version(val numero: Int, val nom: String, val notes: String, val apk: String, val taille: Long)

    fun versionInstallee(ctx: Context): Int {
        val i = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) i.longVersionCode.toInt() else @Suppress("DEPRECATION") i.versionCode
    }

    fun nomInstalle(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Dernière version publiée (ou null si on n'a pas pu la lire). */
    suspend fun derniere(): Version = withContext(Dispatchers.IO) {
        val c = URL(DERNIERE).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        if (code != 200) throw Exception("GitHub a répondu $code")
        val j = JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
        val tag = j.optString("tag_name")                       // « v1.8 »
        val numero = tag.substringAfterLast('.').toIntOrNull() ?: throw Exception("version illisible : $tag")
        val assets = j.optJSONArray("assets")
        var apk = ""; var taille = 0L
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); taille = a.optLong("size") }
        }
        if (apk.isEmpty()) throw Exception("aucun APK dans la dernière version")
        Version(numero, tag, j.optString("body").trim(), apk, taille)
    }

    /** Télécharge l'APK (progression de 0 à 1). */
    suspend fun telecharger(ctx: Context, v: Version, progression: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dossier = File(ctx.cacheDir, "maj").apply { deleteRecursively(); mkdirs() }
        val f = File(dossier, "Messagerie-${v.nom}.apk")
        var url = v.apk
        var c: HttpURLConnection
        var sauts = 0
        while (true) {   // GitHub redirige vers son stockage
            c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000; c.readTimeout = 30_000
            val code = c.responseCode
            if (code in 300..399 && sauts++ < 5) { url = c.getHeaderField("Location"); c.disconnect(); continue }
            if (code != 200) throw Exception("téléchargement refusé ($code)")
            break
        }
        val total = c.contentLengthLong.takeIf { it > 0 } ?: v.taille
        c.inputStream.use { entree ->
            f.outputStream().use { sortie ->
                val tampon = ByteArray(64 * 1024)
                var lu = 0L
                while (true) {
                    val n = entree.read(tampon)
                    if (n < 0) break
                    sortie.write(tampon, 0, n)
                    lu += n
                    if (total > 0) withContext(Dispatchers.Main) { progression((lu.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }
        }
        f
    }

    /** L'appli a-t-elle le droit d'installer des mises à jour ? */
    fun peutInstaller(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Ouvre l'écran système qui autorise l'appli à installer ses mises à jour. */
    fun demanderAutorisation(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Lance l'installeur d'Android : il installe par-dessus la version actuelle (réglages et messages conservés). */
    fun installer(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
