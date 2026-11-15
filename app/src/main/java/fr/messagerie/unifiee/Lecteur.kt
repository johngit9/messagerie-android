package fr.messagerie.unifiee

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Lecteur des messages vocaux : un seul vocal joue à la fois. */
object Lecteur {
    data class Etat(
        val cle: String? = null,       // message en cours
        val chargement: Boolean = false,
        val enLecture: Boolean = false,
        val position: Int = 0,         // ms
        val duree: Int = 0,            // ms
        val erreur: String? = null,
    )

    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat.asStateFlow()
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lecteur: MediaPlayer? = null
    private var suivi: Job? = null

    /** Lecture / pause du vocal `m` (téléchargé au premier appui). */
    fun basculer(m: Message) {
        val e = _etat.value
        val mp = lecteur
        if (e.cle == m.cle && mp != null) {
            if (mp.isPlaying) { mp.pause(); _etat.value = e.copy(enLecture = false) }
            else { mp.start(); _etat.value = e.copy(enLecture = true); suivre() }
            return
        }
        arreter()
        _etat.value = Etat(cle = m.cle, chargement = true)
        portee.launch {
            try {
                val f = Moteur.fichierMedia(m)
                if (_etat.value.cle != m.cle) return@launch
                val p = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(f.absolutePath)
                    prepare()
                    setOnCompletionListener { _etat.value = _etat.value.copy(enLecture = false, position = 0); suivi?.cancel() }
                }
                lecteur = p
                p.start()
                _etat.value = Etat(cle = m.cle, enLecture = true, duree = p.duration)
                suivre()
            } catch (ex: Exception) {
                _etat.value = Etat(cle = m.cle, erreur = "Lecture impossible : ${ex.message ?: ex.javaClass.simpleName}")
            }
        }
    }

    /** Avance à une fraction (0..1) du vocal en cours. */
    fun aller(fraction: Float) {
        val p = lecteur ?: return
        val pos = (p.duration * fraction).toInt()
        p.seekTo(pos)
        _etat.value = _etat.value.copy(position = pos)
    }

    private fun suivre() {
        suivi?.cancel()
        suivi = portee.launch {
            while (isActive) {
                val p = lecteur ?: break
                try { if (p.isPlaying) _etat.value = _etat.value.copy(position = p.currentPosition) } catch (_: Exception) { break }
                delay(200)
            }
        }
    }

    fun arreter() {
        suivi?.cancel()
        lecteur?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        lecteur = null
        _etat.value = Etat()
    }
}
