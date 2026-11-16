package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Moteur
import kotlinx.coroutines.launch

/**
 * Réponses rapides : vos modèles (modifiables ici) et des brouillons proposés par l'IA.
 * Un appui met le texte dans le champ de saisie ; rien ne part sans « Envoyer ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleReponsesRapides(c: Conversation, fermer: () -> Unit, choisir: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val portee = rememberCoroutineScope()
    var modeles by remember { mutableStateOf(Moteur.cfg.reponsesRapides) }
    var edition by remember { mutableStateOf(false) }
    var nouveau by remember { mutableStateOf("") }
    var propositions by remember { mutableStateOf<List<String>?>(null) }
    var iaEnCours by remember { mutableStateOf(false) }
    var iaErreur by remember { mutableStateOf(false) }
    fun enregistrer(l: List<String>) { modeles = l; Moteur.cfg.reponsesRapides = l }

    ModalBottomSheet(onDismissRequest = fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Réponses rapides", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Un appui met le texte dans le champ : vous pouvez le modifier, il ne part que sur « Envoyer ».",
                fontSize = 13.sp, color = cs.onSurfaceVariant)

            if (Moteur.cfg.ollamaConfigure) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Proposées par l'IA", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (iaEnCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                val p = propositions
                if (p == null) OutlinedButton(onClick = {
                    iaEnCours = true; iaErreur = false
                    portee.launch { val r = Moteur.brouillonsIA(c); iaEnCours = false; if (r.isNullOrEmpty()) iaErreur = true else propositions = r }
                }, enabled = !iaEnCours) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (iaEnCours) "L'IA réfléchit…" else "Proposer des réponses")
                }
                else p.forEach { t -> Bulle(t, ia = true) { choisir(t); fermer() } }
                if (iaErreur) Text("L'IA n'a pas répondu.", fontSize = 13.sp, color = cs.error)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mes modèles", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { edition = !edition }) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (edition) "Terminé" else "Modifier")
                }
            }
            if (modeles.isEmpty() && !edition) Text("Aucun modèle. Appuyez sur « Modifier » pour en ajouter.", fontSize = 13.sp, color = cs.onSurfaceVariant)
            modeles.forEachIndexed { i, t ->
                if (edition) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { enregistrer(modeles.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.Delete, "Supprimer ce modèle", tint = cs.error) }
                } else Bulle(t, ia = false) { choisir(t); fermer() }
            }
            if (edition) Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(nouveau, { nouveau = it }, placeholder = { Text("Nouveau modèle") }, modifier = Modifier.weight(1f), maxLines = 4)
                IconButton(onClick = { if (nouveau.isNotBlank()) { enregistrer(modeles + nouveau.trim()); nouveau = "" } }, enabled = nouveau.isNotBlank()) {
                    Icon(Icons.Outlined.Add, "Ajouter le modèle")
                }
            }
        }
    }
}

@Composable
private fun Bulle(texte: String, ia: Boolean, choisir: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(texte, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(if (ia) cs.primaryContainer.copy(alpha = 0.5f) else cs.surfaceContainerLow, RoundedCornerShape(14.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp)).clickable(onClick = choisir).padding(horizontal = 14.dp, vertical = 10.dp))
}
