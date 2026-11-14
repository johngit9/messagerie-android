package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Source
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val degradeIA = Brush.linearGradient(listOf(Color(0xFFFA7340), Color(0xFFD94DBF), Color(0xFF5973FF)))
val orangeAttente = Color(0xFFE8833A)

fun Source.teinte() = Color(couleur)

sealed class Filtre {
    data object ATraiter : Filtre()
    data object NonLus : Filtre()
    data object Tout : Filtre()
    data class ParSource(val source: Source) : Filtre()
}

private fun memeJour(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** « 14:32 », « Hier 14:32 », « Lundi 14:32 », « 3 nov. 14:32 », « 3 nov. 2025 14:32 ». */
fun dateCourte(ms: Long): String {
    val d = Calendar.getInstance().apply { timeInMillis = ms }
    val maintenant = Calendar.getInstance()
    val hier = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val jours = (System.currentTimeMillis() - ms) / 86_400_000
    val motif = when {
        memeJour(d, maintenant) -> "HH:mm"
        memeJour(d, hier) -> "'Hier' HH:mm"
        jours < 7 -> "EEEE HH:mm"
        d.get(Calendar.YEAR) == maintenant.get(Calendar.YEAR) -> "d MMM HH:mm"
        else -> "d MMM yyyy HH:mm"
    }
    return SimpleDateFormat(motif, Locale.FRANCE).format(Date(ms)).replaceFirstChar { it.uppercase() }
}

fun jourComplet(ms: Long): String {
    val d = Calendar.getInstance().apply { timeInMillis = ms }
    val hier = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        memeJour(d, Calendar.getInstance()) -> "Aujourd'hui"
        memeJour(d, hier) -> "Hier"
        else -> SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date(ms)).replaceFirstChar { it.uppercase() }
    }
}

fun heure(ms: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))

@Composable
fun Avatar(nom: String, source: Source, groupe: Boolean, taille: Dp = 44.dp) {
    val initiales = nom.split(' ', '-').filter { it.firstOrNull()?.isLetter() == true }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "#" }
    val teinte = (nom.fold(0) { a, c -> (a * 31 + c.code) and 0xFFFF } % 360).toFloat()
    Box(Modifier.size(taille)) {
        Box(
            Modifier.size(taille).background(
                Brush.verticalGradient(listOf(Color.hsv(teinte, 0.45f, 0.85f), Color.hsv(teinte, 0.65f, 0.62f))), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (groupe) "👥" else initiales, color = Color.White, fontWeight = FontWeight.SemiBold,
                fontSize = (taille.value * 0.36f).sp)
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(taille * 0.38f)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .background(source.teinte(), CircleShape),
        )
    }
}

@Composable
fun Pastille(n: Int) {
    if (n <= 0) return
    Text(if (n > 999) "999+" else "$n", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
}

@Composable
fun Etiquette(texte: String, couleur: Color, plein: Boolean = false) {
    Text(texte, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        color = if (plein) Color.White else couleur,
        modifier = Modifier.background(if (plein) couleur else couleur.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp))
}
