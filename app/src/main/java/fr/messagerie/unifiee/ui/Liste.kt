package fr.messagerie.unifiee.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
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
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.EtatSource
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.launch

@Composable
fun EcranListe(e: Etat, vm: ListeVM, ouvrir: (String) -> Unit, ouvrirReglages: () -> Unit, nouveau: () -> Unit) {
    val vue by vm.vue.collectAsState()
    val genre by vm.genre.collectAsState()
    val espace by vm.espace.collectAsState()
    val source by vm.source.collectAsState()
    val recherche by vm.recherche.collectAsState()
    val visibles by vm.visibles.collectAsState()
    var actionsPour by remember { mutableStateOf<Conversation?>(null) }
    val tiroir = rememberDrawerState(DrawerValue.Closed)
    val portee = rememberCoroutineScope()
    val sources = Source.entries.filter { s -> e.statistiques[s] != null || e.sources[s] is EtatSource.Ok || e.sources[s] is EtatSource.Erreur }

    ModalNavigationDrawer(
        drawerState = tiroir,
        drawerContent = {
            Tiroir(e, sources, fermer = { portee.launch { tiroir.close() } }, ouvrirReglages = ouvrirReglages,
                choisirSource = { vm.source.value = it; vm.vue.value = Vue.Toutes })
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = nouveau, icon = { Icon(Icons.Outlined.Edit, null) }, text = { Text("Nouveau") },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            bottomBar = { if (sources.size > 1) BarreSources(e, sources, source, espace) { vm.source.value = it } },
        ) { pad ->
            Column(Modifier.padding(bottom = pad.calculateBottomPadding()).fillMaxSize()) {
                Entete(e, vue, genre, espace, source, visibles.size, recherche,
                    choisirGenre = { vm.genre.value = if (vm.genre.value == it) Genre.Tous else it },
                    choisirEspace = { vm.espace.value = it },
                    ouvrirTiroir = { portee.launch { tiroir.open() } }, ouvrirReglages = ouvrirReglages,
                    chercher = { vm.recherche.value = it }, choisirVue = { vm.vue.value = it })

                if (visibles.isEmpty()) {
                    val q = recherche.trim()
                    EtatVide(
                        when {
                            e.conversations.isEmpty() && e.enCours -> Triple(Icons.Outlined.Refresh, "Lecture des messages…", "")
                            q.isNotEmpty() -> Triple(Icons.Outlined.Search, "Aucun résultat", "Rien ne correspond à « $q ».")
                            vue == Vue.ATraiter -> Triple(Icons.Outlined.DoneAll, "Tout est à jour", "Personne n'attend de réponse de votre part.")
                            vue == Vue.NonLus -> Triple(Icons.Outlined.DoneAll, "Tout est lu", "")
                            vue == Vue.Archivees -> Triple(Icons.Outlined.Inbox, "Aucune conversation archivée", "Appui long sur une conversation pour l'archiver.")
                            else -> Triple(Icons.Outlined.Forum, "Aucune conversation", "")
                        },
                    )
                } else LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(when (fr.messagerie.unifiee.Affichage.design) { 1 -> 0.dp; 3 -> 12.dp; else -> 8.dp }),
                ) {
                    items(visibles, key = { it.cle }) { c ->
                        CarteConversation(e, c, Modifier.animateItem(),
                            ouvrir = { vm.derniereOuverte = c.cle; ouvrir(c.cle) }, actions = { actionsPour = c })
                    }
                }
            }
        }
    }

    actionsPour?.let { c -> FeuilleActions(e, c) { actionsPour = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Entete(
    e: Etat, vue: Vue, genre: Genre, espace: Espace, source: Source?, nombre: Int, recherche: String,
    choisirGenre: (Genre) -> Unit, choisirEspace: (Espace) -> Unit,
    ouvrirTiroir: () -> Unit, ouvrirReglages: () -> Unit, chercher: (String) -> Unit, choisirVue: (Vue) -> Unit,
) {
    Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ouvrirTiroir, modifier = Modifier.padding(start = 0.dp)) { Icon(Icons.Outlined.Menu, "Menu") }
            Spacer(Modifier.weight(1f))
            if (e.enCours) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else IconButton(onClick = { Moteur.actualiserMaintenant() }) {
                Icon(Icons.Outlined.Refresh, "Actualiser", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = ouvrirReglages) { Icon(Icons.Outlined.Settings, "Réglages", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text((if (source != null) source.libelle + " · " else "") + vue.libelle,
                fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    e.importEnCours != null -> e.importEnCours
                    e.enCours -> "Actualisation…"
                    vue == Vue.ATraiter -> if (e.jours == 1) "Dernières 24 h" else "${e.jours} derniers jours"
                    else -> "$nombre conversation" + if (nombre > 1) "s" else ""
                },
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        // Vues Perso / Pro
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Espace.entries.forEachIndexed { i, es ->
                SegmentedButton(
                    selected = espace == es, onClick = { choisirEspace(es) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, Espace.entries.size),
                    icon = {},
                    label = { Text(es.libelle, fontSize = 13.sp) },
                )
            }
        }
        // Recherche
        Row(
            Modifier.fillMaxWidth().height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (recherche.isEmpty()) Text("Rechercher dans tous les messages", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                BasicTextField(
                    value = recherche, onValueChange = chercher, singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (recherche.isNotEmpty()) IconButton(onClick = { chercher("") }) { Icon(Icons.Outlined.Close, "Effacer") }
        }
        // Pastilles de vue
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Vue.entries.forEach { v ->
                val n = when (v) {
                    Vue.ATraiter -> e.nombreATraiter(source, espace.pro)
                    Vue.NonLus -> e.nombreMessagesNonLus(source, espace.pro)
                    else -> 0
                }
                PastilleVue(v.libelle, n, v == vue) { choisirVue(v) }
            }
            Box(Modifier.padding(horizontal = 2.dp).size(1.dp, 26.dp).align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.outlineVariant))
            PastilleVue(Genre.Discussions.libelle, 0, genre == Genre.Discussions) { choisirGenre(Genre.Discussions) }
            PastilleVue(Genre.Groupes.libelle, 0, genre == Genre.Groupes) { choisirGenre(Genre.Groupes) }
        }
    }
}

@Composable
private fun PastilleVue(libelle: String, n: Int, choisie: Boolean, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(38.dp)
            .background(if (choisie) cs.onSurface else cs.surfaceContainerLow, RoundedCornerShape(19.dp))
            .then(if (choisie) Modifier else Modifier.border(1.dp, cs.outline.copy(alpha = 0.35f), RoundedCornerShape(19.dp)))
            .clickable(onClick = clic).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, fontSize = 14.sp, fontWeight = if (choisie) FontWeight.SemiBold else FontWeight.Medium,
            color = if (choisie) cs.surface else cs.onSurface)
        if (n > 0) {
            Spacer(Modifier.width(8.dp))
            Text(compte(n), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (choisie) cs.onSurface else cs.onPrimary,
                modifier = Modifier.background(if (choisie) cs.surface else cs.primary, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 1.dp))
        }
    }
}

