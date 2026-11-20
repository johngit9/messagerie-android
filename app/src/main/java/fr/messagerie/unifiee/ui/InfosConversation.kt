package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.Message
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.launch

private val LIEN_WEB = Regex("""(?i)\b(?:https?://|www\.)\S+""")

/**
 * Fiche d'informations de la conversation (comme la fiche d'un groupe WhatsApp) :
 * actions rapides, médias, réglages, membres et « Quitter le groupe » (avec confirmation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleInfos(
    e: Etat, c: Conversation, historique: List<Message>, fermer: () -> Unit,
    appeler: () -> Unit, rechercher: () -> Unit, selectionner: () -> Unit, message: (String) -> Unit,
    apresSuppression: () -> Unit = {}, ouvrir: (String) -> Unit = {},
) {
    var aConfirmer by remember { mutableStateOf<Action?>(null) }
    ConfirmerAction(aConfirmer, fermer = { aConfirmer = null }, apres = { fermer(); apresSuppression() })
    val cs = MaterialTheme.colorScheme
    val portee = rememberCoroutineScope()
    var confirmerDepart by remember { mutableStateOf(false) }
    var depart by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var accesContacts by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val demandeContacts = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> accesContacts = ok }
    val membres by produceState<List<Moteur.Membre>?>(null, c.cle, accesContacts) { value = if (c.estGroupe) Moteur.membresGroupe(c) else null }
    var modifier by remember { mutableStateOf(false) }
    if (modifier) DialogueModifierContact(c) { modifier = false }

    ModalBottomSheet(onDismissRequest = fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // En-tête
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(c.nom, c.source, c.estGroupe, taille = 88.dp, bordure = cs.surfaceContainerLow, cle = c.cle)
                Spacer(Modifier.height(10.dp))
                Text(c.nom, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(if (c.estGroupe) "Groupe" else "Discussion")
                        append(" · ").append(e.libelleSource(c))
                        membres?.let { append(" · ${it.size} membre" + if (it.size > 1) "s" else "") }
                    },
                    fontSize = 14.sp, color = cs.onSurfaceVariant,
                )
            }
            // Actions rapides
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoutonInfo("Appeler", Icons.Outlined.Call, c.source.teinte(), Modifier.weight(1f)) { fermer(); appeler() }
                BoutonInfo("Rechercher", Icons.Outlined.Search, c.source.teinte(), Modifier.weight(1f)) { fermer(); rechercher() }
                BoutonInfo("Sélectionner", Icons.Outlined.Checklist, c.source.teinte(), Modifier.weight(1f)) { fermer(); selectionner() }
                BoutonInfo("Modifier", Icons.Outlined.Edit, c.source.teinte(), Modifier.weight(1f)) { modifier = true }
            }
            // Médias, liens et documents (messages chargés)
            val photos = historique.filter { it.type == "image" && it.media != null }
            val videos = historique.count { it.type == "video" }
            val docs = historique.count { it.type == "fichier" }
            val liens = historique.sumOf { LIEN_WEB.findAll(it.texte).count() }
            Carte {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PermMedia, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Médias, liens et docs", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(listOf("${photos.size} photo(s)", "$videos vidéo(s)", "$docs doc(s)", "$liens lien(s)").joinToString(" · "),
                            fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }
                val enCache = remember(photos) { photos.asReversed().mapNotNull { Moteur.mediaEnCache(it) }.take(8) }
                if (enCache.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    enCache.forEach { f ->
                        AsyncImage(model = f, contentDescription = "Photo", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
                    }
                }
            }
            Carte { Box(Modifier.padding(10.dp)) { ChoixStatut(e, c) } }
            // Fiche contact : la même personne sur d'autres messageries
            if (!c.estGroupe) FicheContact(e, c, ouvrir = { fermer(); ouvrir(it) })
            // Réglages de la conversation (notifications, Pro/Perso, épingler, archiver…)
            Carte {
                actionsConversation(e, c).forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = cs.outlineVariant)
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { if (a.confirmer != null) aConfirmer = a else a.faire() }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(a.icone, null, tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Text(a.libelle, fontSize = 15.sp)
                    }
                }
            }
            // Membres
            if (c.estGroupe) Carte {
                Text(membres?.let { "${it.size} membre" + if (it.size > 1) "s" else "" } ?: "Membres",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp))
                when {
                    membres == null && (c.source == Source.ELEMENT || c.source == Source.TEAMS) ->
                        Text("La liste des membres n'est pas disponible pour ${c.source.libelle}.", fontSize = 13.sp,
                            color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
                    membres == null -> Text("Chargement…", fontSize = 13.sp, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, bottom = 14.dp))
                    else -> {
                        var tous by remember { mutableStateOf(false) }
                        val liste = membres!!
                        if (!accesContacts && liste.any { it.nom == null && it.numero != null }) TextButton(
                            onClick = { demandeContacts.launch(android.Manifest.permission.READ_CONTACTS) },
                            modifier = Modifier.padding(start = 6.dp)) { Text("Afficher les noms de mes contacts") }
                        (if (tous) liste else liste.take(12)).forEach { mb ->
                            val titre = mb.nom ?: mb.numero?.let { "+$it" } ?: "Inconnu"
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(titre, c.source, false, taille = 34.dp, bordure = cs.surfaceContainerLow)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(titre, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (mb.nom != null && mb.numero != null && !mb.moi) Text("+" + mb.numero, fontSize = 12.sp, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                        if (liste.size > 12 && !tous) TextButton(onClick = { tous = true }, modifier = Modifier.padding(start = 6.dp)) {
                            Text("Voir tout (${liste.size - 12} de plus)")
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            // Quitter le groupe
            if (c.estGroupe) Carte {
                if (Moteur.peutQuitter(c)) Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = !depart) { confirmerDepart = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.ExitToApp, null, tint = cs.error)
                    Spacer(Modifier.width(14.dp))
                    Text(if (depart) "Départ en cours…" else "Quitter le groupe", fontSize = 15.sp, color = cs.error, fontWeight = FontWeight.SemiBold)
                }
                else Text("Pour quitter ce groupe, utilisez l'appli ${c.source.libelle}.", fontSize = 13.sp, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp))
            }
        }
    }

    if (confirmerDepart) AlertDialog(
        onDismissRequest = { confirmerDepart = false },
        title = { Text("Quitter « ${c.nom} » ?") },
        text = {
            Text("Vous ne recevrez plus les messages de ce groupe et il sera archivé dans Messagerie. " +
                "La passerelle ${c.source.libelle} transmet votre départ ; s'il n'apparaît pas dans l'appli ${c.source.libelle}, quittez aussi depuis celle-ci.")
        },
        confirmButton = {
            TextButton(onClick = {
                confirmerDepart = false; depart = true
                portee.launch {
                    val err = Moteur.quitterGroupe(c)
                    depart = false
                    if (err == null) { message("Vous avez quitté « ${c.nom} »"); fermer() } else message("Échec : $err")
                }
            }) { Text("Quitter le groupe", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmerDepart = false }) { Text("Annuler") } },
    )
}

@Composable
private fun Carte(contenu: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.background)
        .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))) { contenu() }
}

@Composable
private fun BoutonInfo(libelle: String, icone: ImageVector, teinte: Color, modifier: Modifier, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.height(76.dp).clip(RoundedCornerShape(16.dp)).background(cs.background)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp)).clickable(onClick = clic),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icone, null, tint = teinte)
        Spacer(Modifier.height(6.dp))
        Text(libelle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Choix de la conversation où transférer les messages sélectionnés.
 * Rien n'est envoyé ici : l'envoi part seulement après la confirmation qui suit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleTransfert(e: Etat, nombre: Int, fermer: () -> Unit, titre: String? = null,
    possible: (Conversation) -> Boolean = { Moteur.destinationPossible(it) && !e.estArchivee(it) }, choisir: (Conversation) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var filtre by remember { mutableStateOf("") }
    val candidates = remember(e.conversations, filtre) {
        e.conversations.filter(possible)
            .filter { filtre.isBlank() || it.nom.contains(filtre.trim(), ignoreCase = true) }
            .sortedByDescending { it.derniere }
    }
    ModalBottomSheet(onDismissRequest = fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(titre ?: ("Transférer $nombre message" + if (nombre > 1) "s" else ""), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = filtre, onValueChange = { filtre = it }, singleLine = true,
                placeholder = { Text("Rechercher une conversation") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(candidates, key = { it.cle }) { c ->
                    Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).clickable { choisir(c) }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.nom, c.source, c.estGroupe, taille = 40.dp, cle = c.cle)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nom, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(e.libelleSource(c) + if (c.estGroupe) " · groupe" else "", fontSize = 12.sp, color = c.source.teinte())
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Petite pastille ronde de sélection (cochée ou non). */
@Composable
fun PastilleSelection(choisi: Boolean, teinte: Color) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(22.dp).clip(CircleShape)
            .background(if (choisi) teinte else Color.Transparent)
            .border(2.dp, if (choisi) teinte else cs.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (choisi) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Fiche contact unifiée : la même personne sur plusieurs messageries (rapprochée par nom ou numéro,
 * ou reliée à la main). Tout reste sur ce téléphone.
 */
@Composable
private fun FicheContact(e: Etat, c: Conversation, ouvrir: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var version by remember { mutableStateOf(0) }
    var relier by remember { mutableStateOf(false) }
    val autres = remember(e.conversations, version) { Moteur.autresComptes(c, e) }
    Carte {
        Text(if (autres.isEmpty()) "Fiche contact" else "Fiche contact · ${autres.size + 1} messageries",
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp))
        (listOf(c) + autres).forEach { a ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = a.cle != c.cle) { ouvrir(a.cle) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(a.nom, a.source, false, taille = 36.dp, bordure = cs.surface, cle = a.cle)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.nom, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.libelleSource(a) + (if (a.cle == c.cle) " · ici" else "") + " · " + dateCourte(a.derniere),
                        fontSize = 12.sp, color = a.source.teinte())
                }
                if (a.cle != c.cle) IconButton(onClick = { Moteur.separerContact(a); version++ }) {
                    Icon(Icons.Outlined.LinkOff, "Pas la même personne", tint = cs.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { relier = true }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, null, tint = cs.primary)
            Spacer(Modifier.width(14.dp))
            Text("Relier une autre conversation…", fontSize = 14.sp, color = cs.primary)
        }
    }
    if (relier) FeuilleTransfert(e, 0, fermer = { relier = false }, titre = "Même personne que ${c.nom}",
        possible = { it.cle != c.cle && !it.estGroupe && autres.none { a -> a.cle == it.cle } }) { b ->
        Moteur.lierContacts(c, b); relier = false; version++
    }
}

