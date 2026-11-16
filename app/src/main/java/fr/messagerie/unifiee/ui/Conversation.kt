package fr.messagerie.unifiee.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.text.withLink
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTapGestures
import fr.messagerie.unifiee.Lecteur
import fr.messagerie.unifiee.MaReaction
import fr.messagerie.unifiee.Reaction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.Message
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Ollama
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class Ligne(val cle: String) {
    class Jour(val date: Long) : Ligne("jour-$date")
    data object NonLus : Ligne("nonlus")
    class Msg(val m: Message, val auteur: Boolean) : Ligne(m.cle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranConversation(e: Etat, c: Conversation, vm: ConversationVM, retour: () -> Unit) {
    val portee = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val historique by vm.historique.collectAsState()
    val total by vm.total.collectAsState()
    val chargement by vm.chargement.collectAsState()
    val reactions by vm.reactions.collectAsState()
    val mesReactions by vm.mesReactions.collectAsState()
    var brouillon by rememberSaveable(c.cle) { mutableStateOf("") }
    // Versions précédentes du texte avant chaque modification par l'IA (pour revenir en arrière)
    var versionsIA by remember(c.cle) { mutableStateOf(listOf<String>()) }
    var saisieAgrandie by rememberSaveable(c.cle) { mutableStateOf(false) }
    // Champ de saisie avec position du curseur (pour les mentions @)
    var champ by remember(c.cle) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    // Correcteur d'orthographe du téléphone (sur l'appareil, rien n'est envoyé)
    val correcteur = remember { fr.messagerie.unifiee.Correcteur(Moteur.app) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { correcteur.fermer() } }
    var fautes by remember(c.cle) { mutableStateOf<Pair<String, List<fr.messagerie.unifiee.Faute>>>("" to emptyList()) }
    LaunchedEffect(brouillon) {
        if (brouillon.isBlank()) { fautes = "" to emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(500)
        val t = brouillon
        fautes = t to correcteur.verifier(t)
    }
    // Mentions insérées : nom affiché → identifiant du membre
    var mentions by remember(c.cle) { mutableStateOf(mapOf<String, String>()) }
    val membresMention by androidx.compose.runtime.produceState<List<Moteur.Membre>>(emptyList(), c.cle) {
        if (c.estGroupe) value = Moteur.membresGroupe(c).orEmpty().filter { !it.moi && it.id.isNotEmpty() }
    }
    // Image collée depuis le presse-papiers (copie d'écran…) : envoyée seulement sur « Envoyer »
    var imageCollee by remember(c.cle) { mutableStateOf<Pair<ByteArray, String>?>(null) }
    var imageDispo by remember { mutableStateOf(false) }
    var reponseA by remember(c.cle) { mutableStateOf<Message?>(null) }
    var envoi by remember { mutableStateOf(false) }
    var iaEnCours by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var menuIA by remember { mutableStateOf(false) }
    var menuAppel by remember { mutableStateOf(false) }
    var menuPlus by remember { mutableStateOf(false) }
    var infos by remember { mutableStateOf(false) }
    // Sélection de plusieurs messages (copier, transférer)
    var selection by remember(c.cle) { mutableStateOf(setOf<String>()) }
    var modeSelection by remember(c.cle) { mutableStateOf(false) }
    var transfert by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<Conversation?>(null) }
    var transfertEnCours by remember { mutableStateOf(false) }
    fun quitterSelection() { modeSelection = false; selection = emptySet() }
    fun basculerSelection(m: Message) {
        modeSelection = true
        selection = if (m.cle in selection) selection - m.cle else selection + m.cle
        if (selection.isEmpty()) modeSelection = false
    }
    androidx.activity.compose.BackHandler(enabled = modeSelection) { quitterSelection() }
    // Recherche dans la discussion
    var rechercheOuverte by remember(c.cle) { mutableStateOf(false) }
    var requete by remember(c.cle) { mutableStateOf("") }
    val trouves by vm.trouves.collectAsState()
    var rang by remember(c.cle) { mutableStateOf(0) }
    var courant by remember(c.cle) { mutableStateOf<String?>(null) }
    val liste = androidx.compose.foundation.lazy.rememberLazyListState()
    val ctxVocal = LocalContext.current
    val enregistreur = remember(c.cle) { fr.messagerie.unifiee.Enregistreur(ctxVocal.applicationContext) }
    var vocal by remember(c.cle) { mutableStateOf(0) }   // 0 rien, 1 enregistrement, 2 aperçu
    var ecouteVocal by remember(c.cle) { mutableStateOf(false) }
    var tempsVocal by remember(c.cle) { mutableStateOf(0L) }
    androidx.compose.runtime.DisposableEffect(c.cle) { onDispose { enregistreur.annuler() } }
    // Le presse-papiers contient-il une image ? (on lit seulement sa description ; l'image n'est lue que sur appui)
    val pressePapiers = remember { ctxVocal.getSystemService(ClipboardManager::class.java) }
    fun verifierPressePapiers() {
        imageDispo = runCatching { pressePapiers.primaryClipDescription?.hasMimeType("image/*") == true }.getOrDefault(false)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val l = ClipboardManager.OnPrimaryClipChangedListener { verifierPressePapiers() }
        pressePapiers.addPrimaryClipChangedListener(l)
        onDispose { pressePapiers.removePrimaryClipChangedListener(l) }
    }
    val fenetreActive = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(fenetreActive) { if (fenetreActive) verifierPressePapiers() }
    fun collerImage() {
        runCatching {
            val clip = pressePapiers.primaryClip ?: error("presse-papiers vide")
            val uri = (0 until clip.itemCount).firstNotNullOfOrNull { clip.getItemAt(it).uri } ?: error("aucune image")
            val mime = ctxVocal.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/png"
            val octets = ctxVocal.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("image illisible")
            if (octets.size > 20_000_000) error("image trop lourde")
            imageCollee = octets to mime
        }.onFailure { portee.launch { snack.showSnackbar("Impossible de coller l'image : ${it.message}") } }
    }
    LaunchedEffect(vocal) { while (vocal == 1) { tempsVocal = enregistreur.ecoule(); kotlinx.coroutines.delay(200) } }
    val demandeMicro = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) runCatching { enregistreur.demarrer(); vocal = 1 }
    }
    // Date de lecture à l'ouverture : place le repère « Nouveaux messages ».
    val luAvant = remember(c.cle) { if (e.estNonLue(c)) e.luJusqua(c) else null }

    LaunchedEffect(c.cle) { Moteur.marquerLue(c) }
    androidx.compose.runtime.DisposableEffect(c.cle) {
        Moteur.convOuverte = c.cle
        androidx.core.app.NotificationManagerCompat.from(Moteur.app).cancel(c.cle.hashCode())
        onDispose { if (Moteur.convOuverte == c.cle) Moteur.convOuverte = null }
    }   // lu dans l'appli seulement : aucun accusé envoyé
    LaunchedEffect(c.derniere) {
        vm.actualiser(c.messages)
        if (!e.estForcee(c)) Moteur.marquerLue(c)
    }

    val lignes = remember(historique, luAvant) {
        val l = ArrayList<Ligne>()
        var nonLusPlace = false
        historique.forEachIndexed { i, m ->
            val prec = historique.getOrNull(i - 1)
            if (prec == null || jourComplet(prec.date) != jourComplet(m.date)) l.add(Ligne.Jour(m.date))
            if (!nonLusPlace && luAvant != null && !m.deMoi && m.date > luAvant) { l.add(Ligne.NonLus); nonLusPlace = true }
            val suite = prec != null && prec.auteur == m.auteur && m.date - prec.date < 300_000
            l.add(Ligne.Msg(m, !suite))
        }
        l
    }

    fun afficher(t: String) { portee.launch { snack.showSnackbar(t) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (modeSelection) TopAppBar(
                navigationIcon = { IconButton(onClick = { quitterSelection() }) { Icon(Icons.Outlined.Close, "Annuler la sélection") } },
                title = { Text("${selection.size} sélectionné" + if (selection.size > 1) "s" else "") },
                actions = {
                    val ctxSel = LocalContext.current
                    val choisis = historique.filter { it.cle in selection }
                    IconButton(onClick = {
                        val texte = choisis.joinToString("\n") { m ->
                            (if (choisis.size > 1) "[${heure(m.date)}] ${m.auteur} : " else "") + m.texteUtile
                        }
                        copier(ctxSel, texte); afficher(if (choisis.size > 1) "${choisis.size} messages copiés" else "Texte copié"); quitterSelection()
                    }, enabled = choisis.isNotEmpty()) { Icon(Icons.Outlined.ContentCopy, "Copier") }
                    IconButton(onClick = { transfert = true }, enabled = choisis.isNotEmpty() && !transfertEnCours) {
                        if (transfertEnCours) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Outlined.Forward, "Transférer")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) else TopAppBar(
                navigationIcon = { IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                title = {
                    if (rechercheOuverte) {
                        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        androidx.compose.foundation.text.BasicTextField(
                            value = requete, onValueChange = { requete = it; vm.chercher(it) }, singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            decorationBox = { champ ->
                                Box { if (requete.isEmpty()) Text("Rechercher dans la discussion", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp); champ() }
                            },
                        )
                    } else
                    Row(Modifier.clickable { infos = true }, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.nom, c.source, c.estGroupe, taille = 38.dp, cle = c.cle)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(c.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.libelleSource(c) + if (c.estGroupe) " · groupe" else "",
                            style = MaterialTheme.typography.labelMedium, color = c.source.teinte())
                    }
                    }
                },
                actions = actions@{
                    if (rechercheOuverte) {
                        fun aller(i: Int) {
                            if (trouves.isEmpty()) return
                            rang = i.coerceIn(0, trouves.size - 1)
                            val m = trouves[trouves.size - 1 - rang]   // du plus récent au plus ancien
                            courant = m.cle
                            vm.allerA(m) { portee.launch {
                                kotlinx.coroutines.delay(80)
                                val idx = lignes.asReversed().indexOfFirst { it is Ligne.Msg && it.m.cle == m.cle }
                                if (idx >= 0) liste.animateScrollToItem(idx + 1)
                            } }
                        }
                        LaunchedEffect(trouves) { if (trouves.isNotEmpty()) aller(0) else courant = null }
                        Text(if (requete.isBlank()) "" else if (trouves.isEmpty()) "0" else "${rang + 1}/${trouves.size}",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { aller(rang + 1) }, enabled = rang < trouves.size - 1) { Icon(Icons.Outlined.KeyboardArrowUp, "Plus ancien") }
                        IconButton(onClick = { aller(rang - 1) }, enabled = rang > 0) { Icon(Icons.Outlined.KeyboardArrowDown, "Plus récent") }
                        IconButton(onClick = { rechercheOuverte = false; requete = ""; vm.chercher(""); courant = null }) { Icon(Icons.Outlined.Close, "Fermer la recherche") }
                        return@actions
                    }
                    // Icônes regroupées : l'appel reste visible, le reste passe dans le menu ⋯
                    val ctxAppel = LocalContext.current
                    val appels = remember(c.cle) { moyensAppel(c) }
                    Box {
                        IconButton(onClick = { menuAppel = true }) { Icon(Icons.Outlined.Call, "Appeler") }
                        DropdownMenu(expanded = menuAppel, onDismissRequest = { menuAppel = false }) {
                            appels.forEach { (libelle, lien) ->
                                DropdownMenuItem(text = { Text(libelle) }, onClick = { menuAppel = false; lancerAppel(ctxAppel, lien) })
                            }
                            if (c.source != Source.TEAMS) {
                                HorizontalDivider()
                                // Jitsi Meet : appel vidéo gratuit sans compte ; le lien est mis dans le champ, envoyé seulement si vous appuyez sur Envoyer
                                DropdownMenuItem(
                                    text = { Column {
                                        Text("Appel vidéo Jitsi")
                                        Text("S'ouvre dans l'appli ; le lien est mis dans votre message (à envoyer)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } },
                                    onClick = {
                                        menuAppel = false
                                        val lien = fr.messagerie.unifiee.AppelJitsi.nouveauLien(Moteur.cfg)
                                        brouillon = (if (brouillon.isBlank()) "" else brouillon.trimEnd() + " ") + "Appel vidéo : $lien"
                                        fr.messagerie.unifiee.AppelJitsi.ouvrir(ctxAppel, lien)
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuPlus = true }) { Icon(Icons.Outlined.MoreVert, "Plus d'actions") }
                        DropdownMenu(expanded = menuPlus, onDismissRequest = { menuPlus = false }) {
                            DropdownMenuItem(text = { Text("Rechercher") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                onClick = { menuPlus = false; rechercheOuverte = true })
                            DropdownMenuItem(text = { Text(if (c.estGroupe) "Infos du groupe" else "Infos du contact") }, leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                onClick = { menuPlus = false; infos = true })
                            DropdownMenuItem(text = { Text("Sélectionner des messages") }, leadingIcon = { Icon(Icons.Outlined.Checklist, null) },
                                onClick = { menuPlus = false; modeSelection = true })
                            DropdownMenuItem(text = { Text("Actions de la conversation") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                                onClick = { menuPlus = false; menu = true })
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = liste, reverseLayout = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                item(key = "resume") { CarteResume(e, c) { brouillon = it } }
                items(lignes.asReversed(), key = { it.cle }) { l ->
                    when (l) {
                        is Ligne.Jour -> Text(jourComplet(l.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Ligne.NonLus -> Text("— Nouveaux messages —", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Ligne.Msg -> Row(Modifier.fillMaxWidth().background(
                            when {
                                l.m.cle in selection -> c.source.teinte().copy(alpha = 0.14f)
                                l.m.cle == courant -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                else -> Color.Transparent
                            }, RoundedCornerShape(14.dp))
                            .then(if (modeSelection) Modifier.clickable { basculerSelection(l.m) } else Modifier),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (modeSelection) { Spacer(Modifier.width(4.dp)); PastilleSelection(l.m.cle in selection, c.source.teinte()); Spacer(Modifier.width(6.dp)) }
                            Box(Modifier.weight(1f)) { Bulle(l.m, c, l.auteur,
                            reactions = reactions[l.m.cle].orEmpty(), maReaction = mesReactions[l.m.cle],
                            repondre = { reponseA = l.m },
                            copier = { copier(it, l.m.texteUtile); afficher("Texte copié") },
                            reagir = { emoji -> vm.reagir(l.m, c, emoji) { afficher(it) } },
                            afficherMessage = { afficher(it) },
                            luParContact = e.lusParContacts[c.cle] ?: 0L,
                            modeSelection = modeSelection, choisi = l.m.cle in selection,
                            basculer = { basculerSelection(l.m) }) } }
                    }
                }
                if (historique.size < total) item(key = "plus") {
                    OutlinedButton(onClick = { vm.plusAnciens() }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(if (chargement) "Chargement…" else "Messages plus anciens (${total - historique.size})")
                    }
                }
            }

            // --- Compositeur (Teams : lecture seule, on répond depuis l'appli Teams)
            if (c.source == Source.TEAMS) Surface(tonalElevation = 3.dp) {
                val ctx = LocalContext.current
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.synthese(c)?.reponse?.takeIf { it.isNotEmpty() && it != brouillon }?.let { r ->
                        Text("✨ $r", fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().border(1.dp, degradeIA, RoundedCornerShape(10.dp))
                                .clickable { brouillon = r }.padding(8.dp))
                    }
                    OutlinedTextField(
                        value = brouillon, onValueChange = { brouillon = it },
                        placeholder = { Text("Préparer la réponse Teams…") },
                        maxLines = 6, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rien n'est envoyé d'ici : le texte est copié, collez-le dans Teams.", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (brouillon.isNotBlank()) {
                                ctx.getSystemService(android.content.ClipboardManager::class.java)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("Réponse Teams", brouillon.trim()))
                                android.widget.Toast.makeText(ctx, "Réponse copiée : collez-la dans Teams", android.widget.Toast.LENGTH_LONG).show()
                            }
                            ouvrirTeams(ctx, c.jid, historique.lastOrNull()?.cle?.substringAfter(':'))
                        }, colors = ButtonDefaults.buttonColors(containerColor = c.source.teinte())) {
                            Text(if (brouillon.isNotBlank()) "Copier et ouvrir Teams" else "Ouvrir Teams")
                        }
                    }
                }
            } else Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reponseA?.let { m ->
                        Row(Modifier.fillMaxWidth().background(c.source.teinte().copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("En réponse à ${m.auteur}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte())
                                Text(m.texteUtile, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            }
                            IconButton(onClick = { reponseA = null }) { Icon(Icons.Outlined.Close, "Annuler la réponse") }
                        }
                    }
                    // Réponse proposée par l'IA : jamais insérée d'office, seulement sur votre appui.
                    e.synthese(c)?.reponse?.takeIf { it.isNotEmpty() && it != brouillon }?.let { r ->
                        Text("✨ $r", fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().border(1.dp, degradeIA, RoundedCornerShape(10.dp))
                                .clickable { brouillon = r }.padding(8.dp))
                    }
                    if (versionsIA.isNotEmpty()) Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .border(1.dp, degradeIA, RoundedCornerShape(12.dp)).padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("✨ Modifié par l'IA", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { brouillon = versionsIA.last(); versionsIA = versionsIA.dropLast(1) }) {
                            Text(if (versionsIA.size > 1) "↶ Annuler (${versionsIA.size})" else "↶ Annuler", fontSize = 13.sp)
                        }
                        if (versionsIA.size > 1) TextButton(onClick = { brouillon = versionsIA.first(); versionsIA = emptyList() }) { Text("Mon texte", fontSize = 13.sp) }
                    }
                    // Image collée : aperçu, puis envoi sur appui
                    imageCollee?.let { (octets, mime) ->
                        Row(Modifier.fillMaxWidth().background(c.source.teinte().copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            coil3.compose.AsyncImage(model = octets, contentDescription = "Image collée",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(72.dp).background(Color.White, RoundedCornerShape(10.dp))
                                    .then(Modifier.padding(0.dp)))
                            Column(Modifier.weight(1f)) {
                                Text("Image à envoyer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${octets.size / 1024} Ko", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { imageCollee = null }) { Icon(Icons.Outlined.Close, "Retirer l'image") }
                            Button(onClick = {
                                envoi = true
                                portee.launch {
                                    val err = Moteur.envoyerImage(octets, mime, c)
                                    envoi = false
                                    if (err == null) { imageCollee = null; afficher("Image envoyée") } else afficher("Échec : $err")
                                }
                            }, enabled = !envoi, colors = ButtonDefaults.buttonColors(containerColor = c.source.teinte())) {
                                if (envoi) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                else Icon(Icons.AutoMirrored.Outlined.Send, "Envoyer l'image")
                            }
                        }
                    }
                    if (imageCollee == null && imageDispo && Moteur.imagePossible(c)) Text("📋 Coller l'image copiée",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte(),
                        modifier = Modifier.background(c.source.teinte().copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .clickable { collerImage() }.padding(horizontal = 12.dp, vertical = 8.dp))
                    if (vocal != 0) Row(
                        Modifier.fillMaxWidth().background(c.source.teinte().copy(alpha = 0.10f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val sec = (if (vocal == 1) tempsVocal else enregistreur.dureeMs) / 1000
                        if (vocal == 1) {
                            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                            Text("Enregistrement  ${sec / 60}:${"%02d".format(sec % 60)}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { enregistreur.annuler(); vocal = 0 }) { Text("Annuler") }
                            Button(onClick = { vocal = if (enregistreur.arreter()) 2 else 0; if (vocal == 0) afficher("Vocal trop court") },
                                colors = ButtonDefaults.buttonColors(containerColor = c.source.teinte())) { Text("Arrêter") }
                        } else {
                            IconButton(onClick = {
                                if (ecouteVocal) { enregistreur.arreterEcoute(); ecouteVocal = false }
                                else { ecouteVocal = true; runCatching { enregistreur.ecouter { ecouteVocal = false } } }
                            }) { Icon(if (ecouteVocal) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, if (ecouteVocal) "Arrêter l'écoute" else "Écouter") }
                            Text("Vocal  ${sec / 60}:${"%02d".format(sec % 60)}", modifier = Modifier.weight(1f))
                            IconButton(onClick = { enregistreur.annuler(); vocal = 0; ecouteVocal = false }) { Icon(Icons.Outlined.Delete, "Supprimer le vocal") }
                            Button(onClick = {
                                val f = enregistreur.fichier ?: return@Button
                                enregistreur.arreterEcoute(); ecouteVocal = false
                                envoi = true
                                portee.launch {
                                    val err = Moteur.envoyerVocal(f, enregistreur.typeMime, enregistreur.dureeMs, c)
                                    envoi = false
                                    if (err == null) { enregistreur.annuler(); vocal = 0; afficher("Vocal envoyé") } else afficher("Échec : $err")
                                }
                            }, enabled = !envoi, colors = ButtonDefaults.buttonColors(containerColor = c.source.teinte())) {
                                if (envoi) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                else Icon(Icons.AutoMirrored.Outlined.Send, "Envoyer le vocal")
                            }
                        }
                    } else {
                    // Suggestions de mention : « @ » suivi des premières lettres d'un membre
                    val avantCurseur = if (champ.text == brouillon) brouillon.take(champ.selection.start.coerceIn(0, brouillon.length)) else brouillon
                    val saisieMention = Regex("""(?:^|\s)@([^\s@]{0,24})$""").find(avantCurseur)?.groupValues?.get(1)
                    val proposes = if (saisieMention == null || membresMention.isEmpty()) emptyList() else {
                        val q = sansAccents(saisieMention)
                        membresMention.filter { mb ->
                            q.isEmpty() || sansAccents(mb.libelle).split(' ', '-').any { it.startsWith(q) } || (mb.numero?.contains(q) == true)
                        }.take(6)
                    }
                    val aCorriger = if (fautes.first == brouillon) fautes.second.filter { it.suggestions.isNotEmpty() }.take(4) else emptyList()
                    if (aCorriger.isNotEmpty() && proposes.isEmpty()) Row(
                        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Orthographe :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        aCorriger.forEach { f ->
                            f.suggestions.take(2).forEach { sug ->
                                Text(buildString { append(f.mot); append(" → "); append(sug) }, fontSize = 13.sp,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (f.fin <= brouillon.length && brouillon.substring(f.debut, f.fin) == f.mot) {
                                                val nouveau = brouillon.substring(0, f.debut) + sug + brouillon.substring(f.fin)
                                                val curseur = (champ.selection.start + sug.length - f.mot.length).coerceIn(0, nouveau.length)
                                                brouillon = nouveau
                                                champ = androidx.compose.ui.text.input.TextFieldValue(nouveau, androidx.compose.ui.text.TextRange(if (champ.selection.start >= f.fin) curseur else champ.selection.start))
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                    if (proposes.isNotEmpty()) Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(vertical = 4.dp),
                    ) {
                        proposes.forEach { mb ->
                            Row(Modifier.fillMaxWidth().height(48.dp).clickable {
                                val debut = avantCurseur.length - saisieMention!!.length - 1
                                val apres = brouillon.substring(avantCurseur.length)
                                val insere = "@" + mb.libelle + " "
                                val nouveau = brouillon.substring(0, debut) + insere + apres
                                brouillon = nouveau
                                champ = androidx.compose.ui.text.input.TextFieldValue(nouveau, androidx.compose.ui.text.TextRange(debut + insere.length))
                                mentions = mentions + (mb.libelle to mb.id)
                            }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(mb.libelle, c.source, false, taille = 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(mb.libelle, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (mb.nom != null && mb.numero != null) Text("+" + mb.numero, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val valeurChamp = if (champ.text == brouillon) champ
                            else androidx.compose.ui.text.input.TextFieldValue(brouillon, androidx.compose.ui.text.TextRange(brouillon.length))
                        val fautesActuelles = if (fautes.first == brouillon) fautes.second else emptyList()
                        OutlinedTextField(
                            value = valeurChamp, onValueChange = { champ = it; brouillon = it.text }, enabled = !iaEnCours,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                autoCorrectEnabled = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                            // Fautes soulignées en rouge
                            visualTransformation = if (fautesActuelles.isEmpty()) androidx.compose.ui.text.input.VisualTransformation.None
                                else androidx.compose.ui.text.input.VisualTransformation { texte ->
                                    val souligne = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.error,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                                    val a = androidx.compose.ui.text.buildAnnotatedString {
                                        append(texte)
                                        fautesActuelles.forEach { f -> if (f.fin <= texte.length) addStyle(souligne, f.debut, f.fin) }
                                    }
                                    androidx.compose.ui.text.input.TransformedText(a, androidx.compose.ui.text.input.OffsetMapping.Identity)
                                },
                            placeholder = { Text(if (c.source == Source.SMS) "Écrire un SMS…" else "Écrire une réponse…") },
                            minLines = if (saisieAgrandie) 8 else 2, maxLines = if (saisieAgrandie) 16 else 8,
                            trailingIcon = {
                                IconButton(onClick = { saisieAgrandie = !saisieAgrandie }) {
                                    Icon(if (saisieAgrandie) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                                        if (saisieAgrandie) "Réduire la zone de saisie" else "Agrandir la zone de saisie", modifier = Modifier.size(20.dp))
                                }
                            },
                            shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f),
                        )
                        if (Moteur.cfg.ollamaConfigure) Box {
                            OutlinedButton(onClick = { menuIA = true }, enabled = brouillon.isNotBlank() && !iaEnCours,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                                if (iaEnCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.AutoAwesome, "Améliorer avec l'IA")
                            }
                            DropdownMenu(expanded = menuIA, onDismissRequest = { menuIA = false }) {
                                Ollama.Facon.entries.forEach { f ->
                                    DropdownMenuItem(text = { Text(f.libelle) }, onClick = {
                                        menuIA = false
                                        val avant = brouillon
                                        iaEnCours = true
                                        portee.launch {
                                            val r = Moteur.ameliorer(avant, f, c)
                                            iaEnCours = false
                                            if (r != null) { versionsIA = versionsIA + avant; brouillon = r } else afficher("L'IA n'a pas répondu")
                                        }
                                    })
                                }
                            }
                        }
                        if (brouillon.isBlank() && Moteur.vocalPossible(c)) Button(
                            onClick = {
                                val ok = androidx.core.content.ContextCompat.checkSelfPermission(ctxVocal, android.Manifest.permission.RECORD_AUDIO) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (ok) runCatching { enregistreur.demarrer(); vocal = 1 }.onFailure { afficher("Micro indisponible") }
                                else demandeMicro.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = c.source.teinte()),
                        ) { Icon(Icons.Outlined.Mic, "Enregistrer un vocal") }
                        else Button(
                            onClick = {
                                envoi = true
                                portee.launch {
                                    val err = Moteur.envoyer(brouillon, c, reponseA, mentions)
                                    envoi = false
                                    if (err == null) {
                                        brouillon = ""; versionsIA = emptyList(); reponseA = null; saisieAgrandie = false; mentions = emptyMap()
                                        afficher(if (c.source == Source.SMS) "SMS envoyé" else "Message envoyé")
                                    } else afficher("Échec : $err")
                                }
                            },
                            enabled = brouillon.isNotBlank() && !envoi,
                            colors = ButtonDefaults.buttonColors(containerColor = c.source.teinte()),
                        ) {
                            if (envoi) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.AutoMirrored.Outlined.Send, "Envoyer")
                        }
                    }
                    }
                }
            }
        }
    }
    if (menu) FeuilleActions(e, c) { menu = false }
    if (infos) FeuilleInfos(e, c, historique, fermer = { infos = false },
        appeler = { menuAppel = true }, rechercher = { rechercheOuverte = true }, selectionner = { modeSelection = true },
        message = { afficher(it) })
    if (transfert) FeuilleTransfert(e, selection.size, fermer = { transfert = false }) { d -> transfert = false; destination = d }
    destination?.let { d ->
        val n = selection.size
        AlertDialog(
            onDismissRequest = { destination = null },
            title = { Text("Transférer à ${d.nom} ?") },
            text = { Text("$n message" + (if (n > 1) "s seront envoyés" else " sera envoyé") + " dans cette conversation ${d.source.libelle}.") },
            confirmButton = {
                TextButton(onClick = {
                    destination = null
                    val choisis = historique.filter { it.cle in selection }
                    transfertEnCours = true
                    portee.launch {
                        val (faits, err) = Moteur.transferer(choisis, d)
                        transfertEnCours = false
                        quitterSelection()
                        afficher(when {
                            err == null -> "$faits message" + (if (faits > 1) "s transférés" else " transféré") + " à ${d.nom}"
                            faits > 0 -> "$faits transféré(s), mais : $err"
                            else -> "Échec : $err"
                        })
                    }
                }) { Text("Transférer") }
            },
            dismissButton = { TextButton(onClick = { destination = null }) { Text("Annuler") } },
        )
    }
}

/** Minuscules sans accents (pour comparer les noms). */
private fun sansAccents(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "").lowercase()

private fun copier(ctx: Context, texte: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", texte))
}

private val emojisRapides = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "✅", "👀")

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Bulle(
    m: Message, c: Conversation, afficherAuteur: Boolean,
    reactions: List<Reaction>, maReaction: MaReaction?,
    repondre: () -> Unit, copier: (Context) -> Unit, reagir: (String?) -> Unit,
    afficherMessage: (String) -> Unit = {},
    luParContact: Long = 0,
    modeSelection: Boolean = false, choisi: Boolean = false, basculer: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val moi = m.deMoi
    // Mon émoji : celui posé dans l'appli, sinon une réaction « de moi » venue d'un autre appareil.
    val monEmoji = maReaction?.emoji ?: reactions.firstOrNull { it.deMoi }?.emoji
    Column(Modifier.fillMaxWidth().padding(top = if (afficherAuteur) 6.dp else 1.dp),
        horizontalAlignment = if (moi) Alignment.End else Alignment.Start) {
        if (c.estGroupe && !moi && afficherAuteur) {
            Text(m.auteur, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte(), modifier = Modifier.padding(start = 10.dp, bottom = 2.dp))
        }
        val forme = if (moi) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(if (moi) c.source.teinte() else MaterialTheme.colorScheme.surfaceContainerLow, forme)
                .then(if (moi) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, forme))
                .combinedClickable(onClick = { if (modeSelection) basculer() }, onLongClick = { if (modeSelection) basculer() else menu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val couleur = if (moi) Color.White else MaterialTheme.colorScheme.onSurface
            when (m.type) {
                "audio" -> if (m.media != null) LecteurVocal(m, couleur) else
                    Text("🎤 Message vocal" + (m.duree?.let { d -> " · " + minSec(d.toInt() * 1000) } ?: ""),
                        color = couleur, fontStyle = FontStyle.Italic)
                "image" -> PhotoMessage(m, couleur)
                "video" -> VideoMessage(m, couleur, afficherMessage)
                "fichier" -> FichierMessage(m, couleur, afficherMessage)
                else -> Text(avecLiens(m.texte, couleur), color = couleur)
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(heure(m.date), fontSize = 10.sp, color = couleur.copy(alpha = 0.7f))
                // Statut de vos messages : ✓ envoyé, ✓✓ lu par le contact (si la messagerie le transmet)
                if (moi && c.source != Source.TEAMS) {
                    val lu = luParContact >= m.date && luParContact > 0
                    Spacer(Modifier.width(3.dp))
                    Text(if (lu) "✓✓" else "✓", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (lu) Color(0xFF7FD4FF) else couleur.copy(alpha = 0.7f),
                        modifier = Modifier.semantics { contentDescription = if (lu) "Lu" else "Envoyé" })
                }
            }
        }
        // Réactions sous la bulle
        val autres = reactions.filter { !it.deMoi }
        if (autres.isNotEmpty() || monEmoji != null) {
            val groupes = LinkedHashMap<String, Int>()
            autres.forEach { groupes[it.emoji] = (groupes[it.emoji] ?: 0) + 1 }
            if (monEmoji != null) groupes[monEmoji] = (groupes[monEmoji] ?: 0) + 1
            FlowRow(Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                groupes.forEach { (emoji, n) ->
                    val aMoi = emoji == monEmoji
                    Text(
                        emoji + if (n > 1) " $n" else "", fontSize = 13.sp,
                        modifier = Modifier
                            .background(if (aMoi) c.source.teinte().copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .border(1.dp, if (aMoi) c.source.teinte() else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { menu = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
    if (menu) FeuilleMessage(m, c, monEmoji, fermer = { menu = false },
        reagir = { menu = false; reagir(it) }, repondre = { menu = false; repondre() }, copier = { menu = false; copier(ctx) },
        selectionner = { menu = false; basculer() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMessage(m: Message, c: Conversation, monEmoji: String?, fermer: () -> Unit,
                           reagir: (String?) -> Unit, repondre: () -> Unit, copier: () -> Unit, selectionner: () -> Unit) {
    ModalBottomSheet(onDismissRequest = fermer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Réagir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                emojisRapides.forEach { emoji ->
                    val choisi = emoji == monEmoji
                    Box(
                        Modifier.size(42.dp)
                            .background(if (choisi) c.source.teinte().copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            .clickable { reagir(if (choisi) null else emoji) },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 24.sp) }
                }
            }
            Text(
                if (Moteur.reactionTransmissible(m, c)) "Votre réaction sera envoyée au contact."
                else "Votre réaction restera sur ce téléphone.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (monEmoji != null) ElementAction(Icons.Outlined.DeleteOutline, "Retirer ma réaction $monEmoji") { reagir(null) }
            ElementAction(Icons.AutoMirrored.Outlined.Reply, "Répondre") { repondre() }
            if (m.type != "audio") ElementAction(Icons.Outlined.ContentCopy, "Copier le texte") { copier() }
            ElementAction(Icons.Outlined.Checklist, "Sélectionner (plusieurs messages)") { selectionner() }
        }
    }
}

/** Lecteur intégré à la bulle d'un message vocal. */
@Composable
private fun LecteurVocal(m: Message, couleur: Color) {
    val etat by Lecteur.etat.collectAsState()
    val actif = etat.cle == m.cle
    val dureeMs = if (actif && etat.duree > 0) etat.duree else ((m.duree ?: 0.0) * 1000).toInt()
    val position = if (actif) etat.position else 0
    Column(Modifier.width(220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(couleur.copy(alpha = 0.15f), CircleShape).clickable { Lecteur.basculer(m) },
                contentAlignment = Alignment.Center) {
                when {
                    actif && etat.chargement -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = couleur)
                    actif && etat.enLecture -> Icon(Icons.Outlined.Pause, "Pause", tint = couleur)
                    else -> Icon(Icons.Outlined.PlayArrow, "Écouter", tint = couleur)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                var largeur by remember { mutableIntStateOf(1) }
                val fraction = if (dureeMs > 0) (position.toFloat() / dureeMs).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .onSizeChanged { largeur = maxOf(1, it.width) }
                        .pointerInput(m.cle, actif) {
                            detectTapGestures { p -> if (actif) Lecteur.aller(p.x / largeur) }
                        },
                    color = couleur, trackColor = couleur.copy(alpha = 0.25f),
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    (if (actif && position > 0) minSec(position) + " / " else "🎤 ") + (if (dureeMs > 0) minSec(dureeMs) else "Message vocal"),
                    fontSize = 12.sp, color = couleur.copy(alpha = 0.85f),
                )
            }
        }
        if (actif && etat.erreur != null) Text(etat.erreur!!, fontSize = 11.sp, color = couleur, fontStyle = FontStyle.Italic)
    }
}

private fun minSec(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun CarteResume(e: Etat, c: Conversation, utiliser: (String) -> Unit) {
    if (!Moteur.cfg.ollamaConfigure) return
    var deplie by rememberSaveable(c.cle) { mutableStateOf(false) }
    val s = e.synthese(c)
    val enCours = c.cle in e.resumesEnCours
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp)
            .border(1.dp, degradeIA, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { deplie = !deplie }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (deplie) "▾ " else "▸ ") + "✨ Résumé IA", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (enCours) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            if (e.aTraiter(c)) Text("Réponse attendue", fontSize = 11.sp, color = orangeAttente, fontWeight = FontWeight.SemiBold)
        }
        if (deplie) {
            Spacer(Modifier.size(8.dp))
            when {
                s != null -> {
                    Text(s.resume)
                    if (s.reponse.isNotEmpty()) TextButton(onClick = { utiliser(s.reponse) }) { Text("Utiliser : « ${s.reponse} »", maxLines = 2) }
                }
                enCours -> Text("L'IA lit la conversation…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> OutlinedButton(onClick = { Moteur.regenerer(c) }) { Text("Résumer cette conversation") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Généré par Ollama sur votre serveur · peut contenir des erreurs", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (s != null && !enCours) TextButton(onClick = { Moteur.regenerer(c) }) { Text("Refaire", fontSize = 12.sp) }
            }
        }
    }
}

/** Ouvre l'appli Teams sur la discussion (lien profond vers le dernier message), sinon l'appli seule. */
internal fun ouvrirTeams(ctx: android.content.Context, conversation: String, dernierMessage: String?) {
    val paquet = "com.microsoft.teams"
    if (dernierMessage != null && conversation.isNotEmpty()) {
        val lien = android.net.Uri.parse("https://teams.microsoft.com/l/message/" + android.net.Uri.encode(conversation) + "/" +
            android.net.Uri.encode(dernierMessage) + "?context=" + android.net.Uri.encode("{\"contextType\":\"chat\"}"))
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, lien).setPackage(paquet)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return
    }
    val i = ctx.packageManager.getLaunchIntentForPackage(paquet)
        ?: android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://teams.microsoft.com"))
    runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Moyens d'appeler ce contact : l'appel se fait dans l'appli concernée (Messagerie ne passe pas d'appel). */
private fun moyensAppel(c: Conversation): List<Pair<String, String>> {
    val l = ArrayList<Pair<String, String>>()
    // Numéro : identifiant WhatsApp (numéro@s.whatsapp.net) ou nom de la conversation s'il ressemble à un numéro
    val numero = when {
        c.source == Source.WHATSAPP && c.jid.endsWith("@s.whatsapp.net") -> c.jid.substringBefore('@').substringBefore(':')
        else -> c.nom.filter { it.isDigit() || it == '+' }.takeIf { it.count(Char::isDigit) >= 8 && c.nom.none { ch -> ch.isLetter() } }
    }?.trimStart('+')
    when (c.source) {
        Source.WHATSAPP -> if (numero != null) l += "Appel WhatsApp (vidéo ou audio)" to "https://wa.me/$numero" else l += "Ouvrir WhatsApp" to "appli:com.whatsapp"
        Source.TELEGRAM -> l += "Appel Telegram (ouvrir l'appli)" to "appli:org.telegram.messenger"
        Source.TEAMS -> l += "Appel Teams (ouvrir la discussion)" to "teams:${c.jid}"
        Source.LINKEDIN -> l += "Ouvrir LinkedIn" to "appli:com.linkedin.android"
        Source.ELEMENT -> l += "Appel Element (ouvrir l'appli)" to "appli:im.vector.app"
        else -> {}
    }
    if (numero != null) l += "Appel téléphonique" to "tel:+$numero"
    l += "Nouvelle visio Google Meet" to "https://meet.google.com/new"
    return l
}

private fun lancerAppel(ctx: Context, lien: String) {
    val nt = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    when {
        lien.startsWith("appli:") -> {
            val i = ctx.packageManager.getLaunchIntentForPackage(lien.removePrefix("appli:"))
            if (i == null) android.widget.Toast.makeText(ctx, "Appli non installée", android.widget.Toast.LENGTH_SHORT).show()
            else runCatching { ctx.startActivity(i.addFlags(nt)) }
        }
        lien.startsWith("teams:") -> ouvrirTeams(ctx, lien.removePrefix("teams:"), null)
        lien.startsWith("tel:") -> runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse(lien)).addFlags(nt)) }
        else -> runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(lien)).addFlags(nt)) }
    }
}

private val LIEN = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+[^\s<>"'.,;:!?)\]}])""")

/** Texte avec liens web cliquables (soulignés), ouverts dans le navigateur. */
private fun avecLiens(t: String, couleur: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        for (r in LIEN.findAll(t)) {
            append(t.substring(i, r.range.first))
            val url = r.value.let { if (it.startsWith("www.", true)) "https://$it" else it }
            val style = androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = couleur,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
            val hote = runCatching { android.net.Uri.parse(url).host }.getOrNull()
            val jitsi = hote != null && (hote == "meet.jit.si" || hote == runCatching { android.net.Uri.parse(Moteur.cfg.jitsi).host }.getOrNull())
            // Lien d'appel Jitsi : l'appel s'ouvre dans l'appli ; les autres liens dans le navigateur
            if (jitsi) withLink(androidx.compose.ui.text.LinkAnnotation.Clickable("jitsi", style) {
                fr.messagerie.unifiee.AppelJitsi.ouvrir(Moteur.app, url)
            }) { append(r.value) }
            else withLink(androidx.compose.ui.text.LinkAnnotation.Url(url, style)) { append(r.value) }
            i = r.range.last + 1
        }
        append(t.substring(i))
    }
