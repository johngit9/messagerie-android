package fr.messagerie.unifiee

import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

/**
 * Transcription des messages vocaux avec la reconnaissance vocale du téléphone (Android 13 et plus).
 * Le vocal est décodé sur le téléphone puis donné au service de reconnaissance ; la reconnaissance
 * « sur l'appareil » est utilisée quand le téléphone la propose. Le texte reste sur le téléphone.
 */
object Transcripteur {
    val possible get() = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isRecognitionAvailable(Moteur.app)
    private val unAlaFois = Mutex()

    /** Texte du vocal, ou une exception avec un message lisible. */
    suspend fun transcrire(m: Message): String = unAlaFois.withLock {
        if (Build.VERSION.SDK_INT < 33) throw IllegalStateException("il faut Android 13 ou plus")
        val fichier = Moteur.fichierMedia(m)
        val pcm = withContext(Dispatchers.IO) { decoder(fichier) }
        try {
            withTimeoutOrNull(180_000) { reconnaitre(pcm.first, pcm.second, pcm.third) }
                ?: throw IllegalStateException("la reconnaissance n'a pas répondu")
        } finally { pcm.first.delete() }
    }

    /** Décode le vocal (ogg/opus, m4a…) en PCM 16 bits mono. Renvoie (fichier, fréquence, canaux). */
    private fun decoder(source: File): Triple<File, Int, Int> {
        val ex = MediaExtractor(); ex.setDataSource(source.absolutePath)
        val piste = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: throw IllegalStateException("pas de piste audio")
        ex.selectTrack(piste)
        val format = ex.getTrackFormat(piste)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0); codec.start()
        val sortie = File(Moteur.app.cacheDir, "transcription.pcm")
        var frequence = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var canaux = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        FileOutputStream(sortie).use { out ->
            val info = MediaCodec.BufferInfo()
            var finEntree = false
            while (true) {
                if (!finEntree) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); finEntree = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    codec.outputFormat.let { f -> frequence = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); canaux = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                } else if (o >= 0) {
                    val b = codec.getOutputBuffer(o)!!
                    val octets = ByteArray(info.size); b.position(info.offset); b.get(octets)
                    out.write(octets)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        codec.stop(); codec.release(); ex.release()
        return Triple(sortie, frequence, canaux)
    }

    private suspend fun reconnaitre(pcm: File, frequence: Int, canaux: Int): String = withContext(Dispatchers.Main) {
        val app = Moteur.app
        val surAppareil = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        val r = if (surAppareil) SpeechRecognizer.createOnDeviceSpeechRecognizer(app) else SpeechRecognizer.createSpeechRecognizer(app)
        val fini = CompletableDeferred<String>()
        val morceaux = ArrayList<String>()
        val pfd = ParcelFileDescriptor.open(pcm, ParcelFileDescriptor.MODE_READ_ONLY)
        r.setRecognitionListener(object : RecognitionListener {
            private fun texte(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            override fun onSegmentResults(b: Bundle) { texte(b).takeIf { it.isNotEmpty() }?.let { morceaux += it } }
            override fun onEndOfSegmentedSession() { fini.complete(morceaux.joinToString(" ")) }
            override fun onResults(b: Bundle?) { texte(b).takeIf { it.isNotEmpty() }?.let { morceaux += it }; fini.complete(morceaux.joinToString(" ")) }
            override fun onError(code: Int) {
                if (morceaux.isNotEmpty()) fini.complete(morceaux.joinToString(" "))
                else fini.completeExceptionally(IllegalStateException(when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "aucune parole reconnue"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "autorisez le micro pour Messagerie"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "réseau indisponible pour la reconnaissance"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "le français n'est pas installé pour la reconnaissance vocale"
                    else -> "la reconnaissance a échoué (code $code)"
                }))
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, canaux)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, frequence)
            .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        try {
            r.startListening(intent)
            fini.await()
        } finally {
            runCatching { r.destroy() }; runCatching { pfd.close() }
        }
    }
}
