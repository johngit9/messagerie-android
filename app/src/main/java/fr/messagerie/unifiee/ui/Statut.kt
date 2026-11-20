package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.launch

/*
 * Statut montré aux autres (« en ligne », « absent », « hors ligne »).
 * Deux statuts réellement distincts existent : le compte Matrix du serveur (repris par les passerelles
 * WhatsApp et Telegram, qui partagent donc le même statut) et Element (par le relais).
 * Signal, SMS, LinkedIn et Teams ne montrent aucun statut.
 */

private val CHOIX = listOf("offline" to "Hors ligne", "unavailable" to "Absent", "online" to "En ligne")

fun couleurStatut(v: String?): Color = when (v) {
    "online" -> Color(0xFF22A35A)
    "unavailable" -> Color(0xFFE0922F)
    else -> Color(0xFF9AA0AB)
}

private fun rang(v: String) = when (v) { "online" -> 2; "unavailable" -> 1; else -> 0 }

/** Bouton rond du haut de l'accueil : silhouette + pastille du statut le plus visible. */
@Composable
fun BoutonStatut(teinte: Color, ouvrir: () -> Unit) {
    val (m, el) = Moteur.statut.collectAsState().value
    val visible = if (rang(m) >= rang(el)) m else el
    IconButton(onClick = ouvrir) {
        Box {
            Icon(Icons.Outlined.AccountCircle, "Mon statut : " + (CHOIX.firstOrNull { it.first == visible }?.second ?: ""), tint = teinte)
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(11.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)
                .background(couleurStatut(visible), CircleShape))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleStatut(fermer: () -> Unit) {
    ModalBottomSheet(onDismissRequest = fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Statut montré aux autres", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            ChoixStatuts()
        }
    }
}

/** Contenu commun à la feuille de l'accueil et aux Réglages > Confidentialité. */
@Composable
fun ChoixStatuts() {
    val cfg = Moteur.cfg
    val (m, el) = Moteur.statut.collectAsState().value
    val portee = rememberCoroutineScope()
    var retour by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val matrix = cfg.matrixConfigure
    val element = cfg.relaisConfigure

    fun appliquer(v: String, surMatrix: Boolean, surElement: Boolean) {
        if (!surMatrix && !surElement) return
        retour = "Application…"
        portee.launch {
            val (ok, ko) = Moteur.definirPresence(v, surMatrix, surElement)
            retour = (if (ok.isNotEmpty()) "Appliqué : " + ok.joinToString(", ") + "." else "") +
                (if (ko.isNotEmpty()) " Non joignable : " + ko.joinToString(", ") + "." else "")
        }
    }

    // Perso / Pro : chaque espace regroupe les statuts des messageries qui y sont rangées
    fun canaux(pro: Boolean) = (matrix && (cfg.pro(Source.WHATSAPP) == pro || cfg.pro(Source.TELEGRAM) == pro)) to
        (element && cfg.pro(Source.ELEMENT) == pro)
    Text("Rapide", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
    listOf(false to "Perso", true to "Pro").forEach { (pro, nom) ->
        val (surM, surE) = canaux(pro)
        if (!surM && !surE) return@forEach
        val valeurs = listOfNotNull(m.takeIf { surM }, el.takeIf { surE }).distinct()
        LigneStatut(nom, null, valeurs.singleOrNull()) { appliquer(it, surM, surE) }
    }

    Text("Par messagerie", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
    if (matrix) LigneStatut("WhatsApp · Telegram", listOf(Source.WHATSAPP, Source.TELEGRAM), m, lie = true) { appliquer(it, true, false) }
    if (element) LigneStatut("Element", listOf(Source.ELEMENT), el) { appliquer(it, false, true) }
    Text("WhatsApp et Telegram passent par le même compte de votre serveur : ils montrent le même statut. " +
        "Signal, SMS, LinkedIn et Teams n'affichent pas de statut. Hors ligne par défaut ; une messagerie peut ne pas reprendre le statut.",
        fontSize = 12.sp, lineHeight = 16.sp, color = cs.onSurfaceVariant)
    retour?.let { Text(it.trim(), fontSize = 12.sp, color = cs.primary) }
}

/** Une ligne : logos (ou nom d'espace) et trois pastilles Hors ligne / Absent / En ligne. */
@Composable
private fun LigneStatut(nom: String, sources: List<Source>?, valeur: String?, lie: Boolean = false, choisir: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow, RoundedCornerShape(16.dp))
        .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            sources?.forEachIndexed { i, s ->
                LogoSource(s, 24.dp, Modifier.then(if (i > 0) Modifier.offset(x = (-6 * i).dp) else Modifier))
            }
            if (sources != null) Spacer(Modifier.width(if (sources.size > 1) 4.dp else 10.dp))
            Text(nom, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (lie) Icon(Icons.Outlined.Link, "Statut commun", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CHOIX.forEach { (v, t) ->
                val choisi = v == valeur
                Row(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (choisi) couleurStatut(v).copy(alpha = 0.16f) else cs.surface)
                        .border(1.dp, if (choisi) couleurStatut(v) else cs.outlineVariant, RoundedCornerShape(18.dp))
                        .clickable { choisir(v) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(9.dp).background(couleurStatut(v), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(t, fontSize = 13.sp, fontWeight = if (choisi) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/** Petite puce « ● Hors ligne » pour le bandeau de l'accueil (prend la place d'un texte). */
@Composable
fun PuceStatut(ouvrir: () -> Unit) {
    val (m, el) = Moteur.statut.collectAsState().value
    val cfg = Moteur.cfg
    val valeurs = listOfNotNull(m.takeIf { cfg.matrixConfigure }, el.takeIf { cfg.relaisConfigure }).distinct()
    val visible = valeurs.maxByOrNull { rang(it) } ?: m
    Row(
        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = ouvrir).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(couleurStatut(visible), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (valeurs.size > 1) "Statuts variés" else CHOIX.first { it.first == visible }.second,
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}
