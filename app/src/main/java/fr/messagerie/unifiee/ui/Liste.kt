package fr.messagerie.unifiee.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.EtatSource
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranListe(
    e: Etat,
    filtre: Filtre,
    onFiltre: (Filtre) -> Unit,
    ouvrir: (String) -> Unit,
    ouvrirReglages: () -> Unit,
    selection: String?,
) {
    var genre by rememberSaveable { mutableStateOf(0) }       // 0 tout, 1 discussions, 2 groupes
    var recherche by rememberSaveable { mutableStateOf("") }
    var trouvees by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(recherche) {
        val q = recherche.trim()
        delay(200)
        trouvees = if (q.isEmpty()) emptySet() else withContext(Dispatchers.IO) { Moteur.archive.rechercher(q) }
    }

    val duFiltre = when (filtre) {
        Filtre.ATraiter -> e.conversations.filter { e.aTraiter(it) }
        Filtre.NonLus -> e.conversations.filter { e.estNonLue(it) || it.cle == selection }
        Filtre.Tout -> e.conversations
        is Filtre.ParSource -> e.conversations.filter { it.source == filtre.source }
    }
    var visibles = when (genre) { 1 -> duFiltre.filter { !it.estGroupe }; 2 -> duFiltre.filter { it.estGroupe }; else -> duFiltre }
    val q = recherche.trim()
    if (q.isNotEmpty()) visibles = visibles.filter { c ->
        c.nom.contains(q, true) || c.cle in trouvees || e.synthese(c)?.resume?.contains(q, true) == true
    }
    val titre = when (filtre) { Filtre.ATraiter -> "À traiter"; Filtre.NonLus -> "Non lus"; Filtre.Tout -> "Toutes"; is Filtre.ParSource -> filtre.source.libelle }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(titre)
                    Text(
                        when {
                            e.importEnCours != null -> e.importEnCours
                            e.enCours -> "Actualisation…"
                            filtre == Filtre.ATraiter -> if (e.jours == 1) "Dernières 24 h" else "${e.jours} derniers jours"
                            else -> "${visibles.size} conversations"
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                if (e.enCours) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { Moteur.actualiserMaintenant() }) { Text("⟳", fontSize = 20.sp) }
                TextButton(onClick = ouvrirReglages) { Text("⚙", fontSize = 20.sp) }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Filtres
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                item { ChipFiltre("✨ À traiter", e.nombreATraiter, filtre == Filtre.ATraiter) { onFiltre(Filtre.ATraiter) } }
                item { ChipFiltre("Non lus", e.nombreMessagesNonLus(null), filtre == Filtre.NonLus, menu = "Tout marquer comme lu" to { Moteur.toutMarquerLu() }) { onFiltre(Filtre.NonLus) } }
                item { ChipFiltre("Toutes", 0, filtre == Filtre.Tout) { onFiltre(Filtre.Tout) } }
                items(Source.entries.filter { s -> e.sources[s] !is EtatSource.NonConfigure || e.statistiques[s] != null }) { s ->
                    val st = e.sources[s]
                    val erreur = st is EtatSource.Erreur
                    ChipFiltre((if (erreur) "⚠️ " else "") + s.libelle, e.nombreMessagesNonLus(s), filtre == Filtre.ParSource(s), point = s.teinte(),
                        detail = (st as? EtatSource.Erreur)?.message) { onFiltre(Filtre.ParSource(s)) }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val nbGroupes = duFiltre.count { it.estGroupe }
                listOf("Tout", "Discussions (${duFiltre.size - nbGroupes})", "Groupes ($nbGroupes)").forEachIndexed { i, l ->
                    FilterChip(selected = genre == i, onClick = { genre = i }, label = { Text(l, fontSize = 12.sp) })
                }
            }
            OutlinedTextField(
                value = recherche, onValueChange = { recherche = it }, singleLine = true,
                placeholder = { Text("Rechercher (noms, messages, résumés)") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            if (visibles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            e.conversations.isEmpty() && e.enCours -> "Lecture des messages…"
                            q.isNotEmpty() -> "Aucun résultat pour « $q »"
                            filtre == Filtre.ATraiter -> "✅ Tout est à jour\nPersonne n'attend de réponse de votre part."
                            filtre == Filtre.NonLus -> "✅ Tout est lu"
                            else -> "Aucune conversation"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(visibles, key = { it.cle }) { c ->
                    LigneConversation(e, c) { ouvrir(c.cle) }
                    HorizontalDivider(Modifier.padding(start = 76.dp), thickness = 0.5.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipFiltre(
    libelle: String, n: Int, choisi: Boolean, point: Color? = null, detail: String? = null,
    menu: Pair<String, () -> Unit>? = null, clic: () -> Unit,
) {
    var ouvert by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = choisi,
            onClick = { if (choisi && (menu != null || detail != null)) ouvert = true else clic() },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (point != null) { Box(Modifier.size(8.dp).background(point, CircleShape)); Spacer(Modifier.width(6.dp)) }
                    Text(libelle)
                    if (n > 0) { Spacer(Modifier.width(6.dp)); Pastille(n) }
                }
            },
        )
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            if (detail != null) DropdownMenuItem(text = { Text(detail, fontSize = 13.sp) }, onClick = { ouvert = false })
            if (menu != null) DropdownMenuItem(text = { Text(menu.first) }, onClick = { menu.second(); ouvert = false })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneConversation(e: Etat, c: Conversation, ouvrir: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val aTraiter = e.aTraiter(c)
    val nonLue = e.estNonLue(c)
    val nb = e.nombreNonLus(c)
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = ouvrir, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 18.dp).size(8.dp).background(if (nonLue) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape))
            Spacer(Modifier.width(4.dp))
            Avatar(c.nom, c.source, c.estGroupe)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.nom, fontWeight = if (aTraiter || nonLue) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (e.etat(c).sourdine) Text(" 🔕", fontSize = 11.sp)
                    if (c.source == Source.SMS) {
                        Spacer(Modifier.width(4.dp))
                        val rcs = c.cle in e.rcs
                        Etiquette(if (rcs) "RCS" else "SMS", Source.SMS.teinte(), plein = rcs)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(dateCourte(c.derniere), fontSize = 12.sp,
                        color = if (aTraiter || nonLue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (nonLue) FontWeight.SemiBold else FontWeight.Normal)
                    if (nb > 0) { Spacer(Modifier.width(6.dp)); Pastille(nb) }
                }
                val s = e.synthese(c)
                val apercu = when {
                    s != null -> s.resume
                    c.cle in e.resumesEnCours -> "✨ Résumé en cours…"
                    else -> c.messages.lastOrNull()?.let { m -> (if (m.deMoi) "Vous : " else if (c.estGroupe) "${m.auteur} : " else "") + m.texteUtile } ?: ""
                }
                Text(apercu, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (aTraiter) {
                    Text("Réponse attendue", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = orangeAttente,
                        modifier = Modifier.padding(top = 3.dp).background(orangeAttente.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuConversation(e, c) { menu = false }
        }
    }
}

/** Actions communes (liste et conversation). Toutes LOCALES sauf « Refaire le résumé » (Ollama). */
@Composable
fun MenuConversation(e: Etat, c: Conversation, fermer: () -> Unit) {
    if (e.estNonLue(c)) DropdownMenuItem(text = { Text("Marquer comme lu") }, onClick = { Moteur.marquerLue(c); fermer() })
    else DropdownMenuItem(text = { Text("Marquer comme non lu") }, onClick = { Moteur.marquerNonLue(c); fermer() })
    DropdownMenuItem(text = { Text(if (e.etat(c).manuel) "Retirer l'indicateur « À traiter »" else "Marquer « À traiter »") },
        onClick = { Moteur.basculerATraiter(c); fermer() })
    DropdownMenuItem(text = { Text(if (e.etat(c).sourdine) "Réactiver les notifications" else "Mettre en sourdine") },
        onClick = { Moteur.basculerSourdine(c); fermer() })
    DropdownMenuItem(text = { Text("Marquer comme traitée") }, onClick = { Moteur.marquerTraitee(c); fermer() })
    if (Moteur.cfg.ollamaConfigure) DropdownMenuItem(text = { Text("Refaire le résumé") }, onClick = { Moteur.regenerer(c); fermer() })
}
