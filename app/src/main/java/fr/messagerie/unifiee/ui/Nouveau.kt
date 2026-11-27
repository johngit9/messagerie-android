package fr.messagerie.unifiee.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.Source
import fr.messagerie.unifiee.Moteur
import kotlinx.coroutines.launch
import java.text.Normalizer

/** Un contact = ses discussions à deux, toutes messageries confondues. */
private class Contact(val nom: String, val convs: List<Conversation>) {
    val derniere = convs.maxOf { it.derniere }
}

private fun normaliser(s: String) = Normalizer.normalize(s.replace(" (WA)", ""), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "").lowercase().trim()

/** Numéro saisi (au moins 9 chiffres) au format international sans « + » : 06… → 336… */
private fun numero(q: String): String? {
    val chiffres = q.filter { it.isDigit() }
    if (chiffres.length < 9 || q.any { it.isLetter() }) return null
    return if (chiffres.startsWith("0") && chiffres.length == 10) "33" + chiffres.drop(1) else chiffres
}

/** Numéro du carnet au format international avec « + » : 06… → +336…, 0044… → +44… */
private fun international(num: String): String {
    val chiffres = num.filter { it.isDigit() }
    return "+" + when {
        num.trim().startsWith("+") -> chiffres
        chiffres.startsWith("00") -> chiffres.drop(2)
        chiffres.startsWith("0") && chiffres.length == 10 -> "33" + chiffres.drop(1)
        else -> chiffres
    }
}

