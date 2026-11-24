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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.draw.clip
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
    /** auteur = premier du bloc (même expéditeur, à moins de 5 min) ; dernier = fin du bloc. */
    class Msg(val m: Message, val auteur: Boolean, val dernier: Boolean = true) : Ligne(m.cle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranConversation(e: Etat, c: Conversation, vm: ConversationVM, retour: () -> Unit,
    rechercheInitiale: String? = null, messageCible: String? = null, ouvrir: (String) -> Unit = {}) {
    val portee = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val historique by vm.historique.collectAsState()
    val total by vm.total.collectAsState()
    val chargement by vm.chargement.collectAsState()
    val reactions by vm.reactions.collectAsState()
    val mesReactions by vm.mesReactions.collectAsState()
    // Brouillon gardé sur le téléphone : on le retrouve en revenant dans la conversation
    var brouillon by rememberSaveable(c.cle) { mutableStateOf(Moteur.brouillon(c.cle)) }
    // Message en cours de modification (le vôtre) : le champ contient son texte, « Enregistrer » le remplace
    var enModification by remember(c.cle) { mutableStateOf<Message?>(null) }
    LaunchedEffect(brouillon, enModification) {
        if (enModification != null) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        Moteur.garderBrouillon(c.cle, brouillon)
    }
    val vote by vm.votes.collectAsState()
    val favoris by vm.favoris.collectAsState()
    var listeFavoris by remember { mutableStateOf(false) }
    // Messages épinglés et messages éphémères (lus sur le serveur, rien n'est envoyé)
    val infosSalon by androidx.compose.runtime.produceState<Moteur.InfosSalon?>(null, c.cle) { value = Moteur.infosSalon(c) }
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
    var pieceJointe by remember(c.cle) { mutableStateOf<PieceJointe?>(null) }
    var menuJoindre by remember { mutableStateOf(false) }
    var imageDispo by remember { mutableStateOf(false) }
    var reponseA by remember(c.cle) { mutableStateOf<Message?>(null) }
    var envoi by remember { mutableStateOf(false) }
    var iaEnCours by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var menuIA by remember { mutableStateOf(false) }
    var reponsesRapides by remember { mutableStateOf(false) }
    var menuAppel by remember { mutableStateOf(false) }
    var menuPlus by remember { mutableStateOf(false) }
    // « En train d'écrire » : reçu seulement, l'appli n'envoie jamais le sien
    var ecrivent by remember(c.cle) { mutableStateOf(listOf<String>()) }
    LaunchedEffect(c.cle) { Moteur.suivreSaisie(c) { ecrivent = it } }
    var infos by remember { mutableStateOf(false) }
    // Sélection de plusieurs messages (copier, transférer)
    var selection by remember(c.cle) { mutableStateOf(setOf<String>()) }
    var modeSelection by remember(c.cle) { mutableStateOf(false) }
    var transfert by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<Conversation?>(null) }
    var transfertEnCours by remember { mutableStateOf(false) }
    var aSupprimer by remember { mutableStateOf<Set<String>?>(null) }
    fun quitterSelection() { modeSelection = false; selection = emptySet() }
    fun basculerSelection(m: Message) {
        modeSelection = true
        selection = if (m.cle in selection) selection - m.cle else selection + m.cle
        if (selection.isEmpty()) modeSelection = false
    }
    androidx.activity.compose.BackHandler(enabled = modeSelection) { quitterSelection() }
    // Recherche dans la discussion
    var rechercheOuverte by remember(c.cle) { mutableStateOf(rechercheInitiale != null) }
    var requete by remember(c.cle) { mutableStateOf(rechercheInitiale.orEmpty()) }
    // Ouvert depuis la recherche globale : mots déjà cherchés, puis le message choisi
    var cibleRecherche by remember(c.cle) { mutableStateOf(messageCible) }
    LaunchedEffect(c.cle) { if (rechercheInitiale != null) vm.chercher(rechercheInitiale) }
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
        // Une notification affichée en bulle reste : la retirer fermerait la bulle
        fr.messagerie.unifiee.Bulles.retirerNotification(Moteur.app, c.cle.hashCode())
        onDispose { if (Moteur.convOuverte == c.cle) Moteur.convOuverte = null }
    }   // lu dans l'appli seulement : aucun accusé envoyé
    LaunchedEffect(c.derniere) {
        vm.actualiser(c.messages)
        if (!e.estForcee(c)) Moteur.marquerLue(c)
    }

    // Contact connu = conversation où vous avez déjà écrit : aperçu automatique des liens seulement là
    val contactConnu = remember(historique, c.messages) { historique.any { it.deMoi } || c.messages.any { it.deMoi } }
    val lignes = remember(historique, luAvant) {
        val l = ArrayList<Ligne>()
        var nonLusPlace = false
        historique.forEachIndexed { i, m ->
            val prec = historique.getOrNull(i - 1)
            if (prec == null || jourComplet(prec.date) != jourComplet(m.date)) l.add(Ligne.Jour(m.date))
            val nouveauJour = prec == null || jourComplet(prec.date) != jourComplet(m.date)
            var marque = false
            if (!nonLusPlace && luAvant != null && !m.deMoi && m.date > luAvant) { l.add(Ligne.NonLus); nonLusPlace = true; marque = true }
            val suite = prec != null && !nouveauJour && !marque && prec.deMoi == m.deMoi && prec.auteur == m.auteur && m.date - prec.date < 300_000
            l.add(Ligne.Msg(m, !suite))
        }
        // Fin de bloc : la ligne suivante n'est pas un message, ou commence un autre bloc
        for (i in l.indices) {
            val x = l[i] as? Ligne.Msg ?: continue
            val n = l.getOrNull(i + 1)
            if (n is Ligne.Msg && !n.auteur) l[i] = Ligne.Msg(x.m, x.auteur, dernier = false)
        }
        l
    }

    fun afficher(t: String) { portee.launch { snack.showSnackbar(t) } }
    val lignesActuelles by androidx.compose.runtime.rememberUpdatedState(lignes)
    /** Fait défiler jusqu'à un message (cité, épinglé, favori), même ancien. */
    fun allerAuMessage(cle: String) {
        portee.launch {
            val m = historique.firstOrNull { it.cle == cle }
                ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Moteur.archive.messageParCle(cle) }
                ?: run { afficher("Message introuvable (pas encore récupéré)"); return@launch }
            courant = m.cle
            vm.allerA(m) { portee.launch {
                kotlinx.coroutines.delay(120)
                val idx = lignesActuelles.asReversed().indexOfFirst { it is Ligne.Msg && it.m.cle == m.cle }
                if (idx >= 0) liste.animateScrollToItem(idx + 1)
                kotlinx.coroutines.delay(2500)
                if (courant == m.cle && !rechercheOuverte) courant = null
            } }
        }
    }

    // Pièces jointes : photo / vidéo, fichier, contact. Rien ne part avant l'appui sur « Envoyer » de l'aperçu.
    fun chargerFichier(uri: android.net.Uri) {
        runCatching {
            val cr = ctxVocal.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            var nom = "fichier"; var taille = -1L
            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) { nom = it.getString(0) ?: nom; if (!it.isNull(1)) taille = it.getLong(1) }
            }
            if (taille > 50_000_000) error("fichier trop lourd (50 Mo au plus)")
            val octets = cr.openInputStream(uri)?.use { it.readBytes() } ?: error("fichier illisible")
            if (octets.size > 50_000_000) error("fichier trop lourd (50 Mo au plus)")
            if (mime.startsWith("image/") && mime != "image/svg+xml") imageCollee = octets to mime
            else pieceJointe = PieceJointe(octets, mime, nom)
        }.onFailure { afficher("Impossible de joindre le fichier : ${it.message}") }
    }
    fun chargerContact(uri: android.net.Uri) {
        runCatching {
            val cr = ctxVocal.contentResolver
            var id = ""; var cleRecherche = ""; var nom = "Contact"
            cr.query(uri, arrayOf(android.provider.ContactsContract.Contacts._ID, android.provider.ContactsContract.Contacts.LOOKUP_KEY,
                android.provider.ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) { id = it.getString(0) ?: ""; cleRecherche = it.getString(1) ?: ""; nom = it.getString(2) ?: nom }
            }
            if (cleRecherche.isEmpty()) error("contact introuvable")
            val numeros = mutableListOf<String>()
            cr.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf(id), null)?.use {
                while (it.moveToNext()) it.getString(0)?.let { n -> if (numeros.none { x -> x.filter(Char::isDigit) == n.filter(Char::isDigit) }) numeros += n }
            }
            val vcf = android.net.Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_VCARD_URI, cleRecherche)
            val octets = cr.openAssetFileDescriptor(vcf, "r")?.use { it.createInputStream().use { s -> s.readBytes() } } ?: error("fiche illisible")
            val texte = (listOf(nom) + numeros).joinToString("\n")
            pieceJointe = PieceJointe(octets, "text/vcard", nom.replace(Regex("""[\\/:*?"<>|]"""), "") + ".vcf", texte)
        }.onFailure { afficher("Impossible de joindre le contact : ${it.message}") }
    }
    val choisirMedia = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { it?.let { u -> chargerFichier(u) } }
    val choisirFichier = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { it?.let { u -> chargerFichier(u) } }
    val choisirContact = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickContact()) { it?.let { u -> chargerContact(u) } }
    val permissionContacts = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) choisirContact.launch(null) else afficher("Autorisez l'accès aux contacts pour en joindre un")
    }
    fun joindreContact() {
        val ok = androidx.core.content.ContextCompat.checkSelfPermission(ctxVocal, android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (ok) choisirContact.launch(null) else permissionContacts.launch(android.Manifest.permission.READ_CONTACTS)
    }

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
                    IconButton(onClick = { aSupprimer = selection }, enabled = choisis.isNotEmpty()) { Icon(Icons.Outlined.DeleteOutline, "Supprimer") }
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
                        if (ecrivent.isNotEmpty()) Text(
                            when {
                                !c.estGroupe -> "en train d'écrire…"
                                ecrivent.size == 1 -> "${ecrivent[0]} écrit…"
                                ecrivent.size == 2 -> "${ecrivent[0]} et ${ecrivent[1]} écrivent…"
                                else -> "${ecrivent.size} personnes écrivent…"
                            },
                            style = MaterialTheme.typography.labelMedium, color = c.source.teinte(), fontStyle = FontStyle.Italic,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else Text(e.libelleSource(c) + (if (c.estGroupe) " · groupe" else "") +
                            when (fr.messagerie.unifiee.Presences.etat.collectAsState().value[c.cle]) { "online" -> " · en ligne"; "unavailable" -> " · absent"; else -> "" },
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
                        LaunchedEffect(trouves) {
                            if (trouves.isEmpty()) { courant = null; return@LaunchedEffect }
                            val i = cibleRecherche?.let { m -> trouves.indexOfFirst { it.cle == m } } ?: -1
                            cibleRecherche = null
                            aller(if (i >= 0) trouves.size - 1 - i else 0)
                        }
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
                            DropdownMenuItem(text = { Text("Messages favoris") }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null) },
                                onClick = { menuPlus = false; listeFavoris = true })
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
            if (!rechercheOuverte && !modeSelection) AussiSur(e, c, ouvrir)
            if (!rechercheOuverte && !modeSelection) BandeauSalon(infosSalon, historique, c) { allerAuMessage(it) }
            // Anti-spam : alerte sur une conversation suspecte (score ≥ 30), avec le pourquoi
            e.risques[c.cle]?.takeIf { !it.manuel || it.estSpam }?.let { v -> AlerteSpam(v) { spam -> Moteur.marquerSpam(c, spam) } }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = liste, reverseLayout = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                item(key = "resume") { CarteResume(e, c) { brouillon = it } }
                items(lignes.asReversed(), key = { it.cle }) { l ->
                    when (l) {
                        is Ligne.Jour -> Text(jourComplet(l.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Ligne.NonLus -> Text("— Nouveaux messages —", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Ligne.Msg -> androidx.compose.runtime.CompositionLocalProvider(LocalContactConnu provides contactConnu) { Row(Modifier.fillMaxWidth().background(
                            when {
                                l.m.cle in selection -> c.source.teinte().copy(alpha = 0.14f)
                                l.m.cle == courant -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                else -> Color.Transparent
                            }, RoundedCornerShape(14.dp))
                            .then(if (modeSelection) Modifier.clickable { basculerSelection(l.m) } else Modifier)
                            .padding(top = if (l.auteur) 10.dp else 0.dp),
                            verticalAlignment = Alignment.Bottom) {
                            if (modeSelection) { Spacer(Modifier.width(4.dp)); PastilleSelection(l.m.cle in selection, c.source.teinte()); Spacer(Modifier.width(6.dp)) }
                            // Groupe : pastille de l'expéditeur au bas de son bloc de messages
                            if (c.estGroupe && !l.m.deMoi) {
                                Box(Modifier.padding(bottom = 2.dp).size(30.dp)) { if (l.dernier) PastilleAuteur(l.m.auteur) }
                                Spacer(Modifier.width(6.dp))
                            }
                            Box(Modifier.weight(1f)) { Bulle(l.m, c, l.auteur, dernier = l.dernier,
                            reactions = reactions[l.m.cle].orEmpty(), maReaction = mesReactions[l.m.cle],
                            repondre = { reponseA = l.m },
                            copier = { copier(it, l.m.texteUtile); afficher("Texte copié") },
                            reagir = { emoji -> vm.reagir(l.m, c, emoji) { afficher(it) } },
                            afficherMessage = { afficher(it) },
                            luParContact = e.lusParContacts[c.cle] ?: 0L,
                            modeSelection = modeSelection, choisi = l.m.cle in selection,
                            basculer = { basculerSelection(l.m) },
                            supprimer = { aSupprimer = setOf(l.m.cle) },
                            votes = vote[l.m.cle].orEmpty(),
                            voter = { choix -> vm.voter(l.m, c, choix) { afficher(it) } },
                            favori = l.m.cle in favoris,
                            basculerFavori = { vm.basculerFavori(l.m); afficher(if (l.m.cle in favoris) "Retiré des favoris" else "Ajouté aux favoris") },
                            allerA = { allerAuMessage(it) },
                            modifier = if (Moteur.modifiable(l.m, c)) ({ enModification = l.m; reponseA = null; brouillon = l.m.texte }) else null) } } }
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
                    enModification?.let { m ->
                        Row(Modifier.fillMaxWidth().background(c.source.teinte().copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Edit, null, tint = c.source.teinte())
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Modifier le message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte())
                                Text(m.texte, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            }
                            IconButton(onClick = { enModification = null; brouillon = Moteur.brouillon(c.cle) }) { Icon(Icons.Outlined.Close, "Annuler la modification") }
                        }
                    }
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
                    // Fichier ou contact joint : aperçu, puis envoi sur appui
                    pieceJointe?.let { pj ->
                        Row(Modifier.fillMaxWidth().background(c.source.teinte().copy(alpha = 0.10f), RoundedCornerShape(14.dp)).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(48.dp).background(c.source.teinte().copy(alpha = 0.18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(when {
                                    pj.contact != null -> Icons.Outlined.Person
                                    pj.mime.startsWith("video/") -> Icons.Outlined.Movie
                                    else -> Icons.Outlined.Description
                                }, null, tint = c.source.teinte())
                            }
                            Column(Modifier.weight(1f)) {
                                Text(pj.contact?.substringBefore('\n') ?: pj.nom, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (pj.contact != null) "Fiche contact" + pj.contact.lines().drop(1).firstOrNull()?.let { " · $it" }.orEmpty()
                                    else if (pj.octets.size >= 1_048_576) "%.1f Mo".format(pj.octets.size / 1_048_576.0) else "${pj.octets.size / 1024} Ko",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                if (pj.contact != null) Text("Mettre en texte", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte(),
                                    modifier = Modifier.clickable {
                                        brouillon = (if (brouillon.isBlank()) "" else brouillon.trimEnd() + "\n") + pj.contact
                                        pieceJointe = null
                                    }.padding(vertical = 4.dp))
                            }
                            IconButton(onClick = { pieceJointe = null }) { Icon(Icons.Outlined.Close, "Retirer la pièce jointe") }
                            Button(onClick = {
                                envoi = true
                                portee.launch {
                                    val err = Moteur.envoyerFichier(pj.octets, pj.mime, pj.nom, c)
                                    envoi = false
                                    if (err == null) { pieceJointe = null; afficher(if (pj.contact != null) "Contact envoyé" else "Fichier envoyé") } else afficher("Échec : $err")
                                }
                            }, enabled = !envoi, colors = ButtonDefaults.buttonColors(containerColor = c.source.teinte())) {
                                if (envoi) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                else Icon(Icons.AutoMirrored.Outlined.Send, "Envoyer la pièce jointe")
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
                        val rougeFaute = MaterialTheme.colorScheme.error
                        // Barre de saisie d'une ligne (comme Messages) : trombone et IA dans la pastille, qui grandit avec le texte
                        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 2.dp)) {
                                if (Moteur.imagePossible(c)) Box {
                                    IconButton(onClick = { menuJoindre = true }) { Icon(Icons.Outlined.AddCircleOutline, "Joindre", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    DropdownMenu(expanded = menuJoindre, onDismissRequest = { menuJoindre = false }) {
                                        DropdownMenuItem(text = { Text("Photo ou vidéo") }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, onClick = {
                                            menuJoindre = false
                                            choisirMedia.launch(androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                        })
                                        DropdownMenuItem(text = { Text("Fichier") }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, onClick = {
                                            menuJoindre = false; choisirFichier.launch(arrayOf("*/*"))
                                        })
                                        DropdownMenuItem(text = { Text("Contact") }, leadingIcon = { Icon(Icons.Outlined.Person, null) }, onClick = {
                                            menuJoindre = false; joindreContact()
                                        })
                                    }
                                } else Spacer(Modifier.width(14.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = valeurChamp, onValueChange = { champ = it; brouillon = it.text }, enabled = !iaEnCours,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.source.teinte()),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                        autoCorrectEnabled = true, keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
                                    // Fautes soulignées en rouge
                                    visualTransformation = if (fautesActuelles.isEmpty()) androidx.compose.ui.text.input.VisualTransformation.None
                                        else androidx.compose.ui.text.input.VisualTransformation { texte ->
                                            val souligne = androidx.compose.ui.text.SpanStyle(color = rougeFaute,
                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                                            val a = androidx.compose.ui.text.buildAnnotatedString {
                                                append(texte)
                                                fautesActuelles.forEach { f -> if (f.fin <= texte.length) addStyle(souligne, f.debut, f.fin) }
                                            }
                                            androidx.compose.ui.text.input.TransformedText(a, androidx.compose.ui.text.input.OffsetMapping.Identity)
                                        },
                                    minLines = if (saisieAgrandie) 8 else 1, maxLines = if (saisieAgrandie) 16 else 6,
                                    modifier = Modifier.weight(1f).padding(vertical = 13.dp),
                                    decorationBox = { champTexte ->
                                        Box {
                                            if (brouillon.isEmpty()) Text(if (c.source == Source.SMS) "Message SMS" else "Message ${c.source.id}",
                                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            champTexte()
                                        }
                                    },
                                )
                                // Texte long : agrandir / réduire la zone
                                if (saisieAgrandie || brouillon.length > 120 || brouillon.count { it == '\n' } >= 3)
                                    IconButton(onClick = { saisieAgrandie = !saisieAgrandie }) {
                                        Icon(if (saisieAgrandie) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                                            if (saisieAgrandie) "Réduire la zone de saisie" else "Agrandir la zone de saisie", modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                // Champ vide : réponses rapides ; sinon, améliorer avec l'IA
                                if (brouillon.isBlank() && !iaEnCours) IconButton(onClick = { reponsesRapides = true }) {
                                    Icon(Icons.Outlined.Bolt, "Réponses rapides", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else if (Moteur.cfg.ollamaConfigure) Box {
                                    IconButton(onClick = { menuIA = true }, enabled = brouillon.isNotBlank() && !iaEnCours) {
                                        if (iaEnCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Outlined.AutoAwesome, "Améliorer avec l'IA", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            }
                        }
                        // Bouton rond : micro quand le champ est vide, sinon envoyer
                        val micro = brouillon.isBlank() && enModification == null && Moteur.vocalPossible(c)
                        Box(Modifier.size(52.dp).clip(CircleShape).background(if (micro || brouillon.isNotBlank()) c.source.teinte() else c.source.teinte().copy(alpha = 0.4f))
                            .clickable(enabled = micro || (brouillon.isNotBlank() && !envoi)) {
                                if (micro) {
                                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(ctxVocal, android.Manifest.permission.RECORD_AUDIO) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (ok) runCatching { enregistreur.demarrer(); vocal = 1 }.onFailure { afficher("Micro indisponible") }
                                    else demandeMicro.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else if (enModification != null) {
                                    val m = enModification!!
                                    envoi = true
                                    portee.launch {
                                        val err = Moteur.modifier(m, brouillon, c)
                                        envoi = false
                                        if (err == null) {
                                            enModification = null; brouillon = Moteur.brouillon(c.cle); versionsIA = emptyList(); saisieAgrandie = false
                                            afficher("Message modifié")
                                        } else afficher("Échec : $err")
                                    }
                                } else {
                                    envoi = true
                                    portee.launch {
                                        val err = Moteur.envoyer(brouillon, c, reponseA, mentions)
                                        envoi = false
                                        if (err == null) {
                                            brouillon = ""; versionsIA = emptyList(); reponseA = null; saisieAgrandie = false; mentions = emptyMap()
                                            afficher(if (c.source == Source.SMS) "SMS envoyé" else "Message envoyé")
                                        } else afficher("Échec : $err")
                                    }
                                }
                            }, contentAlignment = Alignment.Center) {
                            if (envoi) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            else if (enModification != null && !micro) Icon(Icons.Outlined.Check, "Enregistrer la modification", tint = Color.White)
                            else Icon(if (micro) Icons.Outlined.Mic else Icons.AutoMirrored.Outlined.Send, if (micro) "Enregistrer un vocal" else "Envoyer", tint = Color.White)
                        }
                    }
                    }
                }
            }
        }
    }
    if (menu) FeuilleActions(e, c) { menu = false }
    if (listeFavoris) DialogueFavoris(c, fermer = { listeFavoris = false }) { listeFavoris = false; allerAuMessage(it) }
    if (reponsesRapides) FeuilleReponsesRapides(c, fermer = { reponsesRapides = false }) { t -> brouillon = t }
    if (infos) FeuilleInfos(e, c, historique, fermer = { infos = false },
        appeler = { menuAppel = true }, rechercher = { rechercheOuverte = true }, selectionner = { modeSelection = true },
        message = { afficher(it) }, apresSuppression = retour, ouvrir = ouvrir)
    // Suppression de messages : dans Messagerie seulement, après confirmation
    aSupprimer?.let { cles ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text(if (cles.size > 1) "Supprimer ${cles.size} messages ?" else "Supprimer ce message ?") },
            text = {
                val tous = historique.filter { it.cle in cles }
                val partout = tous.count { Moteur.supprimablePourTous(it, c) }
                Text(when {
                    partout == tous.size -> "« Pour tout le monde » le supprime aussi chez vos contacts et dans ${c.source.libelle} (définitif)."
                    partout > 0 -> "$partout de vos messages peuvent être supprimés pour tout le monde ; les autres seulement dans Messagerie."
                    else -> "Seuls vos propres messages peuvent être supprimés pour tout le monde : ici, la suppression se fera dans Messagerie seulement."
                })
            },
            confirmButton = {
                val choisis = historique.filter { it.cle in cles }
                Column(horizontalAlignment = Alignment.End) {
                    if (choisis.any { Moteur.supprimablePourTous(it, c) }) TextButton(onClick = {
                        aSupprimer = null; quitterSelection()
                        portee.launch {
                            val (n, err) = Moteur.supprimerPourTous(choisis, c)
                            // Ceux qui ne pouvaient pas l'être partout : dans Messagerie seulement
                            val reste = choisis.filter { !Moteur.supprimablePourTous(it, c) }.map { it.cle }
                            if (reste.isNotEmpty()) Moteur.supprimerMessages(reste)
                            kotlinx.coroutines.delay(400); vm.actualiser(c.messages)
                            afficher(when {
                                err == null -> "$n message" + (if (n > 1) "s supprimés" else " supprimé") + " pour tout le monde"
                                n > 0 -> "$n supprimé(s) pour tout le monde, mais : $err"
                                else -> "Échec : $err"
                            })
                        }
                    }) { Text("Pour tout le monde", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = {
                        aSupprimer = null; quitterSelection()
                        Moteur.supprimerMessages(cles.toList())
                        portee.launch { kotlinx.coroutines.delay(400); vm.actualiser(c.messages) }
                        afficher(if (cles.size > 1) "${cles.size} messages supprimés de Messagerie" else "Message supprimé de Messagerie")
                    }) { Text("Dans Messagerie seulement") }
                    TextButton(onClick = { aSupprimer = null }) { Text("Annuler") }
                }
            },
        )
    }
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

/** Couleur propre à chaque expéditeur d'un groupe (nom et pastille), lisible en clair comme en sombre. */
private val COULEURS_AUTEURS = listOf(0xFF1E88E5, 0xFFD81B60, 0xFF8E24AA, 0xFF43A047, 0xFFF4511E, 0xFF00897B, 0xFF6D4C41, 0xFF3949AB, 0xFFC0CA33, 0xFF5E35B1)
private fun couleurAuteur(nom: String) = Color(COULEURS_AUTEURS[Math.floorMod(nom.hashCode(), COULEURS_AUTEURS.size)])

@Composable
private fun PastilleAuteur(nom: String) {
    val initiale = nom.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(Modifier.size(30.dp).background(couleurAuteur(nom), CircleShape), contentAlignment = Alignment.Center) {
        Text(initiale, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private val emojisRapides = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "✅", "👀")

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun Bulle(
    m: Message, c: Conversation, afficherAuteur: Boolean, dernier: Boolean = true,
    reactions: List<Reaction>, maReaction: MaReaction?,
    repondre: () -> Unit, copier: (Context) -> Unit, reagir: (String?) -> Unit,
    afficherMessage: (String) -> Unit = {},
    luParContact: Long = 0,
    modeSelection: Boolean = false, choisi: Boolean = false, basculer: () -> Unit = {},
    supprimer: () -> Unit = {},
    votes: List<fr.messagerie.unifiee.Vote> = emptyList(),
    voter: (List<String>) -> Unit = {},
    favori: Boolean = false,
    basculerFavori: () -> Unit = {},
    allerA: (String) -> Unit = {},
    modifier: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val moi = m.deMoi
    // Mon émoji : celui posé dans l'appli, sinon une réaction « de moi » venue d'un autre appareil.
    val monEmoji = maReaction?.emoji ?: reactions.firstOrNull { it.deMoi }?.emoji
    Column(Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalAlignment = if (moi) Alignment.End else Alignment.Start) {
        if (c.estGroupe && !moi && afficherAuteur) {
            Text(m.auteur, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = couleurAuteur(m.auteur), modifier = Modifier.padding(start = 12.dp, bottom = 3.dp))
        }
        // Bloc de messages : coins resserrés du côté de l'expéditeur entre deux bulles du même bloc
        val haut = if (afficherAuteur) 18.dp else 5.dp
        val bas = if (dernier) 18.dp else 5.dp
        val forme = if (moi) RoundedCornerShape(18.dp, haut, bas, 18.dp) else RoundedCornerShape(haut, 18.dp, 18.dp, bas)
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(if (m.type == "autocollant") Color.Transparent else if (moi) c.source.teinte() else MaterialTheme.colorScheme.surfaceContainerHigh, forme)
                .combinedClickable(onClick = { if (modeSelection) basculer() else ouvrirLien(m.texte, ctx) }, onLongClick = { if (modeSelection) basculer() else menu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val couleur = if (moi && m.type != "autocollant") Color.White else MaterialTheme.colorScheme.onSurface
            // Réponse : le message cité, touché = on y remonte
            m.cite?.let { k -> Citation(k, moi, couleur, c) { allerA(k) } }
            when (m.type) {
                "autocollant" -> AutocollantMessage(m, couleur)
                "position" -> PositionMessage(m, couleur)
                "sondage" -> m.sondage?.let { CarteSondage(it, votes, couleur, Moteur.votePossible(c), voter) } ?: Text(m.texte, color = couleur)
                "audio" -> if (m.media != null) LecteurVocal(m, couleur) else
                    Text("🎤 Message vocal" + (m.duree?.let { d -> " · " + minSec(d.toInt() * 1000) } ?: ""),
                        color = couleur, fontStyle = FontStyle.Italic)
                "image" -> PhotoMessage(m, couleur)
                "video" -> VideoMessage(m, couleur, afficherMessage)
                "fichier" -> FichierMessage(m, couleur, afficherMessage)
                else -> {
                    Text(m.html?.let { texteMisEnForme(it, m.texte, couleur) } ?: avecLiens(m.texte, couleur), color = couleur)
                    // Aperçu du premier lien (titre, description, image de la page)
                    // Contact connu : aperçu chargé tout de suite ; inconnu : domaine bien visible, aperçu seulement à la demande
                    LIEN.find(m.texte)?.let { ApercuLien(urlComplete(it.value), moi, couleur, LocalContactConnu.current) }
                }
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (favori) { Icon(Icons.Outlined.Star, "Favori", tint = couleur.copy(alpha = 0.8f), modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                if (m.modifie) { Text("modifié · ", fontSize = 10.sp, fontStyle = FontStyle.Italic, color = couleur.copy(alpha = 0.7f)) }
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
        // Réactions de tous (contacts et vous, y compris depuis les applis officielles) : emoji → auteurs
        val autres = reactions.filter { !it.deMoi }.distinctBy { it.auteur + it.emoji }
        if (autres.isNotEmpty() || monEmoji != null) {
            val auteurs = LinkedHashMap<String, MutableList<String>>()
            if (monEmoji != null) auteurs.getOrPut(monEmoji) { ArrayList() }.add("Vous")
            autres.forEach { auteurs.getOrPut(it.emoji) { ArrayList() }.add(it.auteur) }
            val groupes = auteurs.mapValues { it.value.size }
            var detailReactions by remember { mutableStateOf(false) }
            if (detailReactions) AlertDialog(
                onDismissRequest = { detailReactions = false },
                title = { Text("Réactions") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        auteurs.forEach { (emoji, qui) ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(emoji, fontSize = 22.sp, modifier = Modifier.width(40.dp))
                                Text(qui.joinToString(", "), fontSize = 15.sp,
                                    fontWeight = if ("Vous" in qui) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { detailReactions = false; menu = true }) { Text(if (monEmoji != null) "Changer ma réaction" else "Réagir") }
                },
                dismissButton = { TextButton(onClick = { detailReactions = false }) { Text("Fermer") } },
            )
            FlowRow(Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                groupes.forEach { (emoji, n) ->
                    val aMoi = emoji == monEmoji
                    Text(
                        emoji + if (n > 1) " $n" else "", fontSize = 13.sp,
                        modifier = Modifier
                            .background(if (aMoi) c.source.teinte().copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .border(1.dp, if (aMoi) c.source.teinte() else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { detailReactions = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
    if (menu) FeuilleMessage(m, c, monEmoji, fermer = { menu = false },
        reagir = { menu = false; reagir(it) }, repondre = { menu = false; repondre() }, copier = { menu = false; copier(ctx) },
        selectionner = { menu = false; basculer() }, supprimer = { menu = false; supprimer() },
        favori = favori, basculerFavori = { menu = false; basculerFavori() },
        modifier = modifier?.let { f -> { menu = false; f() } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMessage(m: Message, c: Conversation, monEmoji: String?, fermer: () -> Unit,
                           reagir: (String?) -> Unit, repondre: () -> Unit, copier: () -> Unit, selectionner: () -> Unit, supprimer: () -> Unit,
                           favori: Boolean = false, basculerFavori: () -> Unit = {}, modifier: (() -> Unit)? = null) {
    var partie by remember { mutableStateOf(false) }
    if (partie) { DialogueSelectionTexte(m.texte) { partie = false; fermer() }; return }
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
            if (modifier != null) ElementAction(Icons.Outlined.Edit, "Modifier") { modifier() }
            ElementAction(if (favori) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                if (favori) "Retirer des favoris" else "Ajouter aux favoris") { basculerFavori() }
            if (m.type != "audio") ElementAction(Icons.Outlined.ContentCopy, "Copier tout le texte") { copier() }
            if (m.type != "audio" && m.texte.isNotBlank()) ElementAction(Icons.Outlined.TextFields, "Copier une partie du texte") { partie = true }
            ElementAction(Icons.Outlined.Checklist, "Sélectionner (plusieurs messages)") { selectionner() }
            ElementAction(Icons.Outlined.DeleteOutline, "Supprimer") { supprimer() }
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
                // Vitesse de lecture (1×, 1,5×, 2×), gardée d'un vocal à l'autre
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (actif && position > 0) minSec(position) + " / " else "🎤 ") + (if (dureeMs > 0) minSec(dureeMs) else "Message vocal"),
                        fontSize = 12.sp, color = couleur.copy(alpha = 0.85f), modifier = Modifier.weight(1f),
                    )
                    Text(when (etat.vitesse) { 1.5f -> "1,5×"; 2f -> "2×"; else -> "1×" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = couleur,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(couleur.copy(alpha = 0.15f))
                            .clickable { Lecteur.changerVitesse() }.padding(horizontal = 8.dp, vertical = 3.dp)
                            .semantics { contentDescription = "Vitesse de lecture" })
                }
            }
        }
        if (actif && etat.erreur != null) Text(etat.erreur!!, fontSize = 11.sp, color = couleur, fontStyle = FontStyle.Italic)
        // Transcription (sur le téléphone ; rien n'est envoyé au contact)
        Moteur.versionTranscriptions.collectAsState().value
        val transcrit = Moteur.transcriptionConnue(m)
        val portee = rememberCoroutineScope()
        var enCours by remember(m.cle) { mutableStateOf(false) }
        var erreur by remember(m.cle) { mutableStateOf<String?>(null) }
        fun lancer() {
            enCours = true; erreur = null
            portee.launch {
                runCatching { fr.messagerie.unifiee.Transcripteur.transcrire(m) }
                    .onSuccess { t -> if (t.isBlank()) erreur = "Aucune parole reconnue" else Moteur.noterTranscription(m, t) }
                    .onFailure { erreur = "Transcription impossible : " + (it.message ?: it.toString()) }
                enCours = false
            }
        }
        val micro = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) lancer() else erreur = "Autorisez le micro pour transcrire" }
        val ctx = LocalContext.current
        when {
            transcrit != null -> Text("« $transcrit »", fontSize = 13.sp, color = couleur, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 6.dp))
            enCours -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = couleur)
                Spacer(Modifier.width(6.dp)); Text("Transcription…", fontSize = 12.sp, color = couleur)
            }
            fr.messagerie.unifiee.Transcripteur.possible -> Text("Transcrire", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = couleur,
                modifier = Modifier.padding(top = 4.dp).clickable {
                    val ok = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (ok) lancer() else micro.launch(android.Manifest.permission.RECORD_AUDIO)
                }.padding(horizontal = 4.dp, vertical = 6.dp))
        }
        erreur?.let { Text(it, fontSize = 11.sp, color = couleur, fontStyle = FontStyle.Italic) }
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
    val tel = Moteur.numeroTelephone(c)?.filter { it.isDigit() || it == '+' }?.takeIf { it.count(Char::isDigit) >= 6 }
    // wa.me demande le numéro international : seulement l'identifiant WhatsApp ou un numéro en +
    val numero = c.jid.substringBefore('@').substringBefore(':').takeIf { c.source == Source.WHATSAPP && c.jid.endsWith("@s.whatsapp.net") }
        ?: tel?.takeIf { it.startsWith("+") }?.trimStart('+')
    // Appel par le téléphone en premier : ouvre le composeur, rien n'est composé automatiquement
    if (tel != null) l += "Appel téléphonique ($tel)" to "tel:$tel"
    when (c.source) {
        Source.WHATSAPP -> if (numero != null) l += "Appel WhatsApp (vidéo ou audio)" to "https://wa.me/$numero" else l += "Ouvrir WhatsApp" to "appli:com.whatsapp"
        Source.TELEGRAM -> l += "Appel Telegram (ouvrir l'appli)" to "appli:org.telegram.messenger"
        Source.TEAMS -> l += "Appel Teams (ouvrir la discussion)" to "teams:${c.jid}"
        Source.LINKEDIN -> l += "Ouvrir LinkedIn" to "appli:com.linkedin.android"
        Source.SIGNAL -> l += "Appel Signal (ouvrir l'appli)" to "appli:org.thoughtcrime.securesms"
        Source.ELEMENT -> l += "Appel Element (ouvrir l'appli)" to "appli:im.vector.app"
        else -> {}
    }
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

// Liens avec http(s):// ou www., et aussi sans schéma (« my.dpd.fr/47yabsol2 ») quand le domaine finit par une extension connue
private val LIEN = Regex("""(?i)(?<![@\w.\-/])((?:https?://|www\.)[^\s<>"']*[^\s<>"'.,;:!?)\]}]|(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:fr|com|net|org|eu|be|ch|io|co|me|app|info|biz|de|uk|es|it|nl|lu|ca|us|ly|gl|gle|link|page|dev|xyz|shop|store|online|site|tv|re|mq|gp|pf|nc)\b(?::\d+)?(?:/[^\s<>"']*[^\s<>"'.,;:!?)\]}])?/?)""")

/** Adresse complète d'un lien trouvé dans le texte (https:// ajouté s'il manque). */
private fun urlComplete(brut: String) = if (Regex("(?i)^https?://").containsMatchIn(brut)) brut else "https://$brut"

/** Texte avec liens web cliquables (soulignés), ouverts dans le navigateur. */
/** Appui sur une bulle qui contient un lien : ouvre le premier lien (appel Jitsi dans l'appli, sinon navigateur). */
private fun ouvrirLien(t: String, ctx: Context) {
    val brut = LIEN.find(t)?.value ?: return
    val url = urlComplete(brut)
    val hote = runCatching { android.net.Uri.parse(url).host }.getOrNull()
    if (hote != null && (hote == "meet.jit.si" || hote == runCatching { android.net.Uri.parse(Moteur.cfg.jitsi).host }.getOrNull()))
        fr.messagerie.unifiee.AppelJitsi.ouvrir(Moteur.app, url)
    else runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}

private fun avecLiens(t: String, couleur: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        for (r in LIEN.findAll(t)) {
            append(t.substring(i, r.range.first))
            val url = urlComplete(r.value)
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

/** Pièce jointe en attente d'envoi ; [contact] = nom et numéros quand c'est une fiche contact. */
private class PieceJointe(val octets: ByteArray, val mime: String, val nom: String, val contact: String? = null)

private val LocalContactConnu = androidx.compose.runtime.compositionLocalOf { false }

/** Carte d'aperçu d'un lien sous le texte de la bulle ; rien ne s'affiche si la page ne donne ni titre ni image. */
@Composable
private fun ApercuLien(url: String, moi: Boolean, couleur: Color, auto: Boolean) {
    var demande by remember(url) { mutableStateOf(auto) }
    var essaye by remember(url) { mutableStateOf(false) }
    val apercu by androidx.compose.runtime.produceState(fr.messagerie.unifiee.ApercusLiens.enCache(url), url, demande) {
        if (value == null && demande) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fr.messagerie.unifiee.ApercusLiens.charger(url) }
            essaye = true
        }
    }
    val site = remember(url) { runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url }
    val a = apercu
    if (a == null) {
        // Domaine bien lisible (repérer un faux site) ; l'aperçu ne se charge que sur appui
        Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (moi) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .then(if (!demande) Modifier.clickable { demande = true } else Modifier).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("🔗 ", fontSize = 12.sp)
            Text(site, color = couleur, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Text(when { !demande -> "  · Aperçu"; essaye -> "  · pas d'aperçu"; else -> "  · …" }, color = couleur.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        return
    }
    val ctx = LocalContext.current
    val fond = if (moi) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
    Column(Modifier.padding(top = 8.dp).widthIn(max = 276.dp).clip(RoundedCornerShape(12.dp)).background(fond)
        .clickable { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(a.url))) } }) {
        a.image?.let {
            coil3.compose.AsyncImage(model = it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp))
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            a.titre?.let { Text(it, color = couleur, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            a.description?.let { Text(it, color = couleur.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Text(a.site, color = couleur.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

/** Bandeau anti-spam en haut d'une conversation suspecte : raisons, puis « Spam » / « Pas spam » (qui apprennent). */
@Composable
private fun AlerteSpam(v: fr.messagerie.unifiee.Spam.Verdict, marquer: (Boolean) -> Unit) {
    val rouge = v.estSpam
    val fond = if (rouge) Color(0xFFFDE2E1) else Color(0xFFFDEEDC)
    val texte = if (rouge) Color(0xFF8A1C1C) else Color(0xFF7A3A08)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).background(fond, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(if (v.manuel) "⚠️ Vous avez marqué cette conversation comme spam"
            else if (rouge) "⚠️ Spam probable (risque ${v.score} %)" else "⚠️ Message à vérifier (risque ${v.score} %)",
            color = texte, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        if (v.raisons.isNotEmpty()) Text(v.raisons.joinToString(" · ").replaceFirstChar { it.uppercase() }, color = texte, fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp))
        if (!v.manuel) Text("N'ouvrez pas le lien et ne donnez aucune information si vous avez un doute.", color = texte.copy(alpha = 0.85f), fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!v.estSpam || !v.manuel) TextButton(onClick = { marquer(true) }) { Text("C'est un spam", color = texte, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = { marquer(false) }) { Text("Ce n'est pas un spam", color = texte) }
        }
    }
}

/**
 * Texte du message sélectionnable : appui long sur un mot puis poignées pour choisir la partie à copier.
 * Les blocs de code (entre ```) sont en police à chasse fixe, défilables, avec leur propre bouton « Copier le code ».
 */
@Composable
private fun DialogueSelectionTexte(texte: String, fermer: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Découpage en morceaux : texte normal / bloc de code
    val morceaux = remember(texte) {
        val res = ArrayList<Pair<Boolean, String>>()
        var reste = texte
        val bloc = Regex("```[a-zA-Z0-9_+-]*\\n?([\\s\\S]*?)```")
        var debut = 0
        bloc.findAll(texte).forEach { r ->
            if (r.range.first > debut) res += false to texte.substring(debut, r.range.first).trim('\n')
            res += true to r.groupValues[1].trimEnd('\n')
            debut = r.range.last + 1
        }
        if (debut < texte.length) res += false to texte.substring(debut).trim('\n')
        res.filter { it.second.isNotBlank() }.ifEmpty { listOf(false to reste) }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = fermer,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 600.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Copier une partie", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Appui long sur un mot, ajustez avec les poignées, puis « Copier ».", fontSize = 13.sp, color = cs.onSurfaceVariant)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    morceaux.forEach { (code, t) ->
                        if (!code) androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(t, fontSize = 16.sp, lineHeight = 22.sp)
                        } else Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLowest, RoundedCornerShape(12.dp))
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Code", fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                                TextButton(onClick = { copier(ctx, t) }) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copier le code")
                                }
                            }
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(t, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, softWrap = false,
                                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { copier(ctx, texte); fermer() }) { Text("Tout copier") }
                    TextButton(onClick = fermer) { Text("Fermer") }
                }
            }
        }
    }
}


/** Bloc du message cité en haut d'une réponse reçue ou envoyée. */
@Composable
private fun Citation(cle: String, moi: Boolean, couleur: Color, c: Conversation, aller: () -> Unit) {
    val cite by androidx.compose.runtime.produceState<Message?>(null, cle) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Moteur.archive.messageParCle(cle) }
    }
    val accent = if (moi) Color.White else c.source.teinte()
    Row(
        Modifier.padding(bottom = 6.dp).widthIn(min = 120.dp).clip(RoundedCornerShape(8.dp))
            .background(couleur.copy(alpha = 0.10f)).clickable { aller() },
    ) {
        Box(Modifier.width(3.dp).height(42.dp).background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(cite?.auteur ?: "Message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
            Text(cite?.let { apercuMessage(it) } ?: "Message plus ancien", fontSize = 13.sp, color = couleur.copy(alpha = 0.85f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Texte reçu mis en forme (gras, italique, barré, code, citation), liens cliquables. */
private fun texteMisEnForme(html: String, brut: String, couleur: Color): androidx.compose.ui.text.AnnotatedString {
    val (texte, passages) = fr.messagerie.unifiee.MiseEnForme.lire(html) ?: return avecLiens(brut, couleur)
    val base = avecLiens(texte, couleur)
    return androidx.compose.ui.text.AnnotatedString.Builder(base).apply {
        for (p in passages) {
            val style = when (p.style) {
                fr.messagerie.unifiee.MiseEnForme.Style.GRAS -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                fr.messagerie.unifiee.MiseEnForme.Style.ITALIQUE -> androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
                fr.messagerie.unifiee.MiseEnForme.Style.BARRE -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                fr.messagerie.unifiee.MiseEnForme.Style.CODE -> androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    background = couleur.copy(alpha = 0.12f))
                fr.messagerie.unifiee.MiseEnForme.Style.CITATION -> androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic, color = couleur.copy(alpha = 0.75f))
            }
            if (p.fin <= texte.length) addStyle(style, p.debut, p.fin)
        }
    }.toAnnotatedString()
}

/** Sondage : question, réponses avec le nombre de votes ; toucher une réponse vote (seulement sur votre appui). */
@Composable
private fun CarteSondage(s: fr.messagerie.unifiee.Sondage, votes: List<fr.messagerie.unifiee.Vote>, couleur: Color, peutVoter: Boolean,
                         voter: (List<String>) -> Unit) {
    val mesChoix = votes.firstOrNull { it.deMoi }?.choix.orEmpty()
    var aConfirmer by remember { mutableStateOf<List<String>?>(null) }
    var voirVotants by remember { mutableStateOf(false) }
    val total = votes.count { it.choix.isNotEmpty() }
    Column(Modifier.widthIn(min = 220.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("📊 " + s.question, color = couleur, fontWeight = FontWeight.SemiBold)
        Text(if (s.max > 1) "Plusieurs réponses possibles" else "Une seule réponse", fontSize = 11.sp, color = couleur.copy(alpha = 0.7f))
        s.reponses.forEach { (id, texte) ->
            val n = votes.count { id in it.choix }
            val choisi = id in mesChoix
            val part = if (total > 0) n.toFloat() / total else 0f
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(couleur.copy(alpha = 0.08f))
                    .border(1.dp, if (choisi) couleur else couleur.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable(enabled = peutVoter) {
                        aConfirmer = if (s.max > 1) (if (choisi) mesChoix - id else (mesChoix + id).takeLast(s.max)) else listOf(id)
                    },
            ) {
                Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(part).background(couleur.copy(alpha = 0.16f))) }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (choisi) "✓ " else "", color = couleur, fontWeight = FontWeight.Bold)
                    Text(texte, color = couleur, modifier = Modifier.weight(1f))
                    Text("$n", color = couleur, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("$total vote" + (if (total > 1) "s" else "") + (if (total > 0) " · voir qui" else "") +
            (if (!peutVoter) " · vote impossible d'ici" else ""), fontSize = 11.sp, color = couleur.copy(alpha = 0.75f),
            modifier = Modifier.clickable(enabled = total > 0) { voirVotants = true })
    }
    aConfirmer?.let { choix ->
        AlertDialog(
            onDismissRequest = { aConfirmer = null },
            title = { Text(if (choix.isEmpty()) "Retirer votre vote ?" else "Voter ?") },
            text = { Text(if (choix.isEmpty()) "Votre vote sera retiré du sondage."
                else "Votre vote (" + choix.mapNotNull { id -> s.reponses.firstOrNull { it.first == id }?.second }.joinToString(", ") + ") sera visible par les participants.") },
            confirmButton = { TextButton(onClick = { aConfirmer = null; voter(choix) }) { Text(if (choix.isEmpty()) "Retirer" else "Voter") } },
            dismissButton = { TextButton(onClick = { aConfirmer = null }) { Text("Annuler") } },
        )
    }
    if (voirVotants) AlertDialog(
        onDismissRequest = { voirVotants = false },
        title = { Text("Votes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.reponses.forEach { (id, texte) ->
                    val qui = votes.filter { id in it.choix }.map { it.auteur }
                    Text(texte + " (" + qui.size + ")", fontWeight = FontWeight.SemiBold)
                    if (qui.isNotEmpty()) Text(qui.joinToString(", "), fontSize = 13.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { voirVotants = false }) { Text("Fermer") } },
    )
}

/** Bandeaux en haut de la conversation : message épinglé (le plus récent) et messages éphémères. */
@Composable
private fun BandeauSalon(infos: Moteur.InfosSalon?, historique: List<Message>, c: Conversation, aller: (String) -> Unit) {
    if (infos == null) return
    val cs = MaterialTheme.colorScheme
    infos.ephemere?.let { ms ->
        val duree = when {
            ms >= 86_400_000L * 7 && ms % (86_400_000L * 7) == 0L -> "${ms / (86_400_000L * 7)} semaine" + if (ms / (86_400_000L * 7) > 1) "s" else ""
            ms >= 86_400_000L -> "${ms / 86_400_000L} jour" + if (ms / 86_400_000L > 1) "s" else ""
            ms >= 3_600_000L -> "${ms / 3_600_000L} h"
            ms >= 60_000L -> "${ms / 60_000L} min"
            else -> "${ms / 1000} s"
        }
        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, null, tint = c.source.teinte(), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Messages éphémères : effacés après $duree" + (if (infos.apresLecture) " (après lecture)" else ""),
                fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
    val cle = infos.epingles.lastOrNull() ?: return
    val m by androidx.compose.runtime.produceState(historique.firstOrNull { it.cle == cle }, cle) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Moteur.archive.messageParCle(cle) }
    }
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { aller(cle) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.PushPin, null, tint = c.source.teinte(), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(if (infos.epingles.size > 1) "${infos.epingles.size} messages épinglés" else "Message épinglé",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte())
            Text(m?.let { (if (it.deMoi) "Vous : " else if (c.estGroupe) it.auteur + " : " else "") + apercuMessage(it) } ?: "Message plus ancien",
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Messages favoris de la conversation (gardés sur ce téléphone). */
@Composable
private fun DialogueFavoris(c: Conversation, fermer: () -> Unit, aller: (String) -> Unit) {
    val liste by androidx.compose.runtime.produceState<List<Message>?>(null, c.cle) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Moteur.archive.favoris(c.cle) }
    }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text("Messages favoris") },
        text = {
            val l = liste
            when {
                l == null -> CircularProgressIndicator(Modifier.size(24.dp))
                l.isEmpty() -> Text("Aucun favori. Appui long sur un message > Ajouter aux favoris.")
                else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    l.forEach { m ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { aller(m.cle) }.padding(10.dp)) {
                            Text(m.auteur + " · " + jourComplet(m.date), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte())
                            Text(apercuMessage(m), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = fermer) { Text("Fermer") } },
    )
}
