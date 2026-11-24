package fr.messagerie.unifiee

/** Messageries prises en charge. `id` sert de préfixe aux clés (« SMS:!salon… »). */
enum class Source(val id: String, val libelle: String, val couleur: Long) {
    WHATSAPP("WhatsApp", "WhatsApp", 0xFF21B866),
    SMS("SMS", "SMS / RCS", 0xFF337AFA),
    ELEMENT("Element", "Element", 0xFF0DBD8C),
    TELEGRAM("Telegram", "Telegram", 0xFF29A1D9),
    LINKEDIN("LinkedIn", "LinkedIn", 0xFF0A66C2),
    SIGNAL("Signal", "Signal", 0xFF3A76F0),
    TEAMS("Teams", "Teams", 0xFF5B5FC7);

    companion object {
        fun depuis(id: String): Source? = entries.firstOrNull { it.id == id }
    }
}

/** Un message, toutes sources confondues. Dates en millisecondes. */
data class Message(
    val cle: String,              // "<Source>:<event_id>"
    val conv: String,             // clé de la conversation
    val date: Long,
    val deMoi: Boolean,
    val auteur: String,
    val texte: String,
    val type: String = "texte",   // texte, audio, image, video, fichier, autre
    val media: String? = null,
    val duree: Double? = null,
    /** Compléments en JSON : message cité, texte mis en forme, sondage, position, modifié (voir [Extra]). */
    val extra: String? = null,
) {
    val texteUtile: String
        get() = if (type == "audio") "🎤 (message vocal)" else texte

    private val x: org.json.JSONObject? by lazy { extra?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } }
    /** Clé du message auquel celui-ci répond. */
    val cite: String? get() = x?.optString("cite")?.takeIf { it.isNotEmpty() }
    /** Texte mis en forme (HTML simple des messageries). */
    val html: String? get() = x?.optString("html")?.takeIf { it.isNotEmpty() }
    /** Position partagée : « geo:lat,lon ». */
    val geo: String? get() = x?.optString("geo")?.takeIf { it.isNotEmpty() }
    val modifie: Boolean get() = x?.optBoolean("modifie") == true
    /** Sondage : question, réponses (id → texte), choix multiples, format (« msc3381 » ou « stable »). */
    val sondage: Sondage? get() = x?.optJSONObject("sondage")?.let { o ->
        val r = o.optJSONArray("reponses") ?: return@let null
        Sondage(o.optString("question"), (0 until r.length()).mapNotNull { i -> r.optJSONObject(i)?.let { it.optString("id") to it.optString("texte") } },
            o.optInt("max", 1), o.optString("format", "msc3381"))
    }
}

data class Sondage(val question: String, val reponses: List<Pair<String, String>>, val max: Int, val format: String)

/** Vote à un sondage (le dernier vote de chaque personne compte). */
data class Vote(val cle: String, val sondage: String, val conv: String, val auteur: String, val deMoi: Boolean, val choix: List<String>, val date: Long)

/** Fabrique le JSON « extra » d'un message (null si vide). */
object Extra {
    fun de(vararg paires: Pair<String, Any?>): String? {
        val o = org.json.JSONObject()
        for ((k, v) in paires) if (v != null && v != false && v != "") o.put(k, v)
        return if (o.length() == 0) null else o.toString()
    }
    /** Ajoute ou remplace une valeur dans un extra existant. */
    fun avec(extra: String?, cle: String, valeur: Any?): String? {
        val o = extra?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: org.json.JSONObject()
        if (valeur == null) o.remove(cle) else o.put(cle, valeur)
        return if (o.length() == 0) null else o.toString()
    }
}

data class Conversation(
    val cle: String,              // "<Source>:<jid>"
    val source: Source,
    val jid: String,
    val nom: String,
    val estGroupe: Boolean,
    val derniere: Long,
    val messages: List<Message>,  // derniers messages (ordre chronologique)
) {
    val dernierDeMoi: Boolean get() = messages.lastOrNull()?.deMoi ?: false
}

/** Résumé IA, valable tant qu'aucun nouveau message n'arrive. */
data class Synthese(val resume: String, val actionRequise: Boolean, val reponse: String, val pourLeMessageDu: Long)

/** État local d'une conversation (rien de tout cela n'est envoyé aux réseaux). */
data class EtatConv(
    val lue: Long = 0,            // date du dernier message lu dans l'appli
    val forcee: Long = 0,         // « marquer comme non lu » (valable jusqu'au prochain message)
    val traitee: Long = 0,
    val notifiee: Long = 0,
    val manuel: Boolean = false,  // « À traiter » posé à la main
    val sourdine: Boolean = false,
)

sealed class EtatSource {
    data object Inactif : EtatSource()
    data object NonConfigure : EtatSource()
    data class Ok(val conversations: Int) : EtatSource()
    data class Erreur(val message: String) : EtatSource()
}

fun cleConv(source: Source, jid: String) = "${source.id}:$jid"

/** Réaction (émoji) posée sur un message, par un contact ou par vous. */
data class Reaction(val cle: String, val cible: String, val conv: String, val emoji: String, val auteur: String, val deMoi: Boolean)

/** Votre réaction choisie dans l'appli. `idEnvoi` : identifiant Matrix si elle a été transmise au contact. */
data class MaReaction(val cible: String, val conv: String, val emoji: String, val salon: String?, val idEnvoi: String?)
