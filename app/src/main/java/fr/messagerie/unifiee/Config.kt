package fr.messagerie.unifiee

import android.content.Context
import android.content.SharedPreferences

/**
 * Réglages et identifiants de connexion, saisis dans l'appli (écran Réglages).
 * Rien n'est codé en dur : aucune adresse ni aucun jeton n'est livré avec l'appli.
 * Stockés dans les préférences privées de l'appli (non sauvegardées : allowBackup=false).
 */
class Config(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    private fun s(k: String) = p.getString(k, "")!!.trim()
    private fun set(k: String, v: String) = p.edit().putString(k, v.trim()).apply()

    // Serveur Matrix (Synapse) et passerelles mautrix
    var matrixUrls: String get() = s("matrix.urls"); set(v) = set("matrix.urls", v)   // une ou plusieurs adresses, séparées par des virgules
    var matrixUtilisateur: String get() = s("matrix.user"); set(v) = set("matrix.user", v)
    var matrixJeton: String get() = s("matrix.jeton"); set(v) = set("matrix.jeton", v)
    var moiFantome: String get() = s("matrix.moi"); set(v) = set("matrix.moi", v)

    // Relais Element
    var relaisUrls: String get() = s("relais.urls"); set(v) = set("relais.urls", v)
    var relaisJeton: String get() = s("relais.jeton"); set(v) = set("relais.jeton", v)

    // Ollama
    var ollamaUrls: String get() = s("ollama.urls"); set(v) = set("ollama.urls", v)
    var ollamaModele: String get() = s("ollama.modele").ifEmpty { "qwen3:8b" }; set(v) = set("ollama.modele", v)

    // Préférences
    var jours: Int get() = p.getInt("jours", 3); set(v) = p.edit().putInt("jours", v).apply()
    var intervalle: Int get() = p.getInt("intervalle", 60); set(v) = p.edit().putInt("intervalle", v).apply()
    var notifications: Boolean get() = p.getBoolean("notifications", true); set(v) = p.edit().putBoolean("notifications", v).apply()
    var veilleArrierePlan: Boolean get() = p.getBoolean("veille", false); set(v) = p.edit().putBoolean("veille", v).apply()

    /** Les messages plus anciens comptent comme lus (fixé au premier lancement : maintenant − 24 h). */
    val debutLecture: Long
        get() {
            if (!p.contains("debutLecture")) p.edit().putLong("debutLecture", System.currentTimeMillis() - 86_400_000).apply()
            return p.getLong("debutLecture", 0)
        }

    val matrixConfigure get() = matrixUrls.isNotEmpty() && matrixJeton.isNotEmpty() && matrixUtilisateur.isNotEmpty()
    val relaisConfigure get() = relaisUrls.isNotEmpty() && relaisJeton.isNotEmpty()
    val ollamaConfigure get() = ollamaUrls.isNotEmpty()
    val configure get() = matrixConfigure || relaisConfigure

    companion object {
        fun liste(s: String) = s.split(',', ' ', '\n').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
    }
}