/** « Aussi sur » : raccourcis vers la même personne sur les autres messageries. */
@Composable
fun AussiSur(e: Etat, c: Conversation, ouvrir: (String) -> Unit) {
    val autres = remember(e.conversations, c.cle) { Moteur.autresComptes(c, e) }
    if (autres.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Aussi sur", fontSize = 12.sp, color = cs.onSurfaceVariant)
        autres.forEach { a ->
            Row(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(a.source.teinte().copy(alpha = 0.12f))
                .clickable { ouvrir(a.cle) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(a.source.teinte(), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(e.libelleSource(a), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = a.source.teinte())
                val n = e.nombreNonLus(a)
                if (n > 0) Text(" · $n", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = a.source.teinte())
            }
        }
    }
}

/** Nom et photo choisis par vous pour ce contact ou ce groupe, gardés dans Messagerie seulement. */
@Composable
private fun DialogueModifierContact(c: Conversation, fermer: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val portee = rememberCoroutineScope()
    val nomPerso = remember { Moteur.nomPerso(c.cle) }
    var nom by remember { mutableStateOf(nomPerso ?: c.nom) }
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    var retirer by remember { mutableStateOf(false) }
    val aPhotoPerso = remember { Moteur.photoPerso(c.cle) != null }
    val choisir = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { photo = uri; retirer = false } }
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(if (c.estGroupe) "Modifier le groupe" else "Modifier le contact") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(96.dp).clip(CircleShape).clickable {
                    choisir.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, contentAlignment = Alignment.Center) {
                    when {
                        photo != null -> coil3.compose.AsyncImage(model = photo, contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(96.dp).clip(CircleShape))
                        retirer -> Avatar(nom, c.source, c.estGroupe, taille = 96.dp)
                        else -> Avatar(nom, c.source, c.estGroupe, taille = 96.dp, cle = c.cle)
                    }
                    Box(Modifier.align(Alignment.BottomEnd).size(32.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AddAPhoto, "Choisir une photo", tint = cs.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
                if ((aPhotoPerso && !retirer) || photo != null) TextButton(onClick = { photo = null; retirer = true }) { Text("Photo d'origine") }
                OutlinedTextField(value = nom, onValueChange = { nom = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Visible seulement dans Messagerie : rien n'est changé sur la messagerie ni chez le contact.",
                    fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                if (nomPerso != null) TextButton(onClick = { nom = "" }) { Text("Nom d'origine") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = nom.trim()
                portee.launch { Moteur.modifierContact(c.cle, if (n.isEmpty()) null else n, photo, retirer) }
                fermer()
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = fermer) { Text("Annuler") } },
    )
}
