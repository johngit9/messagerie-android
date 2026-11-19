package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sous-menu ouvert dans la page de réglages en cours (null = la page elle-même). */
internal class NavSous(val actuel: String?, val ouvrir: (String) -> Unit)

internal val LocalSousMenu = compositionLocalOf { NavSous(null) {} }

/** Une entrée de sous-menu : son titre sert aussi d'identifiant et de titre de la page ouverte. */
internal class ElementSous(
    val titre: String,
    val resume: String,
    val icone: ImageVector? = null,
    val couleur: Long = 0xFF3B5BDB,
    val logo: (@Composable () -> Unit)? = null,
    val pastille: Color? = null,
)

/** Carte listant des sous-menus, chacun ouvrant sa propre page. */
@Composable
internal fun ListeSousMenus(titre: String?, elements: List<ElementSous>) {
    if (elements.isEmpty()) return
    val nav = LocalSousMenu.current
    if (titre != null) Text(titre, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            elements.forEachIndexed { i, el ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().clickable { nav.ouvrir(el.titre) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (el.logo != null) Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { el.logo.invoke() }
                    else Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(el.couleur)), contentAlignment = Alignment.Center) {
                        el.icone?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(21.dp)) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(el.titre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (el.resume.isNotEmpty()) Text(el.resume, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    el.pastille?.let { Box(Modifier.padding(end = 6.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(it)) }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Bloc de réglages dans une carte, avec un titre facultatif. */
@Composable
internal fun Section(titre: String? = null, aide: String? = null, contenu: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (titre != null) Text(titre, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (aide != null) Explication(aide)
            contenu()
        }
    }
}