/** Barre du bas : une entrée par messagerie, plus « Tout ». */
@Composable
private fun BarreSources(e: Etat, sources: List<Source>, choisie: Source?, espace: Espace, choisir: (Source?) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround) {
                OngletSource("Tout", null, choisie == null, e.nombreMessagesNonLus(null, espace.pro), erreur = false) { choisir(null) }
                sources.forEach { s ->
                    OngletSource(s.id, s, choisie == s, e.nombreMessagesNonLus(s, espace.pro), erreur = e.sources[s] is EtatSource.Erreur) { choisir(s) }
                }
            }
        }
    }
}

@Composable
private fun OngletSource(nom: String, source: Source?, choisi: Boolean, n: Int, erreur: Boolean, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 52.dp)
            .background(if (choisi) cs.primaryContainer else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = clic).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            if (source != null) LogoSource(source, 22.dp)
            else Box(Modifier.size(22.dp).background(cs.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Inbox, null, tint = cs.surface, modifier = Modifier.size(13.dp))
            }
            if (erreur) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(cs.error, CircleShape))
        }
        Text(nom + if (n > 0) " · ${compte(n)}" else "", fontSize = 11.sp, maxLines = 1,
            fontWeight = if (choisi) FontWeight.Bold else FontWeight.Medium,
            color = if (choisi) cs.onPrimaryContainer else cs.onSurfaceVariant)
    }
}

