package fr.messagerie.unifiee

/**
 * Réactions SMS / RCS venues d'un iPhone (ou de Google Messages vers un iPhone) : elles arrivent comme un texte
 * du genre « 😂 à "message visé" », « A aimé "…" » ou « Reacted 👍 to "…" ».
 * On les reconnaît pour les afficher en réaction collée au message visé, sans bulle ni notification.
 */
object Tapback {
    private const val GUILLEMETS_OUVRANTS = "\"“«„'‘"
    private const val GUILLEMETS_FERMANTS = "\"”»“'’"

    private val EMOJI_A = Regex("""^(\S{1,12})\s+(?:à|a|to)\s+[$GUILLEMETS_OUVRANTS]\s*(.+?)\s*[$GUILLEMETS_FERMANTS]\s*$""", RegexOption.DOT_MATCHES_ALL)
    private val REACTED = Regex("""^Reacted\s+(\S{1,12})\s+to\s+[$GUILLEMETS_OUVRANTS]\s*(.+?)\s*[$GUILLEMETS_FERMANTS]\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val MOTS = listOf(
        "A adoré" to "❤️", "A aimé" to "👍", "N'a pas aimé" to "👎", "N’a pas aimé" to "👎", "A ri de" to "😂",
        "A mis en évidence" to "‼️", "A souligné" to "‼️", "A posé une question sur" to "❓", "S'est interrogé sur" to "❓",
        "Loved" to "❤️", "Liked" to "👍", "Disliked" to "👎", "Laughed at" to "😂", "Emphasized" to "‼️", "Questioned" to "❓",
    )
    private val MOT = Regex("""^(${MOTS.joinToString("|") { Regex.escape(it.first) }})\s+[$GUILLEMETS_OUVRANTS]\s*(.+?)\s*[$GUILLEMETS_FERMANTS]\s*$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** (emoji, texte cité) si le texte est une réaction, sinon null. */
    fun lire(texte: String): Pair<String, String>? {
        val t = texte.trim()
        if (t.length > 2000) return null
        REACTED.find(t)?.let { return it.groupValues[1] to it.groupValues[2] }
        MOT.find(t)?.let { r -> MOTS.firstOrNull { it.first.equals(r.groupValues[1], ignoreCase = true) }?.let { return it.second to r.groupValues[2] } }
        EMOJI_A.find(t)?.let { r ->
            val e = r.groupValues[1]
            // Le premier mot doit être un émoji (aucune lettre ni chiffre)
            if (e.none { it.isLetterOrDigit() }) return e to r.groupValues[2]
        }
        return null
    }

    private fun normal(t: String) = t.replace(Regex("\\s+"), " ").trim().trimEnd('…', '.').trim()

    /** Le message visé parmi les précédents (le plus récent d'abord) : texte identique, ou début identique si la citation est tronquée. */
    fun cible(cite: String, precedents: List<Message>): Message? {
        val c = normal(cite)
        if (c.isEmpty()) return null
        val candidats = precedents.filter { it.type != "reaction" && lire(it.texte) == null }.sortedByDescending { it.date }
        return candidats.firstOrNull { normal(it.texte) == c }
            ?: candidats.firstOrNull { c.length >= 12 && normal(it.texte).startsWith(c) }
            ?: candidats.firstOrNull { normal(it.texte).length >= 12 && c.startsWith(normal(it.texte)) }
    }

    /**
     * Sépare un lot de messages en vrais messages et réactions reconnues.
     * precedents : messages déjà archivés de la conversation (pour retrouver le message visé).
     */
    fun separer(lot: List<Message>, precedents: List<Message>): Pair<List<Message>, List<Reaction>> {
        val messages = ArrayList<Message>(); val reactions = ArrayList<Reaction>()
        for (m in lot.sortedBy { it.date }) {
            val (emoji, cite) = (if (m.type == "texte") lire(m.texte) else null) ?: run { messages += m; null } ?: continue
            val visee = cible(cite, precedents.filter { it.date <= m.date } + messages.filter { it.conv == m.conv })
            if (visee == null) { messages += m; continue }
            reactions += Reaction(m.cle + "#reaction", visee.cle, m.conv, emoji, m.auteur, m.deMoi)
        }
        return messages to reactions
    }
}
