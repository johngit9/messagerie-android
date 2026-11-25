package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Relais
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun sourceDe(reseau: String): Source? = when (reseau) {
    "element" -> Source.ELEMENT; "whatsapp" -> Source.WHATSAPP; "telegram" -> Source.TELEGRAM
    "gmessages" -> Source.SMS; "linkedin" -> Source.LINKEDIN; "signal" -> Source.SIGNAL; else -> null
}

/** Mon profil : votre nom affiché sur chaque messagerie ; modifiable là où le serveur le permet (champ « modifiable » de chaque réseau). */
@Composable
internal fun PageProfil() {
    val cfg = Moteur.cfg
    val cs = MaterialTheme.colorScheme
    val portee = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var donnees by remember { mutableStateOf<Pair<List<Relais.Profil>, Int>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var aModifier by remember { mutableStateOf<Relais.Profil?>(null) }
    var retour by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version) {
        erreur = null
        if (!cfg.relaisConfigure) { erreur = "Renseignez d'abord le relais (Réglages > Serveur et relais)."; return@LaunchedEffect }
        runCatching { withContext(Dispatchers.IO) { Relais(cfg).profils() } }
            .onSuccess { donnees = it }
            .onFailure { erreur = if (it is fr.messagerie.unifiee.ErreurHttp && it.code == 404) "Le relais du serveur ne gère pas encore les profils." else "Lecture impossible : ${it.message}" }
    }

    Explication("Le nom que vos contacts voient sur chaque messagerie. Il ne change que lorsque vous touchez « Enregistrer ».")
    erreur?.let { Text(it, color = cs.error, fontSize = 14.sp) }
    retour?.let { Text(it, color = cs.primary, fontSize = 14.sp) }
    val (profils, _) = donnees ?: run { if (erreur == null) CircularProgressIndicator(Modifier.size(24.dp)); return }
    profils.sortedBy { sourceDe(it.reseau)?.ordinal ?: 99 }.forEach { p ->
        val s = sourceDe(p.reseau)
        Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s != null) LogoSource(s, 40.dp) else Box(Modifier.size(40.dp).background(cs.outline, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s?.libelle ?: p.reseau, fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(7.dp).background(if (p.connecte) Color(0xFF2F9E44) else cs.outline, CircleShape))
                    }
                    Text(p.nom.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    if (p.identifiant.isNotBlank()) Text(p.identifiant, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    if (!p.modifiable) Text(p.raison.ifBlank { if (!p.connecte) "Non connecté" else "Modifiable seulement dans l'appli officielle" },
                        fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                if (p.modifiable) TextButton(onClick = { aModifier = p }) { Text("Modifier") }
            }
        }
    }

    aModifier?.let { p ->
        var nom by remember(p) { mutableStateOf(p.nom) }
        var enCours by remember(p) { mutableStateOf(false) }
        var echec by remember(p) { mutableStateOf<String?>(null) }
        val libelle = sourceDe(p.reseau)?.libelle ?: p.reseau
        AlertDialog(
            onDismissRequest = { if (!enCours) aModifier = null },
            title = { Text("Mon nom sur $libelle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = nom, onValueChange = { if (it.length <= p.max || it.length < nom.length) nom = it }, singleLine = true,
                        label = { Text("Nom affiché") }, supportingText = { Text("${nom.length} / ${p.max}") }, modifier = Modifier.fillMaxWidth())
                    Text("Vos contacts $libelle verront ce nouveau nom.", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    echec?.let { Text(it, color = cs.error, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = !enCours && nom.trim().isNotEmpty() && nom.trim() != p.nom && nom.trim().length <= p.max, onClick = {
                    enCours = true; echec = null
                    portee.launch {
                        runCatching { withContext(Dispatchers.IO) { Relais(cfg).changerNomProfil(p.reseau, nom.trim()) } }
                            .onSuccess { aModifier = null; retour = "Nom $libelle changé."; version++ }
                            .onFailure { echec = it.message ?: "Échec"; enCours = false }
                    }
                }) { Text(if (enCours) "Enregistrement…" else "Enregistrer") }
            },
            dismissButton = { TextButton(enabled = !enCours, onClick = { aModifier = null }) { Text("Annuler") } },
        )
    }
}
