package fr.messagerie.unifiee

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Police de l'appli (Réglages > Apparence > Police). Polices libres (SIL OFL) incluses dans l'APK,
 * rien n'est téléchargé. 0 = police du téléphone.
 */
object Polices {
    val choix = listOf(
        0 to "Téléphone", 1 to "Inter", 2 to "Nunito", 3 to "Manrope", 4 to "Lora", 5 to "Atkinson",
    )
    val descriptions = mapOf(
        0 to "La police d'Android", 1 to "Nette et moderne", 2 to "Arrondie et douce", 3 to "Géométrique",
        4 to "Avec empattements, style livre", 5 to "Très lisible, conçue pour la malvoyance",
    )

    private val cache = HashMap<Int, FontFamily?>()

    /** Police variable : une graisse par cran, réglée par l'axe « wght ». */
    private fun variable(id: Int) = FontFamily(listOf(300, 400, 500, 600, 700).map { w ->
        Font(id, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    })

    fun famille(i: Int): FontFamily? = synchronized(cache) {
        cache.getOrPut(i) {
            when (i) {
                1 -> variable(R.font.inter)
                2 -> variable(R.font.nunito)
                3 -> variable(R.font.manrope)
                4 -> variable(R.font.lora)
                5 -> FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal), Font(R.font.atkinson_bold, FontWeight.Bold))
                else -> null
            }
        }
    }

    /** Typographie Material 3 avec la police choisie sur tous les styles. */
    fun typographie(i: Int): Typography {
        val t = Typography()
        val f = famille(i) ?: return t
        return Typography(
            displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f), displaySmall = t.displaySmall.copy(fontFamily = f),
            headlineLarge = t.headlineLarge.copy(fontFamily = f), headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
            titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f), titleSmall = t.titleSmall.copy(fontFamily = f),
            bodyLarge = t.bodyLarge.copy(fontFamily = f), bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
            labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f), labelSmall = t.labelSmall.copy(fontFamily = f),
        )
    }
}
