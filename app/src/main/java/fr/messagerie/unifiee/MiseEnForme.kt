package fr.messagerie.unifiee

/**
 * Mise en forme du texte : *gras*, _italique_, ~barré~, `code` (comme WhatsApp et Signal).
 * À l'envoi, ces marques deviennent du HTML Matrix (formatted_body) que les passerelles traduisent
 * pour chaque messagerie. À la réception, le HTML des passerelles est affiché avec ses styles.
 */
object MiseEnForme {
    private val GRAS = Regex("""(?<![\w*])\*(?=\S)([^*\n]+?)(?<=\S)\*(?![\w*])""")
    private val ITALIQUE = Regex("""(?<![\w_])_(?=\S)([^_\n]+?)(?<=\S)_(?![\w_])""")
    private val BARRE = Regex("""(?<![\w~])~(?=\S)([^~\n]+?)(?<=\S)~(?![\w~])""")
    private val CODE = Regex("""`([^`\n]+)`""")

    fun aDesMarques(t: String) = listOf(GRAS, ITALIQUE, BARRE, CODE).any { it.containsMatchIn(t) }

    /** Texte déjà échappé en HTML → HTML avec les styles. */
    fun versHtml(echappe: String): String = echappe
        .replace(CODE) { "<code>" + it.groupValues[1] + "</code>" }
        .replace(GRAS) { "<strong>" + it.groupValues[1] + "</strong>" }
        .replace(ITALIQUE) { "<em>" + it.groupValues[1] + "</em>" }
        .replace(BARRE) { "<del>" + it.groupValues[1] + "</del>" }

    /** Style d'un passage du texte affiché. */
    enum class Style { GRAS, ITALIQUE, BARRE, CODE, CITATION }
    data class Passage(val debut: Int, val fin: Int, val style: Style)

    /**
     * Lit le HTML d'un message reçu : texte affichable et passages stylés.
     * null si le HTML ne contient aucun style utile (le texte brut suffit alors).
     */
    fun lire(html: String): Pair<String, List<Passage>>? {
        val prepare = html
            .replace(Regex("(?i)<(/?)code>"), "<$1tt>")
            .replace(Regex("(?i)<pre[^>]*>"), "<tt>").replace(Regex("(?i)</pre>"), "</tt><br>")
            .replace(Regex("(?i)<(/?)s>"), "<$1strike>").replace(Regex("(?i)<(/?)del>"), "<$1strike>")
        val sp = runCatching { android.text.Html.fromHtml(prepare, android.text.Html.FROM_HTML_MODE_COMPACT) }.getOrNull() ?: return null
        val texte = sp.toString().trimEnd()
        val passages = ArrayList<Passage>()
        for (o in sp.getSpans(0, sp.length, Any::class.java)) {
            val d = sp.getSpanStart(o).coerceAtMost(texte.length); val f = sp.getSpanEnd(o).coerceAtMost(texte.length)
            if (f <= d) continue
            val style = when (o) {
                is android.text.style.StyleSpan -> when (o.style) {
                    android.graphics.Typeface.BOLD -> Style.GRAS
                    android.graphics.Typeface.ITALIC -> Style.ITALIQUE
                    else -> null
                }
                is android.text.style.StrikethroughSpan -> Style.BARRE
                is android.text.style.TypefaceSpan -> if (o.family == "monospace") Style.CODE else null
                is android.text.style.QuoteSpan -> Style.CITATION
                else -> null
            } ?: continue
            passages.add(Passage(d, f, style))
        }
        return if (passages.isEmpty() && !texte.contains('•')) null else texte to passages
    }
}
