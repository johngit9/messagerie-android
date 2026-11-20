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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.offset
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
fun EcranListe(e: Etat, vm: ListeVM, ouvrir: (String) -> Unit, ouvrirReglages: () -> Unit, nouveau: () -> Unit, rechercher: (String) -> Unit = {}) {
    val vue by vm.vue.collectAsState()
    val genre by vm.genre.collectAsState()
    val espace by vm.espace.collectAsState()
    val source by vm.source.collectAsState()
    val recherche by vm.recherche.collectAsState()
    val visibles by vm.visibles.collectAsState()
    var actionsPour by remember { mutableStateOf<Conversation?>(null) }
    // Sélection de plusieurs conversations (appui long) pour des actions groupées
    var choisies by remember { mutableStateOf(setOf<String>()) }
    fun basculer(c: Conversation) { choisies = if (c.cle in choisies) choisies - c.cle else choisies + c.cle }
    androidx.activity.compose.BackHandler(enabled = choisies.isNotEmpty()) { choisies = emptySet() }
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
            containerColor = if (fr.messagerie.unifiee.Affichage.haut == 2) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
            floatingActionButton = {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = nouveau, icon = { Icon(Icons.Outlined.Edit, null) }, text = { Text("Nouveau") },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            },
            bottomBar = { if (sources.size > 1 && fr.messagerie.unifiee.Affichage.barreBas) BarreSources(e, sources, source, espace) { vm.source.value = it } },
        ) { pad ->
            Column(Modifier.padding(bottom = pad.calculateBottomPadding()).fillMaxSize()) {
                if (choisies.isNotEmpty()) BarreSelection(e, e.conversations.filter { it.cle in choisies },
                    toutChoisir = { choisies = visibles.map { it.cle }.toSet() },
                    plus = { cs -> actionsPour = cs }, fin = { choisies = emptySet() })
                else if (fr.messagerie.unifiee.Affichage.haut != 0) EnteteCompact(e, fr.messagerie.unifiee.Affichage.haut == 1, vue, genre, espace, source, visibles.size, recherche,
                    choisirGenre = { vm.genre.value = if (vm.genre.value == it) Genre.Tous else it },
                    choisirEspace = { vm.espace.value = it }, choisirSource = { vm.source.value = it },
                    ouvrirTiroir = { portee.launch { tiroir.open() } }, ouvrirReglages = ouvrirReglages,
                    chercher = { vm.recherche.value = it }, choisirVue = { vm.vue.value = it },
                    rechercheAvancee = { rechercher(vm.recherche.value) })
                else Entete(e, vue, genre, espace, source, visibles.size, recherche,
                    choisirGenre = { vm.genre.value = if (vm.genre.value == it) Genre.Tous else it },
                    choisirEspace = { vm.espace.value = it },
                    ouvrirTiroir = { portee.launch { tiroir.open() } }, ouvrirReglages = ouvrirReglages,
                    chercher = { vm.recherche.value = it }, choisirVue = { vm.vue.value = it },
                    rechercheAvancee = { rechercher(vm.recherche.value) })
                val pannes by fr.messagerie.unifiee.Surveillance.pannes.collectAsState()
                if (choisies.isEmpty() && pannes.isNotEmpty()) BandeauPannes(pannes, ouvrirReglages)

                if (visibles.isEmpty()) {
                    val q = recherche.trim()
                    EtatVide(
                        when {
                            e.conversations.isEmpty() && e.enCours -> Triple(Icons.Outlined.Refresh, "Lecture des messages…", "")
                            q.isNotEmpty() -> Triple(Icons.Outlined.Search, "Aucun résultat", "Rien ne correspond à « $q ».")
                            vue == Vue.ATraiter -> Triple(Icons.Outlined.DoneAll, "Tout est à jour", "Personne n'attend de réponse de votre part.")
                            vue == Vue.NonLus -> Triple(Icons.Outlined.DoneAll, "Tout est lu", "")
                            vue == Vue.Archivees -> Triple(Icons.Outlined.Inbox, "Aucune conversation archivée", "Appui long sur une conversation pour l'archiver.")
                            vue == Vue.Spam -> Triple(Icons.Outlined.DoneAll, "Aucun spam", "Les messages suspects d'expéditeurs inconnus arrivent ici, sans notification.")
                            else -> Triple(Icons.Outlined.Forum, "Aucune conversation", "")
                        },
                    )
                } else LazyColumn(
                    // La liste commence sous le bandeau, sans le chevaucher
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = if (fr.messagerie.unifiee.Affichage.haut == 1) 10.dp else 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(when (fr.messagerie.unifiee.Affichage.design) { 1 -> 0.dp; 3 -> 12.dp; else -> 8.dp }),
                ) {
                    items(visibles, key = { it.cle }) { c ->
                        if (fr.messagerie.unifiee.Affichage.design == 1 && fr.messagerie.unifiee.Affichage.haut == 2 && c.cle != visibles.firstOrNull()?.cle)
                            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        CarteConversation(e, c, Modifier.animateItem(),
                            ouvrir = { if (choisies.isNotEmpty()) basculer(c) else { vm.derniereOuverte = c.cle; ouvrir(c.cle) } },
                            actions = { basculer(c) }, choisie = c.cle in choisies)
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
    rechercheAvancee: () -> Unit = {},
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
            var feuilleStatut by remember { mutableStateOf(false) }
            BoutonStatut(MaterialTheme.colorScheme.onSurfaceVariant) { feuilleStatut = true }
            if (feuilleStatut) FeuilleStatut { feuilleStatut = false }
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
            // Recherche dans les messages eux-mêmes, avec filtres (personne, période, pièces jointes)
            IconButton(onClick = rechercheAvancee) {
                Icon(Icons.AutoMirrored.Outlined.ManageSearch, "Rechercher dans les messages (filtres)", tint = MaterialTheme.colorScheme.primary)
            }
        }
        // Pastilles de vue
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val nNonLus = e.nombreMessagesNonLus(source, espace.pro)
            val nATraiter = e.nombreATraiter(source, espace.pro)
            var menuVue by remember { mutableStateOf(false) }
            Vue.entries.forEach { v ->
                if (v == Vue.Spam && e.nombreSpams == 0 && vue != Vue.Spam) return@forEach   // pastille Spam seulement s'il y en a
                when (v) {
                    Vue.ATraiter -> {}   // regroupée avec « Non lues »
                    Vue.NonLus -> Box {
                        PastilleVue((if (vue == Vue.ATraiter) "À traiter" else "Non lues") + " ▾",
                            if (vue == Vue.ATraiter) nATraiter else nNonLus, vue == Vue.NonLus || vue == Vue.ATraiter) { menuVue = true }
                        androidx.compose.material3.DropdownMenu(expanded = menuVue, onDismissRequest = { menuVue = false }) {
                            listOf(Vue.NonLus to nNonLus, Vue.ATraiter to nATraiter).forEach { (vv, n) ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(vv.libelle + if (n > 0) "  ($n)" else "") },
                                    onClick = { menuVue = false; choisirVue(vv) },
                                    trailingIcon = { if (vv == vue) Icon(Icons.Outlined.TaskAlt, null) })
                            }
                        }
                    }
                    else -> PastilleVue(v.libelle, if (v == Vue.Spam) e.nombreSpams else 0, v == vue) { choisirVue(v) }
                }
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
        Modifier.height(32.dp)
            .background(if (choisie) cs.onSurface else cs.surfaceContainerLow, RoundedCornerShape(16.dp))
            .then(if (choisie) Modifier else Modifier.border(1.dp, cs.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)))
            .clip(RoundedCornerShape(16.dp)).clickable(onClick = clic).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, fontSize = 13.sp, fontWeight = if (choisie) FontWeight.SemiBold else FontWeight.Medium,
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
private fun CarteConversation(e: Etat, c: Conversation, modifier: Modifier, ouvrir: () -> Unit, actions: () -> Unit, choisie: Boolean = false) {
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
            .background(if (choisie) cs.primaryContainer else fond, forme)
            .then(if (choisie) Modifier.border(2.dp, cs.primary, forme)
                else if (design == 1) Modifier else Modifier.border(1.dp, if (design == 2) c.source.teinte().copy(alpha = 0.25f) else cs.outlineVariant, forme))
            .clip(forme)
            .combinedClickable(onClick = ouvrir, onLongClick = actions)
            .padding(horizontal = if (design == 1) 4.dp else 12.dp, vertical = when (design) { 1 -> 9.dp; 3 -> 16.dp; else -> 12.dp }),
        verticalAlignment = if (design == 1) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box {
            Avatar(c.nom, c.source, c.estGroupe, taille = when (design) { 1 -> 40.dp; 3 -> 56.dp; else -> 48.dp }, bordure = cs.surfaceContainerLow, cle = c.cle)
            if (choisie) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).background(cs.primary, androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, cs.surface, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Text("✓", color = cs.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
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
            // Aperçu : le texte du dernier message (le résumé IA reste dans la conversation)
            val apercu = c.messages.lastOrNull()?.let { m -> (if (m.deMoi) "Vous : " else if (c.estGroupe) "${m.auteur} : " else "") + apercuMessage(m) } ?: ""
            Row {
                Text(apercu, fontSize = 14.sp, lineHeight = 19.sp, maxLines = when (design) { 1 -> 1; 3 -> 3; else -> 2 }, overflow = TextOverflow.Ellipsis,
                    color = if (nonLue) cs.onSurface else cs.onSurfaceVariant)
            }
            if (aTraiter || nb > 0) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (aTraiter) Box {
                    // Toucher l'étiquette pour changer le statut
                    var menuStatut by remember { mutableStateOf(false) }
                    Text("Réponse attendue ▾", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.onTertiaryContainer,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cs.tertiaryContainer).clickable { menuStatut = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                    androidx.compose.material3.DropdownMenu(expanded = menuStatut, onDismissRequest = { menuStatut = false }) {
                        val actuel = Moteur.statutChoisi(c)
                        listOf(Moteur.Statut.ATTENDUE to "Réponse attendue", Moteur.Statut.AUCUNE to "Pas de réponse attendue",
                            Moteur.Statut.AUTO to "Automatique (IA)").forEach { (st, t) ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text((if (st == actuel) "✓ " else "    ") + t) },
                                onClick = { menuStatut = false; Moteur.choisirStatut(c, st) })
                        }
                    }
                }
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

/** Barre du haut quand des conversations sont sélectionnées : actions groupées (toutes locales). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarreSelection(e: Etat, liste: List<Conversation>, toutChoisir: () -> Unit, plus: (Conversation) -> Unit, fin: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val n = liste.size
    fun pourTous(action: (Conversation) -> Unit) { liste.forEach(action); fin() }
    var confirmerSuppr by remember { mutableStateOf(false) }
    if (confirmerSuppr) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmerSuppr = false },
        title = { Text("Supprimer $n conversation" + (if (n > 1) "s" else "") + " ?") },
        text = { Text("Elles sont supprimées de Messagerie seulement : les messages restent chez vos contacts et sur les messageries. " +
            "Une conversation revient si un nouveau message arrive.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmerSuppr = false; pourTous { Moteur.supprimerConversation(it) } }) {
            Text("Supprimer", color = cs.error) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmerSuppr = false }) { Text("Annuler") } },
    )
    androidx.compose.material3.TopAppBar(
        navigationIcon = { IconButton(onClick = fin) { Icon(Icons.Outlined.Close, "Annuler la sélection") } },
        title = { Text("$n sélectionnée" + if (n > 1) "s" else "") },
        actions = {
            // Selon l'état : si toutes sont déjà dans un état, seule l'action inverse est proposée
            val toutesATraiter = liste.all { e.aTraiter(it) }
            val toutesLues = liste.none { e.estNonLue(it) }
            if (toutesATraiter) IconButton(onClick = { pourTous { Moteur.marquerTraitee(it) } }) { Icon(Icons.Outlined.TaskAlt, "Marquer comme traitées") }
            else IconButton(onClick = { pourTous { Moteur.mettreATraiter(it, true) } }) { Icon(Icons.Outlined.Flag, "Mettre à traiter") }
            if (toutesLues) IconButton(onClick = { pourTous { Moteur.marquerNonLue(it) } }) { Icon(Icons.Outlined.MarkEmailUnread, "Marquer comme non lues") }
            else IconButton(onClick = { pourTous { Moteur.marquerLue(it) } }) { Icon(Icons.Outlined.MarkEmailRead, "Marquer comme lues") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Plus d'actions") }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val choix = buildList<Pair<String, () -> Unit>> {
                        add("Tout sélectionner" to { toutChoisir() })
                        // Chaque action n'apparaît que si au moins une conversation choisie n'est pas déjà dans cet état
                        if (liste.any { e.aTraiter(it) } && !toutesATraiter) add("Marquer comme traitées" to { pourTous { if (e.aTraiter(it)) Moteur.marquerTraitee(it) } })
                        if (liste.any { e.estNonLue(it) } && liste.any { !e.estNonLue(it) }) add("Marquer comme non lues" to { pourTous { Moteur.marquerNonLue(it) } })
                        if (liste.any { !e.estEpinglee(it) }) add("Épingler" to { pourTous { if (!e.estEpinglee(it)) Moteur.basculerEpingle(it) } })
                        if (liste.any { e.estEpinglee(it) }) add("Désépingler" to { pourTous { if (e.estEpinglee(it)) Moteur.basculerEpingle(it) } })
                        if (liste.any { !e.estArchivee(it) }) add("Archiver" to { pourTous { if (!e.estArchivee(it)) Moteur.basculerArchive(it) } })
                        if (liste.any { e.estArchivee(it) }) add("Désarchiver" to { pourTous { if (e.estArchivee(it)) Moteur.basculerArchive(it) } })
                        if (liste.any { !e.etat(it).sourdine }) add("Mettre en sourdine" to { pourTous { if (!e.etat(it).sourdine) Moteur.basculerSourdine(it) } })
                        if (liste.any { e.etat(it).sourdine }) add("Réactiver les notifications" to { pourTous { if (e.etat(it).sourdine) Moteur.basculerSourdine(it) } })
                        if (liste.any { !e.estPro(it) }) add("Ranger en Pro" to { pourTous { if (!e.estPro(it)) Moteur.classer(it, if (it.source in e.pro) null else "pro") } })
                        if (liste.any { e.estPro(it) }) add("Ranger en Perso" to { pourTous { if (e.estPro(it)) Moteur.classer(it, if (it.source in e.pro) "perso" else null) } })
                        add("Supprimer…" to { confirmerSuppr = true })
                        if (n == 1) add("Autres actions…" to { val c = liste.first(); fin(); plus(c) })
                    }
                    choix.forEach { (t, a) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(t) }, onClick = { menu = false; a() })
                    }
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = cs.secondaryContainer),
    )
}

/** Panneau d'actions (appui long ou ⋮). Toutes LOCALES sauf « Résumé IA » (Ollama sur votre serveur). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleActions(e: Etat, c: Conversation, fermer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var aConfirmer by remember { mutableStateOf<Action?>(null) }
    ConfirmerAction(aConfirmer, fermer = { aConfirmer = null }, apres = fermer)
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
                if (e.aTraiter(c)) TuileAction("Traitée", Icons.Outlined.TaskAlt, Color(0xFF1E7A47), Modifier.weight(1f)) { Moteur.marquerTraitee(c); fermer() }
                else TuileAction("À traiter", Icons.Outlined.Flag, Color(0xFFA04A0B), Modifier.weight(1f)) { Moteur.mettreATraiter(c, true); fermer() }
            }
            Spacer(Modifier.height(10.dp))
            ChoixStatut(e, c)
            Spacer(Modifier.height(8.dp))
            val rapides = setOf("Marquer comme lu", "Marquer comme non lu", "Marquer comme traitée", "Ajouter à « À traiter »", "Refaire le résumé IA")
            actionsConversation(e, c).filter { it.libelle !in rapides }.forEach { a ->
                Row(Modifier.fillMaxWidth().height(52.dp).clickable { if (a.confirmer != null) aConfirmer = a else { a.faire(); fermer() } }.padding(horizontal = 8.dp),
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

/** Alerte : une ou plusieurs messageries sont déconnectées (on ne reçoit plus leurs messages). */
@Composable
private fun BandeauPannes(pannes: List<fr.messagerie.unifiee.Surveillance.Panne>, ouvrir: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(cs.errorContainer).clickable(onClick = ouvrir)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = cs.onErrorContainer, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (pannes.size == 1) "${pannes[0].source.libelle} est déconnecté"
                else pannes.joinToString(", ") { it.source.libelle } + " sont déconnectés",
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = cs.onErrorContainer,
            )
            val p = pannes.first()
            Text(
                (p.depuis?.let { "Depuis " + dateCourte(it) + " à " + heure(it) + " · " } ?: "") + p.detail,
                fontSize = 12.sp, color = cs.onErrorContainer, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Texte court d'un message pour l'aperçu de la liste (pièces jointes nommées, vocaux transcrits si possible). */
fun apercuMessage(m: fr.messagerie.unifiee.Message): String = when (m.type) {
    "audio" -> Moteur.transcriptionConnue(m)?.let { "🎤 « $it »" } ?: ("🎤 Message vocal" + (m.duree?.let { d -> " (" + (d.toInt() / 60) + ":" + "%02d".format(d.toInt() % 60) + ")" } ?: ""))
    "image" -> "📷 " + m.texte.takeIf { it.isNotBlank() && !Regex("""(?i)\.(jpe?g|png|webp|heic|gif)$""").containsMatchIn(it) }.orEmpty().ifEmpty { "Photo" }
    "video" -> "🎬 " + m.texte.takeIf { it.isNotBlank() && !Regex("""(?i)\.(mp4|mov|3gp|webm)$""").containsMatchIn(it) }.orEmpty().ifEmpty { "Vidéo" }
    "fichier" -> "📎 " + m.texte.ifBlank { "Fichier" }
    else -> m.texte.replace(Regex("\\s+"), " ").trim()
}

/** Couleur du bandeau (style « Bandeau »). */
@Composable
internal fun couleurBandeau(): Color {
    val sombre = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val choisie = fr.messagerie.unifiee.Affichage.bandeau
    // Couleur choisie : un peu assombrie en thème sombre pour rester discrète
    if (choisie != 0L) return Color(choisie).let { if (sombre) androidx.compose.ui.graphics.lerp(it, Color.Black, 0.25f) else it }
    return if (sombre) Color(0xFF1A2350) else Color(0xFF22306E)
}

/**
 * Haut de liste compact : une ligne menu + recherche + réglages, puis les puces de filtre.
 * bandeau = true : fond bleu nuit, recherche blanche ; false : style épuré (fond clair, onglets soulignés).
 */
@Composable
private fun EnteteCompact(
    e: Etat, bandeau: Boolean, vue: Vue, genre: Genre, espace: Espace, source: Source?, nombre: Int, recherche: String,
    choisirGenre: (Genre) -> Unit, choisirEspace: (Espace) -> Unit, choisirSource: (Source?) -> Unit,
    ouvrirTiroir: () -> Unit, ouvrirReglages: () -> Unit, chercher: (String) -> Unit, choisirVue: (Vue) -> Unit,
    rechercheAvancee: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val fondHaut = if (bandeau) couleurBandeau() else cs.surface
    val texteHaut = if (bandeau) Color.White else cs.onSurface
    // Icônes de la barre d'état en clair sur le bandeau
    val vueAndroid = androidx.compose.ui.platform.LocalView.current
    if (bandeau) androidx.compose.runtime.DisposableEffect(Unit) {
        val fenetre = (vueAndroid.context as? android.app.Activity)?.window
        val ctrl = fenetre?.let { androidx.core.view.WindowCompat.getInsetsController(it, vueAndroid) }
        val avant = ctrl?.isAppearanceLightStatusBars
        ctrl?.isAppearanceLightStatusBars = false
        onDispose { if (avant != null) ctrl.isAppearanceLightStatusBars = avant }
    }
    var menuEspace by remember { mutableStateOf(false) }
    var menuVue by remember { mutableStateOf(false) }
    var feuilleStatut by remember { mutableStateOf(false) }
    if (feuilleStatut) FeuilleStatut { feuilleStatut = false }
    Column(
        Modifier.fillMaxWidth().background(fondHaut).statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = if (bandeau) 24.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (bandeau) 10.dp else 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ouvrirTiroir) { Icon(Icons.Outlined.Menu, "Menu", tint = if (bandeau) Color.White else cs.onSurfaceVariant) }
            Row(
                Modifier.weight(1f).height(if (bandeau) 46.dp else 44.dp)
                    .background(if (bandeau) Color.White else cs.surfaceContainerHigh, RoundedCornerShape(if (bandeau) 23.dp else 12.dp))
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val gris = if (bandeau) Color(0xFF5A606D) else cs.onSurfaceVariant
                Icon(Icons.Outlined.Search, null, tint = gris, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (recherche.isEmpty()) Text("Rechercher", color = gris, fontSize = 15.sp)
                    BasicTextField(
                        value = recherche, onValueChange = chercher, singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = if (bandeau) Color(0xFF15171C) else cs.onSurface),
                        cursorBrush = SolidColor(cs.primary), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (recherche.isNotEmpty()) IconButton(onClick = { chercher("") }) { Icon(Icons.Outlined.Close, "Effacer", tint = gris) }
                IconButton(onClick = rechercheAvancee) {
                    Icon(Icons.AutoMirrored.Outlined.ManageSearch, "Rechercher dans les messages (filtres)", tint = Color(0xFF3B5BDB))
                }
            }
            if (!bandeau) BoutonStatut(cs.onSurfaceVariant) { feuilleStatut = true }
            if (e.enCours) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = texteHaut)
            } else IconButton(onClick = ouvrirReglages) { Icon(Icons.Outlined.Settings, "Réglages", tint = if (bandeau) Color.White else cs.onSurfaceVariant) }
        }
        // Puces : vues, puis Perso / Pro, puis discussions / groupes
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (bandeau) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            if (source != null) PuceCompacte(bandeau, source.libelle + " ✕", 0, false, true) { choisirSource(null) }
            // Ordre : Toutes, Perso · Pro, Non lues · À traiter (regroupés), Archivées, Spam
            PuceCompacte(bandeau, Vue.Toutes.libelle, 0, false, vue == Vue.Toutes) { choisirVue(Vue.Toutes) }
            Box {
                PuceCompacte(bandeau, (if (espace == Espace.Tout) "Perso · Pro" else espace.libelle) + " ▾", 0, false, espace != Espace.Tout) { menuEspace = true }
                androidx.compose.material3.DropdownMenu(expanded = menuEspace, onDismissRequest = { menuEspace = false }) {
                    Espace.entries.forEach { es ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(if (es == Espace.Tout) "Perso et Pro" else es.libelle) },
                            onClick = { menuEspace = false; choisirEspace(es) },
                            trailingIcon = { if (es == espace) Icon(Icons.Outlined.TaskAlt, null) })
                    }
                }
            }
            val nNonLus = e.nombreMessagesNonLus(source, espace.pro)
            val nATraiter = e.nombreATraiter(source, espace.pro)
            Box {
                val choisie = vue == Vue.NonLus || vue == Vue.ATraiter
                PuceCompacte(bandeau, (if (vue == Vue.ATraiter) "À traiter" else "Non lues") + " ▾",
                    if (vue == Vue.ATraiter) nATraiter else nNonLus, vue == Vue.ATraiter, choisie,
                    n2 = if (vue == Vue.ATraiter) 0 else nATraiter) { menuVue = true }
                androidx.compose.material3.DropdownMenu(expanded = menuVue, onDismissRequest = { menuVue = false }) {
                    listOf(Vue.NonLus to nNonLus, Vue.ATraiter to nATraiter).forEach { (v, n) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(v.libelle + if (n > 0) "  ($n)" else "") },
                            onClick = { menuVue = false; choisirVue(v) },
                            trailingIcon = { if (v == vue) Icon(Icons.Outlined.TaskAlt, null) })
                    }
                }
            }
            PuceCompacte(bandeau, Vue.Archivees.libelle, 0, false, vue == Vue.Archivees) { choisirVue(Vue.Archivees) }
            if (e.nombreSpams > 0 || vue == Vue.Spam) PuceCompacte(bandeau, Vue.Spam.libelle, e.nombreSpams, false, vue == Vue.Spam) { choisirVue(Vue.Spam) }
            PuceCompacte(bandeau, Genre.Discussions.libelle, 0, false, genre == Genre.Discussions) { choisirGenre(Genre.Discussions) }
            PuceCompacte(bandeau, Genre.Groupes.libelle, 0, false, genre == Genre.Groupes) { choisirGenre(Genre.Groupes) }
        }
        if (bandeau) Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    e.importEnCours != null -> e.importEnCours
                    e.enCours -> "Actualisation…"
                    else -> (e.derniereActualisation?.let { "Actualisé à " + heure(it) + " · " } ?: "") + "$nombre conversation" + if (nombre > 1) "s" else ""
                },
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp).clickable { Moteur.actualiserMaintenant() },
            )
            PuceStatut { feuilleStatut = true }
        }
        if (!bandeau) HorizontalDivider(color = cs.outlineVariant)
    }
}

