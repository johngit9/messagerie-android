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
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
    // Gestion du groupe (renommer, ajouter, retirer) : la passerelle transmet ; toujours après confirmation
    var versionMembres by remember { mutableStateOf(0) }
    val membres by produceState<List<Moteur.Membre>?>(null, c.cle, accesContacts, versionMembres) { value = if (c.estGroupe) Moteur.membresGroupe(c) else null }
    var renommer by remember { mutableStateOf(false) }
    var ajouter by remember { mutableStateOf(false) }
    var aRetirer by remember { mutableStateOf<Moteur.Membre?>(null) }
    var gestionEnCours by remember { mutableStateOf(false) }
    fun gerer(action: suspend () -> String?, ok: String) {
        gestionEnCours = true
        portee.launch {
            val err = action()
            gestionEnCours = false
            message(err?.let { "Échec : $it" } ?: ok)
            if (err == null) { kotlinx.coroutines.delay(1500); versionMembres++ }
        }
    }
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
            if (!c.estGroupe) FicheContact(e, c, ouvrir = { fermer(); ouvrir(it) }, accesContacts = accesContacts,
                demanderAcces = { demandeContacts.launch(android.Manifest.permission.READ_CONTACTS) })
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
                        if (Moteur.peutGererGroupe(c)) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !gestionEnCours) { ajouter = true }
                            .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PersonAdd, null, tint = c.source.teinte())
                            Spacer(Modifier.width(14.dp))
                            Text(if (gestionEnCours) "Modification en cours…" else "Ajouter un membre", fontSize = 15.sp, color = c.source.teinte(), fontWeight = FontWeight.SemiBold)
                        }
                        if (!accesContacts && liste.any { it.nom == null && it.numero != null }) TextButton(
                            onClick = { demandeContacts.launch(android.Manifest.permission.READ_CONTACTS) },
                            modifier = Modifier.padding(start = 6.dp)) { Text("Afficher les noms de mes contacts") }
                        (if (tous) liste else liste.take(12)).forEach { mb ->
                            val titre = mb.nom ?: mb.numero?.let { "+$it" } ?: "Inconnu"
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(titre, c.source, false, taille = 34.dp, bordure = cs.surfaceContainerLow)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(titre, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (mb.nom != null && mb.numero != null && !mb.moi) Text("+" + mb.numero, fontSize = 12.sp, color = cs.onSurfaceVariant)
                                }
                                if (Moteur.peutGererGroupe(c) && !mb.moi && mb.id.isNotEmpty())
                                    IconButton(onClick = { aRetirer = mb }, enabled = !gestionEnCours) { Icon(Icons.Outlined.PersonRemove, "Retirer du groupe", tint = cs.onSurfaceVariant) }
                            }
                        }
                        if (liste.size > 12 && !tous) TextButton(onClick = { tous = true }, modifier = Modifier.padding(start = 6.dp)) {
                            Text("Voir tout (${liste.size - 12} de plus)")
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            // Renommer le groupe pour tous
            if (Moteur.peutGererGroupe(c)) Carte {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = !gestionEnCours) { renommer = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, null, tint = c.source.teinte())
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Renommer le groupe", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Pour tous les membres, dans ${c.source.libelle}", fontSize = 12.sp, color = cs.onSurfaceVariant)
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

    if (renommer) {
        var nom by remember { mutableStateOf(c.nom) }
        AlertDialog(
            onDismissRequest = { renommer = false },
            title = { Text("Renommer le groupe") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Le nouveau nom sera visible par tous les membres dans ${c.source.libelle}.", fontSize = 13.sp)
                androidx.compose.material3.OutlinedTextField(nom, { nom = it.take(100) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = nom.isNotBlank() && nom.trim() != c.nom, onClick = {
                renommer = false; gerer({ Moteur.renommerGroupe(c, nom) }, "Groupe renommé")
            }) { Text("Renommer") } },
            dismissButton = { TextButton(onClick = { renommer = false }) { Text("Annuler") } },
        )
    }
    aRetirer?.let { mb ->
        val qui = mb.nom ?: mb.numero?.let { "+$it" } ?: "ce membre"
        AlertDialog(
            onDismissRequest = { aRetirer = null },
            title = { Text("Retirer $qui ?") },
            text = { Text("$qui sera retiré du groupe « ${c.nom} » dans ${c.source.libelle}. Il faut être administrateur du groupe.") },
            confirmButton = { TextButton(onClick = { aRetirer = null; gerer({ Moteur.retirerMembre(c, mb) }, "$qui retiré du groupe") }) {
                Text("Retirer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { aRetirer = null }) { Text("Annuler") } },
        )
    }
    if (ajouter) ChoixContacts(e, c.source, "Ajouter au groupe", multiple = false,
        exclus = membres.orEmpty().mapNotNull { it.numero }.toSet(), fermer = { ajouter = false }) { choisis ->
        ajouter = false
        choisis.firstOrNull()?.let { k -> gerer({ Moteur.ajouterMembre(c, k) }, "${k.nom} ajouté au groupe") }
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
private fun FicheContact(e: Etat, c: Conversation, ouvrir: (String) -> Unit, accesContacts: Boolean, demanderAcces: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var version by remember { mutableStateOf(0) }
    var relier by remember { mutableStateOf(false) }
    val autres = remember(e.conversations, version) { Moteur.autresComptes(c, e) }
    Carte { CarnetTelephone(c, accesContacts, demanderAcces) }
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


/**
 * Choix de contacts (discussions à deux d'une messagerie) : un seul ou plusieurs.
 * Rien n'est fait ici : [valider] reçoit le choix, l'action est confirmée par la boîte de dialogue.
 */
@Composable
internal fun ChoixContacts(e: Etat, s: Source, titre: String, multiple: Boolean, exclus: Set<String> = emptySet(),
                           fermer: () -> Unit, valider: (List<Conversation>) -> Unit) {
    var q by remember { mutableStateOf("") }
    var choisis by remember { mutableStateOf(listOf<Conversation>()) }
    val tous = remember(e.conversations, s) {
        e.conversations.filter { it.source == s && !it.estGroupe }
            .filter { k -> exclus.none { n -> n.length >= 6 && k.jid.filter(Char::isDigit).endsWith(n.takeLast(9)) } }
            .sortedByDescending { it.derniere }
    }
    val liste = tous.filter { q.isBlank() || it.nom.contains(q.trim(), ignoreCase = true) }.take(80)
    AlertDialog(
        onDismissRequest = fermer,
        title = { Text(titre) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Rechercher un contact") },
                    modifier = Modifier.fillMaxWidth())
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (liste.isEmpty()) Text("Aucun contact ${s.libelle}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    liste.forEach { k ->
                        val pris = k in choisis
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (pris) s.teinte().copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { choisis = if (!multiple) listOf(k) else if (pris) choisis - k else choisis + k }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Avatar(k.nom, k.source, false, taille = 32.dp, bordure = MaterialTheme.colorScheme.surface, cle = k.cle)
                            Spacer(Modifier.width(10.dp))
                            Text(k.nom, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (pris) Text("✓", color = s.teinte(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = choisis.isNotEmpty(), onClick = { valider(choisis) }) {
            Text(if (multiple) "Valider (${choisis.size})" else "Ajouter") } },
        dismissButton = { TextButton(onClick = fermer) { Text("Annuler") } },
    )
}

/**
 * Fiche du carnet d'adresses du téléphone liée à la conversation (trouvée par numéro ou nom exact, ou choisie à la main) :
 * photo, numéros, e-mails, ouverture dans Contacts. Lecture locale ; toucher un numéro ouvre le clavier d'appel sans appeler.
 */
@Composable
private fun CarnetTelephone(c: Conversation, accesContacts: Boolean, demanderAcces: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var version by remember { mutableStateOf(0) }
    val fiche by produceState<Moteur.ContactTel?>(null, c.cle, accesContacts, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Moteur.contactTelephone(c) }
    }
    val choisir = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickContact()) { u -> if (u != null) { Moteur.lierCarnet(c, u); version++ } }
    fun lancer(i: android.content.Intent) { runCatching { ctx.startActivity(i) } }
    Text("Carnet du téléphone", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 4.dp))
    when {
        !accesContacts -> Ligne(Icons.Outlined.Contacts, "Autoriser l'accès aux contacts pour afficher sa fiche", cs.primary, demanderAcces)
        fiche == null -> Ligne(Icons.Outlined.Link, "Lier à un contact du téléphone…", cs.primary) { choisir.launch(null) }
        else -> {
            val f = fiche!!
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                lancer(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(f.uri)))
            }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (f.photo != null) AsyncImage(model = f.photo, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape))
                else Box(Modifier.size(36.dp).background(cs.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, null, tint = cs.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.nom, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (f.manuel) "Contact du téléphone · lié par vous" else "Contact du téléphone", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Ouvrir dans Contacts", tint = cs.onSurfaceVariant)
            }
            f.numeros.forEach { n ->
                Ligne(Icons.Outlined.Call, n, cs.onSurface) {
                    lancer(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + android.net.Uri.encode(n))))
                }
            }
            f.emails.forEach { m ->
                Ligne(Icons.Outlined.Email, m, cs.onSurface) {
                    lancer(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$m")))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                TextButton(onClick = { choisir.launch(null) }) { Text("Choisir un autre contact") }
                if (f.manuel) TextButton(onClick = { Moteur.lierCarnet(c, null); version++ }) { Text("Délier") }
            }
        }
    }
}

@Composable
private fun Ligne(icone: ImageVector, texte: String, teinte: Color, clic: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = clic).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, null, tint = if (teinte == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else teinte)
        Spacer(Modifier.width(14.dp))
        Text(texte, fontSize = 14.sp, color = teinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
