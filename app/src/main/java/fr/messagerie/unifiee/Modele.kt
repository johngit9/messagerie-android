package fr.messagerie.unifiee

/** Messageries prises en charge. `id` sert de préfixe aux clés (« SMS:!salon… »). */
enum class Source(val id: String, val libelle: String, val couleur: Long) {
    WHATSAPP("WhatsApp", "WhatsApp", 0xFF21B866),
    SMS("SMS", "SMS / RCS", 0xFF337AFA),
    ELEMENT("Element", "Element", 0xFF0DBD8C),
    TELEGRAM("Telegram", "Telegram", 0xFF29A1D9),
    LINKEDIN("LinkedIn", "LinkedIn", 0xFF0A66C2);

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
) {
    val texteUtile: String
        get() = if (type == "audio") "🎤 (message vocal)" else texte
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