@Composable
private fun PuceCompacte(bandeau: Boolean, libelle: String, n: Int, attente: Boolean, choisie: Boolean, n2: Int = 0, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (bandeau) {
        val nuit = couleurBandeau()
        Row(
            Modifier.height(30.dp)
                .background(if (choisie) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(15.dp))
                .then(if (choisie) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(15.dp)))
                .clip(RoundedCornerShape(15.dp)).clickable(onClick = clic).padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(libelle, fontSize = 13.sp, fontWeight = if (choisie) FontWeight.SemiBold else FontWeight.Medium, color = if (choisie) nuit else Color.White)
            if (n > 0) {
                Spacer(Modifier.width(6.dp))
                Text(compte(n), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = when { attente -> Color(0xFF3B1F06); choisie -> Color.White; else -> Color.White },
                    modifier = Modifier.background(when { attente -> Color(0xFFF7B267); choisie -> Color(0xFF3B5BDB); else -> Color.White.copy(alpha = 0.18f) }, RoundedCornerShape(9.dp))
                        .padding(horizontal = 6.dp, vertical = 0.dp))
            }
            if (n2 > 0) {
                Spacer(Modifier.width(4.dp))
                Text(compte(n2), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B1F06),
                    modifier = Modifier.background(Color(0xFFF7B267), RoundedCornerShape(9.dp)).padding(horizontal = 6.dp, vertical = 0.dp))
            }
        }
    } else {
        // Épuré : onglet souligné
        Column(Modifier.clickable(onClick = clic).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(libelle, fontSize = 14.sp, fontWeight = if (choisie) FontWeight.Bold else FontWeight.Medium,
                    color = if (choisie) cs.onSurface else cs.onSurfaceVariant)
                if (n > 0) {
                    Spacer(Modifier.width(5.dp))
                    Text(compte(n), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (attente) Color(0xFFA04A0B) else cs.primary)
                }
                if (n2 > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(compte(n2), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA04A0B))
                }
            }
            Box(Modifier.height(2.5.dp).width(28.dp).background(if (choisie) cs.onSurface else Color.Transparent, RoundedCornerShape(2.dp)))
        }
    }
}
