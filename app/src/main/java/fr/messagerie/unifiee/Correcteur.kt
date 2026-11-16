package fr.messagerie.unifiee

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Une faute repérée : position dans le texte et suggestions du correcteur. */
data class Faute(val debut: Int, val fin: Int, val mot: String, val suggestions: List<String>)

/**
 * Correcteur d'orthographe du téléphone (celui du clavier, ex. Gboard), en français.
 * Tout se passe sur le téléphone : rien n'est envoyé.
 */
class Correcteur(ctx: Context) : SpellCheckerSession.SpellCheckerSessionListener {
    private var attente: CompletableDeferred<Array<SentenceSuggestionsInfo>?>? = null
    private val session: SpellCheckerSession? = runCatching {
        ctx.getSystemService(TextServicesManager::class.java)
            ?.newSpellCheckerSession(null, Locale.FRANCE, this, true)
    }.getOrNull()

    val disponible get() = session != null

    suspend fun verifier(texte: String): List<Faute> {
        val s = session ?: return emptyList()
        if (texte.isBlank()) return emptyList()
        val d = CompletableDeferred<Array<SentenceSuggestionsInfo>?>()
        attente?.complete(null)
        attente = d
        runCatching { s.getSentenceSuggestions(arrayOf(TextInfo(texte)), 4) }.onFailure { return emptyList() }
        val res = withTimeoutOrNull(3_000) { d.await() } ?: return emptyList()
        val fautes = ArrayList<Faute>()
        for (phrase in res) {
            for (i in 0 until phrase.suggestionsCount) {
                val info = phrase.getSuggestionsInfoAt(i) ?: continue
                val typo = info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
                val connu = info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY != 0
                if (!typo || connu) continue
                val debut = phrase.getOffsetAt(i); val fin = debut + phrase.getLengthAt(i)
                if (debut < 0 || fin > texte.length || fin <= debut) continue
                val mot = texte.substring(debut, fin)
                // Mentions, liens, nombres : jamais signalés
                if (mot.startsWith("@") || mot.contains("://") || mot.any { it.isDigit() }) continue
                val sugg = (0 until maxOf(0, info.suggestionsCount)).map { info.getSuggestionAt(it) }.filter { it.isNotBlank() && it != mot }
                fautes += Faute(debut, fin, mot, sugg.take(3))
            }
        }
        return fautes
    }

    fun fermer() { runCatching { session?.close() } }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {}
    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        @Suppress("UNCHECKED_CAST")
        attente?.complete(results as Array<SentenceSuggestionsInfo>?)
    }
}