/** Nouveau message : choisir un contact (toutes messageries) ou saisir un numéro. Rien n'est envoyé ici. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranNouveau(e: Etat, retour: () -> Unit, ouvrir: (String) -> Unit) {
    val ctx = LocalContext.current
    var q by rememberSaveable { mutableStateOf("") }
    var filtre by rememberSaveable { mutableStateOf<String?>(null) }
    var choix by remember { mutableStateOf<Contact?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val sources = Source.entries.filter { s -> e.conversations.any { it.source == s } }
    val r = normaliser(q)
    val base = e.conversations.filter { c -> filtre == null || c.source.id == filtre }
    val contacts = remember(base, r) {
        base.filter { !it.estGroupe }.groupBy { normaliser(it.nom) }
            .filterKeys { r.isEmpty() || it.contains(r) }
            .map { (_, l) -> Contact(l.maxBy { it.derniere }.nom.replace(" (WA)", ""), l.sortedByDescending { it.derniere }) }
            .sortedByDescending { it.derniere }.take(120)
    }
    val groupes = remember(base, r) {
        base.filter { it.estGroupe && (r.isEmpty() || normaliser(it.nom).contains(r)) }.sortedByDescending { it.derniere }.take(60)
    }
    // Carnet du téléphone : les personnes sans discussion dans Messagerie (ex. jamais écrit depuis l'appli)
    var carnet by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) { carnet = Moteur.carnetTelephone() }
    var nomCarnet by rememberSaveable { mutableStateOf<String?>(null) }
    val fiches = remember(carnet, r, contacts, filtre) {
        if (r.length < 2 || filtre != null) emptyList()
        else {
            val connus = contacts.map { normaliser(it.nom) }.toSet()
            carnet.filter { (nom, _) -> normaliser(nom).let { it.contains(r) && it !in connus } }
                .groupBy { it.first }.map { (nom, l) -> nom to l.map { international(it.second) }.distinct() }
                .sortedBy { normaliser(it.first) }.take(30)
        }
    }
    // Element : personnes de vos groupes et de l'annuaire du serveur, sans discussion à deux
    var annuaire by remember { mutableStateOf<List<fr.messagerie.unifiee.Relais.Personne>>(emptyList()) }
    LaunchedEffect(q, filtre) {
        annuaire = emptyList()
        if (r.length < 2 || r.any { it.isDigit() } || (filtre != null && filtre != Source.ELEMENT.id) || !Moteur.cfg.relaisConfigure) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        annuaire = Moteur.chercherElement(q)
    }
    val personnes = remember(annuaire, contacts) {
        val connus = contacts.filter { k -> k.convs.any { it.source == Source.ELEMENT } }.map { normaliser(it.nom) }.toSet()
        annuaire.filter { normaliser(it.nom) !in connus }
    }
    var demandeElement by remember { mutableStateOf<fr.messagerie.unifiee.Relais.Personne?>(null) }
    val tel = numero(q)
    // Pseudo Telegram (@nom) : discussion ouverte par la passerelle Telegram
    val pseudoTelegram = Regex("^@[A-Za-z][A-Za-z0-9_]{4,31}$").matches(q.trim()) && Moteur.cfg.matrixConfigure
    val portee = androidx.compose.runtime.rememberCoroutineScope()
    var demande by remember { mutableStateOf<Pair<Source, String>?>(null) }
    var enCours by remember { mutableStateOf(false) }
    var resultat by remember { mutableStateOf<String?>(null) }
    // Nouveau groupe : messagerie, puis contacts, puis nom (création confirmée)
    var groupeSource by remember { mutableStateOf<Source?>(null) }
    var choixSourceGroupe by remember { mutableStateOf(false) }
    var groupeMembres by remember { mutableStateOf<List<Conversation>?>(null) }
    fun creerGroupe(s: Source, nom: String, membres: List<Conversation>) {
        enCours = true; resultat = null
        portee.launch {
            val (cle, texte) = Moteur.creerGroupe(s, nom, membres)
            enCours = false
            if (cle != null) ouvrir(cle) else resultat = texte
        }
    }
    fun demarrerElement(p: fr.messagerie.unifiee.Relais.Personne) {
        enCours = true; resultat = null
        portee.launch {
            val (cle, texte) = Moteur.nouvelleDiscussionElement(p)
            enCours = false
            if (cle != null) ouvrir(cle) else resultat = texte
        }
    }
    fun demarrer(s: Source, ident: String) {
        enCours = true; resultat = null
        portee.launch {
            val (cle, texte) = Moteur.nouvelleDiscussion(s, ident)
            enCours = false
            if (cle != null) ouvrir(cle) else resultat = texte
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Nouveau message") },
                navigationIcon = { IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        if (q.isEmpty()) Text("Nom ou numéro", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        BasicTextField(q, { q = it; nomCarnet = null }, singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus))
                    }
                    if (q.isNotEmpty()) IconButton(onClick = { q = ""; nomCarnet = null }) { Icon(Icons.Outlined.Close, "Effacer") }
                }
                if (sources.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Puce("Toutes", null, filtre == null) { filtre = null }
                    sources.forEach { s -> Puce(s.id, s, filtre == s.id) { filtre = if (filtre == s.id) null else s.id } }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tel != null) item(key = "numero") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Dialpad, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text((nomCarnet?.let { "$it · " } ?: "") + "+$tel", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (Moteur.cfg.matrixConfigure) {
                            Text("Écrire dans Messagerie avec :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(Source.WHATSAPP, Source.SIGNAL, Source.SMS, Source.TELEGRAM).forEach { s ->
                                    OutlinedButton(onClick = { demande = s to "+$tel" }, enabled = !enCours) {
                                        LogoSource(s, 20.dp); Spacer(Modifier.width(8.dp)); Text(s.id)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Text("Ou ouvrir l'appli officielle :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$tel"))) }
                            }) { Text("WhatsApp") }
                            androidx.compose.material3.TextButton(onClick = {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("sms:+$tel"))) }
                            }) { Text("Messages") }
                        }
                    }
                }
                if (q.isEmpty() && Moteur.cfg.matrixConfigure) item(key = "groupe") {
                    OutlinedButton(onClick = { choixSourceGroupe = true }, enabled = !enCours, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.GroupAdd, null); Spacer(Modifier.width(8.dp)); Text("Nouveau groupe")
                    }
                }
                if (pseudoTelegram) item(key = "pseudo") {
                    OutlinedButton(onClick = { demande = Source.TELEGRAM to q.trim() }, enabled = !enCours, modifier = Modifier.fillMaxWidth()) {
                        LogoSource(Source.TELEGRAM, 20.dp); Spacer(Modifier.width(8.dp)); Text("Écrire à ${q.trim()} sur Telegram")
                    }
                }
                if (enCours || resultat != null) item(key = "resultat") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (enCours) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                        Text(if (enCours) "La passerelle ouvre la discussion…" else resultat.orEmpty(), fontSize = 13.sp)
                    }
                }
                if (contacts.isNotEmpty()) item(key = "t-contacts") { Titre("Contacts") }
                items(contacts, key = { "c-" + it.nom + it.convs.first().cle }) { k ->
                    LigneContact(k.nom, k.convs.first(), k.convs.map { it.source }.distinct()) {
                        if (k.convs.size == 1) ouvrir(k.convs.first().cle) else choix = k
                    }
                }
                if (groupes.isNotEmpty()) item(key = "t-groupes") { Titre("Groupes") }
                items(groupes, key = { "g-" + it.cle }) { c -> LigneContact(c.nom, c, listOf(c.source)) { ouvrir(c.cle) } }
                if (personnes.isNotEmpty()) item(key = "t-element") { Titre("Element : vos groupes et l'annuaire") }
                items(personnes, key = { "e-" + it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                            .clickable(enabled = !enCours) { demandeElement = p }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LogoSource(Source.ELEMENT, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Pas encore de discussion à deux", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (fiches.isNotEmpty()) item(key = "t-carnet") { Titre("Carnet du téléphone") }
                items(fiches, key = { "f-" + it.first }) { (nom, numeros) ->
                    LigneCarnet(nom, numeros) { num -> q = num; nomCarnet = nom }
                }
                if (contacts.isEmpty() && groupes.isEmpty() && fiches.isEmpty() && personnes.isEmpty() && tel == null) item(key = "vide") {
                    Text("Aucun contact ne correspond. Tapez un numéro pour écrire par WhatsApp ou SMS.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    choix?.let { k ->
        ModalBottomSheet(onDismissRequest = { choix = null }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text("Écrire à ${k.nom} par…", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            k.convs.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { choix = null; ouvrir(c.cle) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LogoSource(c.source, 32.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(e.libelleSource(c), fontWeight = FontWeight.Medium)
                        Text("Dernier message : " + dateCourte(c.derniere), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (choixSourceGroupe) androidx.compose.material3.AlertDialog(
        onDismissRequest = { choixSourceGroupe = false },
        title = { Text("Nouveau groupe sur…") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Source.WHATSAPP, Source.SIGNAL, Source.TELEGRAM).filter { Moteur.groupesGerables(it) }.forEach { s ->
                OutlinedButton(onClick = { choixSourceGroupe = false; groupeSource = s }, modifier = Modifier.fillMaxWidth()) {
                    LogoSource(s, 20.dp); Spacer(Modifier.width(8.dp)); Text(s.libelle)
                }
            }
        } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { choixSourceGroupe = false }) { Text("Annuler") } },
    )
    groupeSource?.let { s ->
        if (groupeMembres == null) ChoixContacts(e, s, "Membres du groupe ${s.libelle}", multiple = true,
            fermer = { groupeSource = null }) { groupeMembres = it }
        else {
            var nom by remember { mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { groupeSource = null; groupeMembres = null },
                title = { Text("Nom du groupe") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(nom, { nom = it.take(100) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nom du groupe") })
                    Text("Le groupe sera créé dans ${s.libelle} avec " + groupeMembres!!.joinToString(", ") { it.nom } +
                        ". Ils verront qu'ils ont été ajoutés ; aucun message n'est envoyé.", fontSize = 13.sp)
                } },
                confirmButton = { androidx.compose.material3.TextButton(enabled = nom.isNotBlank(), onClick = {
                    val m = groupeMembres!!; groupeSource = null; groupeMembres = null; creerGroupe(s, nom, m)
                }) { Text("Créer le groupe") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { groupeSource = null; groupeMembres = null }) { Text("Annuler") } },
            )
        }
    }
    demandeElement?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { demandeElement = null },
            title = { Text("Discussion Element") },
            text = { Text("Ouvrir une discussion à deux avec ${p.nom} ? S'il n'y en a pas encore, il reçoit une invitation " +
                "à la discussion ; aucun message n'est envoyé : vous écrirez ensuite le premier message.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { demandeElement = null; demarrerElement(p) }) { Text("Ouvrir la discussion") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { demandeElement = null }) { Text("Annuler") } },
        )
    }
    demande?.let { (src, ident) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { demande = null },
            title = { Text("Nouvelle discussion ${src.libelle}") },
            text = { Text("Votre serveur demande à la passerelle ${src.libelle} d'ouvrir une discussion avec $ident. " +
                "Aucun message n'est envoyé au contact : vous écrirez ensuite le premier message.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { demande = null; demarrer(src, ident) }) { Text("Ouvrir la discussion") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { demande = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun Titre(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp))

@Composable
private fun Puce(libelle: String, s: Source?, choisie: Boolean, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(36.dp).background(if (choisie) cs.onSurface else cs.surfaceContainerLow, RoundedCornerShape(18.dp))
            .border(1.dp, if (choisie) cs.onSurface else cs.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = clic).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s != null) { LogoSource(s, 18.dp); Spacer(Modifier.width(6.dp)) }
        Text(libelle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (choisie) cs.surface else cs.onSurface)
    }
}

/** Fiche du carnet sans discussion : un appui sur un numéro propose WhatsApp, Signal, SMS ou Telegram. */
@Composable
private fun LigneCarnet(nom: String, numeros: List<String>, clic: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow, RoundedCornerShape(16.dp))) {
        numeros.forEachIndexed { i, num ->
            Row(
                Modifier.fillMaxWidth().clickable { clic(num) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).background(cs.surfaceContainerHigh, RoundedCornerShape(21.dp)), contentAlignment = Alignment.Center) {
                    if (i == 0) Icon(Icons.Outlined.ContactPhone, null, tint = cs.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (i == 0) Text(nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(num + if (i == 0) " · pas encore de discussion" else "", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LigneContact(nom: String, c: Conversation, sources: List<Source>, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(cs.surfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable(onClick = clic).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(nom, c.source, c.estGroupe, taille = 42.dp, bordure = cs.surfaceContainerLow, cle = c.cle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateCourte(c.derniere), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { sources.forEach { LogoSource(it, 20.dp) } }
    }
}