@Composable
private fun Tiroir(e: Etat, sources: List<Source>, fermer: () -> Unit, ouvrirReglages: () -> Unit, choisirSource: (Source) -> Unit) {
    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Messagerie", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(e.derniereActualisation?.let { "Actualisé à ${heure(it)}" } ?: "Toutes vos messageries",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Messageries", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
            sources.forEach { s ->
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
                            else -> e.statistiques[s]?.let { Text("${it.first}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    },
                    selected = false, onClick = { choisirSource(s); fermer() },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            ElementTiroir("Tout marquer comme lu", Icons.Outlined.DoneAll) { Moteur.toutMarquerLu(); fermer() }
            ElementTiroir("Réglages", Icons.Outlined.Settings) { fermer(); ouvrirReglages() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ElementTiroir(libelle: String, icone: ImageVector, clic: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(libelle) }, icon = { Icon(icone, null) }, selected = false, onClick = clic,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
fun PastilleSource(s: Source, taille: Dp = 24.dp) = LogoSource(s, taille)

private fun compte(n: Int) = if (n > 999) "999+" else "$n"

@Composable
private fun EtatVide(contenu: Triple<ImageVector, String, String>) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(contenu.first, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
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
private fun CarteConversation(e: Etat, c: Conversation, modifier: Modifier, ouvrir: () -> Unit, actions: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val aTraiter = e.aTraiter(c)
    val nonLue = e.estNonLue(c)
    val nb = e.nombreNonLus(c)
    val design = fr.messagerie.unifiee.Affichage.design
    val fond = when (design) { 1 -> Color.Transparent; 2 -> c.source.teinte().copy(alpha = 0.08f); else -> cs.surfaceContainerLow }
    val forme = RoundedCornerShape(if (design == 3) 22.dp else 16.dp)
    Column(modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth()
            .background(fond, forme)
            .then(if (design == 1) Modifier else Modifier.border(1.dp, if (design == 2) c.source.teinte().copy(alpha = 0.25f) else cs.outlineVariant, forme))
            .clip(forme)
            .combinedClickable(onClick = ouvrir, onLongClick = actions)
            .padding(horizontal = if (design == 1) 4.dp else 12.dp, vertical = when (design) { 1 -> 9.dp; 3 -> 16.dp; else -> 12.dp }),
        verticalAlignment = if (design == 1) Alignment.CenterVertically else Alignment.Top,
    ) {
        Avatar(c.nom, c.source, c.estGroupe, taille = when (design) { 1 -> 40.dp; 3 -> 56.dp; else -> 48.dp }, bordure = cs.surfaceContainerLow, cle = c.cle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.nom, fontSize = 15.sp, fontWeight = if (nonLue || aTraiter) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(e.libelleSource(c), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte(), maxLines = 1)
                if (e.etat(c).sourdine) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.NotificationsOff, "Sourdine", Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                }
                if (e.estEpinglee(c)) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.PushPin, "Épinglée", Modifier.size(14.dp), tint = cs.primary)
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(dateCourte(c.derniere), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = if (nb > 0) cs.primary else cs.onSurfaceVariant)
            }
            val s = e.synthese(c)
            val enCours = c.cle in e.resumesEnCours
            val apercu = when {
                s != null -> s.resume
                enCours -> "Résumé en cours…"
                else -> c.messages.lastOrNull()?.let { m -> (if (m.deMoi) "Vous : " else if (c.estGroupe) "${m.auteur} : " else "") + m.texteUtile } ?: ""
            }
            Row {
                if (s != null || enCours) Icon(Icons.Outlined.AutoAwesome, "Résumé IA", Modifier.padding(top = 2.dp, end = 4.dp).size(14.dp), tint = Color(0xFF6A4BD6))
                Text(apercu, fontSize = 14.sp, lineHeight = 19.sp, maxLines = when (design) { 1 -> 1; 3 -> 3; else -> 2 }, overflow = TextOverflow.Ellipsis,
                    color = if (nonLue) cs.onSurface else cs.onSurfaceVariant)
            }
            if (aTraiter || nb > 0) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (aTraiter) Text("Réponse attendue", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onTertiaryContainer,
                    modifier = Modifier.background(cs.tertiaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                Spacer(Modifier.weight(1f))
                if (nb > 0) Box(Modifier.defaultMinSize(minWidth = 22.dp).height(22.dp).background(cs.primary, RoundedCornerShape(11.dp)).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center) {
                    Text(compte(nb), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
                }
            }
        }
    }
    if (design == 1) HorizontalDivider(Modifier.padding(start = 56.dp), color = cs.outlineVariant)
    }
}

/** Panneau d'actions (appui long ou ⋮). Toutes LOCALES sauf « Résumé IA » (Ollama sur votre serveur). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleActions(e: Etat, c: Conversation, fermer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.nom, c.source, c.estGroupe, taille = 40.dp, bordure = cs.surfaceContainerLow, cle = c.cle)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(c.nom, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.libelleSource(c) + if (c.estGroupe) " · groupe" else "", fontSize = 12.sp, color = c.source.teinte())
                }
            }
            Spacer(Modifier.height(12.dp))
            // Trois actions rapides
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Moteur.cfg.ollamaConfigure) TuileAction("Résumé IA", Icons.Outlined.AutoAwesome, Color(0xFF6A4BD6), Modifier.weight(1f)) { Moteur.regenerer(c); fermer() }
                if (e.estNonLue(c)) TuileAction("Lu", Icons.Outlined.MarkEmailRead, cs.primary, Modifier.weight(1f)) { Moteur.marquerLue(c); fermer() }
                else TuileAction("Non lu", Icons.Outlined.MarkEmailUnread, cs.primary, Modifier.weight(1f)) { Moteur.marquerNonLue(c); fermer() }
                TuileAction("Traitée", Icons.Outlined.TaskAlt, Color(0xFF1E7A47), Modifier.weight(1f)) { Moteur.marquerTraitee(c); fermer() }
            }
            Spacer(Modifier.height(8.dp))
            val rapides = setOf("Marquer comme lu", "Marquer comme non lu", "Marquer comme traitée", "Refaire le résumé IA")
            actionsConversation(e, c).filter { it.libelle !in rapides }.forEach { a ->
                Row(Modifier.fillMaxWidth().height(52.dp).clickable { a.faire(); fermer() }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(a.icone, null, tint = cs.onSurface)
                    Spacer(Modifier.width(14.dp))
                    Text(a.libelle, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun TuileAction(libelle: String, icone: ImageVector, teinte: Color, modifier: Modifier, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.height(76.dp)
            .background(cs.background, RoundedCornerShape(16.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = clic),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icone, null, tint = teinte)
        Spacer(Modifier.height(6.dp))
        Text(libelle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
