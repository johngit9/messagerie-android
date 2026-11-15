package fr.messagerie.unifiee

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Enregistrement d'un message vocal (fichier local) puis aperçu avant envoi.
 * Rien ne part sans appui sur « Envoyer ».
 */
class Enregistreur(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var lecteur: MediaPlayer? = null
    private var debut = 0L
    var fichier: File? = null; private set
    var dureeMs = 0L; private set
    /** OGG/Opus (vocal natif WhatsApp) depuis Android 10, sinon AAC. */
    val typeMime = if (Build.VERSION.SDK_INT >= 29) "audio/ogg" else "audio/mp4"

    fun demarrer() {
        annuler()
        val dossier = File(ctx.cacheDir, "vocaux").apply { mkdirs() }
        val f = File(dossier, "Message vocal " + System.currentTimeMillis() + if (Build.VERSION.SDK_INT >= 29) ".ogg" else ".m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        if (Build.VERSION.SDK_INT >= 29) {
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG); r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        } else {
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        r.setAudioSamplingRate(48_000); r.setAudioEncodingBitRate(32_000); r.setAudioChannels(1)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        rec = r; fichier = f; debut = System.currentTimeMillis(); dureeMs = 0
    }

    fun ecoule(): Long = if (rec != null) System.currentTimeMillis() - debut else dureeMs

    /** Arrête l'enregistrement ; false s'il est trop court ou raté. */
    fun arreter(): Boolean {
        val r = rec ?: return false
        rec = null
        dureeMs = System.currentTimeMillis() - debut
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || dureeMs < 700) { supprimerFichier(); return false }
        return true
    }

    fun ecouter(fin: () -> Unit) {
        arreterEcoute()
        val f = fichier ?: return
        lecteur = MediaPlayer().apply {
            setDataSource(f.absolutePath); setOnCompletionListener { fin(); arreterEcoute() }
            prepare(); start()
        }
    }

    fun arreterEcoute() { lecteur?.let { runCatching { it.stop() }; it.release() }; lecteur = null }

    fun annuler() {
        rec?.let { runCatching { it.stop() }; it.release() }; rec = null
        arreterEcoute(); supprimerFichier()
    }

    private fun supprimerFichier() { fichier?.delete(); fichier = null; dureeMs = 0 }
}
