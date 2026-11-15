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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
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
    var actionsPour by remember { mutableStateOf<Conversation?>(null) }
    val tiroir = rememberDrawerState(DrawerValue.Closed)
    val portee = rememberCoroutineScope()

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
    val titre = when (filtre) { Filtre.ATraiter -> "À traiter"; Filtre.NonLus -> "Non lus"; Filtre.Tout -> "Toutes les conversations"; is Filtre.ParSource -> filtre.source.libelle }

    fun choisir(f: Filtre) { onFiltre(f); portee.launch { tiroir.close() } }

    ModalNavigationDrawer(
        drawerState = tiroir,
        drawerContent = {
            ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    Row(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(degradeIA, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Messagerie", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                e.derniereActualisation?.let { "Actualisé à ${heure(it)}" } ?: "Toutes vos messageries",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ElementTiroir("À traiter", Icons.Outlined.AutoAwesome, filtre == Filtre.ATraiter, e.nombreATraiter) { choisir(Filtre.ATraiter) }
                    ElementTiroir("Non lus", Icons.Outlined.MarkEmailUnread, filtre == Filtre.NonLus, e.nombreMessagesNonLus(null)) { choisir(Filtre.NonLus) }
                    ElementTiroir("Toutes", Icons.Outlined.Inbox, filtre == Filtre.Tout, 0) { choisir(Filtre.Tout) }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    Text("Messageries", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                    Source.entries.filter { s -> e.sources[s] !is EtatSource.NonConfigure || e.statistiques[s] != null }.forEach { s ->
                        val st = e.sources[s]
                        NavigationDrawerItem(
                            label = {
                                Column {
                                    Text(s.libelle)
                                    if (st is EtatSource.Erreur) Text(st.message, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            icon = { PastilleSource(s) },
                            badge = {
                                when {
                                    st is EtatSource.Erreur -> Icon(Icons.Outlined.ErrorOutline, "Erreur", tint = MaterialTheme.colorScheme.error)
                                    st is EtatSource.Inactif -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else -> e.nombreMessagesNonLus(s).takeIf { it > 0 }?.let { Text(compte(it)) }
                                }
                            },
                            selected = filtre == Filtre.ParSource(s),
                            onClick = { choisir(Filtre.ParSource(s)) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    ElementTiroir("Tout marquer comme lu", Icons.Outlined.DoneAll, false, 0) { Moteur.toutMarquerLu(); portee.launch { tiroir.close() } }
                    ElementTiroir("Réglages", Icons.Outlined.Settings, false, 0) { portee.launch { tiroir.close() }; ouvrirReglages() }
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { portee.launch { tiroir.open() } }) { Icon(Icons.Outlined.Menu, "Menu") }
                    },
                    title = {
                        Column {
                            Text(titre, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    e.importEnCours != null -> e.importEnCours
                                    e.enCours -> "Actualisation…"
                                    filtre == Filtre.ATraiter -> if (e.jours == 1) "Dernières 24 h" else "${e.jours} derniers jours"
                                    else -> "${visibles.size} conversation" + if (visibles.size > 1) "s" else ""
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        if (e.enCours) CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { Moteur.actualiserMaintenant() }) { Icon(Icons.Outlined.Refresh, "Actualiser") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                // Recherche
                TextField(
                    value = recherche, onValueChange = { recherche = it }, singleLine = true,
                    placeholder = { Text("Rechercher dans les messages") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (recherche.isNotEmpty()) IconButton(onClick = { recherche = "" }) { Icon(Icons.Outlined.Close, "Effacer") }
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // Discussions / Groupes
                val nbGroupes = duFiltre.count { it.estGroupe }
                val genres = listOf("Tout", "Discussions", "Groupes")
                val nombres = listOf(duFiltre.size, duFiltre.size - nbGroupes, nbGroupes)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    genres.forEachIndexed { i, l ->
                        SegmentedButton(
                            selected = genre == i, onClick = { genre = i },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = genres.size),
                            label = { Text(if (i == 0) l else "$l · ${nombres[i]}", fontSize = 13.sp, maxLines = 1) },
                        )
                    }
                }

                if (visibles.isEmpty()) {
                    EtatVide(
                        when {
                            e.conversations.isEmpty() && e.enCours -> Triple(Icons.Outlined.Refresh, "Lecture des messages…", "")
                            q.isNotEmpty() -> Triple(Icons.Outlined.Search, "Aucun résultat", "Rien ne correspond à « $q ».")
                            filtre == Filtre.ATraiter -> Triple(Icons.Outlined.DoneAll, "Tout est à jour", "Personne n'attend de réponse de votre part.")
                            filtre == Filtre.NonLus -> Triple(Icons.Outlined.DoneAll, "Tout est lu", "")
                            else -> Triple(Icons.Outlined.Forum, "Aucune conversation", "")
                        },
                    )
                } else LazyColumn(Modifier.fillMaxSize()) {
                    items(visibles, key = { it.cle }) { c ->
                        LigneConversation(e, c, ouvrir = { ouvrir(c.cle) }, actions = { actionsPour = c })
                    }
                }
            }
        }
    }

    actionsPour?.let { c ->
        FeuilleActions(e, c) { actionsPour = null }
    }
}

@Composable
private fun ElementTiroir(libelle: String, icone: ImageVector, choisi: Boolean, n: Int, clic: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(libelle) },
        icon = { Icon(icone, null) },
        badge = { if (n > 0) Text(compte(n), fontWeight = FontWeight.SemiBold) },
        selected = choisi, onClick = clic,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
fun PastilleSource(s: Source, taille: androidx.compose.ui.unit.Dp = 24.dp) {
    Box(Modifier.size(taille).background(s.teinte(), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        Text(s.libelle.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (taille.value * 0.5f).sp)
    }
}

private fun compte(n: Int) = if (n > 999) "999+" else "$n"

@Composable
private fun EtatVide(contenu: Triple<ImageVector, String, String>) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(contenu.first, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(contenu.second, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (contenu.third.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(contenu.third, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneConversation(e: Etat, c: Conversation, ouvrir: () -> Unit, actions: () -> Unit) {
    val aTraiter = e.aTraiter(c)
    val nonLue = e.estNonLue(c)
    val nb = e.nombreNonLus(c)
    val fort = aTraiter || nonLue
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = ouvrir, onLongClick = actions)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(c.nom, c.source, c.estGroupe, taille = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.nom, style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (fort) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (e.etat(c).sourdine) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.NotificationsOff, "Sourdine", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (c.source == Source.SMS) {
                    Spacer(Modifier.width(6.dp))
                    val rcs = c.cle in e.rcs
                    Etiquette(if (rcs) "RCS" else "SMS", Source.SMS.teinte(), plein = rcs)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(dateCourte(c.derniere), style = MaterialTheme.typography.labelMedium,
                    color = if (fort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (nonLue) FontWeight.SemiBold else FontWeight.Normal)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Top) {
                val s = e.synthese(c)
                val enCours = c.cle in e.resumesEnCours
                val apercu = when {
                    s != null -> s.resume
                    enCours -> "Résumé en cours…"
                    else -> c.messages.lastOrNull()?.let { m -> (if (m.deMoi) "Vous : " else if (c.estGroupe) "${m.auteur} : " else "") + m.texteUtile } ?: ""
                }
                if (s != null || enCours) {
                    Icon(Icons.Outlined.AutoAwesome, "Résumé IA", Modifier.padding(top = 2.dp, end = 4.dp).size(14.dp), tint = Color(0xFFD94DBF))
                }
                Text(apercu, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    color = if (nonLue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                if (nb > 0) { Spacer(Modifier.width(8.dp)); Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(compte(nb)) } }
            }
            if (aTraiter) {
                Spacer(Modifier.height(6.dp))
                Text("Réponse attendue", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = orangeAttente,
                    modifier = Modifier.background(orangeAttente.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

/** Feuille d'actions (appui long sur une conversation). Toutes LOCALES sauf « Refaire le résumé » (Ollama). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleActions(e: Etat, c: Conversation, fermer: () -> Unit) {
    ModalBottomSheet(onDismissRequest = fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.nom, c.source, c.estGroupe, taille = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(c.nom, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.libelleSource(c) + if (c.estGroupe) " · groupe" else "", style = MaterialTheme.typography.bodySmall, color = c.source.teinte())
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        actionsConversation(e, c).forEach { a ->
            ElementAction(a.icone, a.libelle) { a.faire(); fermer() }
        }
        Spacer(Modifier.height(24.dp))
    }
}
