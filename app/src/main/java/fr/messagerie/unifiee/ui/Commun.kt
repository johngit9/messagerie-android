package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.Moteur
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Source
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val degradeIA = Brush.linearGradient(listOf(Color(0xFFFA7340), Color(0xFFD94DBF), Color(0xFF5973FF)))
val orangeAttente = Color(0xFFE8833A)

fun Source.teinte() = Color(fr.messagerie.unifiee.Affichage.couleurs[this] ?: couleur)

private fun memeJour(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** « 14:32 », « Hier 14:32 », « Lundi 14:32 », « 3 nov. 14:32 », « 3 nov. 2025 14:32 ». */
fun dateCourte(ms: Long): String {
    val d = Calendar.getInstance().apply { timeInMillis = ms }
    val maintenant = Calendar.getInstance()
    val hier = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val jours = (System.currentTimeMillis() - ms) / 86_400_000
    val motif = when {
        memeJour(d, maintenant) -> "HH:mm"
        memeJour(d, hier) -> "'Hier' HH:mm"
        jours < 7 -> "EEEE HH:mm"
        d.get(Calendar.YEAR) == maintenant.get(Calendar.YEAR) -> "d MMM HH:mm"
        else -> "d MMM yyyy HH:mm"
    }
    return SimpleDateFormat(motif, Locale.FRANCE).format(Date(ms)).replaceFirstChar { it.uppercase() }
}

fun jourComplet(ms: Long): String {
    val d = Calendar.getInstance().apply { timeInMillis = ms }
    val hier = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        memeJour(d, Calendar.getInstance()) -> "Aujourd'hui"
        memeJour(d, hier) -> "Hier"
        else -> SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date(ms)).replaceFirstChar { it.uppercase() }
    }
}

fun heure(ms: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))

@Composable
fun Avatar(nom: String, source: Source, groupe: Boolean, taille: Dp = 44.dp, bordure: Color = MaterialTheme.colorScheme.surface, cle: String? = null) {
    val initiales = nom.split(' ', '-').filter { it.firstOrNull()?.isLetter() == true }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "#" }
    val teinte = (nom.fold(0) { a, c -> (a * 31 + c.code) and 0xFFFF } % 360).toFloat()
    Box(Modifier.size(taille)) {
        Box(
            Modifier.size(taille).background(Color.hsv(teinte, 0.5f, 0.72f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (groupe) Icon(Icons.Outlined.Group, "Groupe", tint = Color.White, modifier = Modifier.size(taille * 0.5f))
            else Text(initiales, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (taille.value * 0.36f).sp)
        }
        if (cle != null) {
            val version by Moteur.versionPhotos.collectAsState()
            val photo by androidx.compose.runtime.produceState<java.io.File?>(null, cle, version) { value = Moteur.photoContact(cle) }
            photo?.let { f ->
                coil3.compose.AsyncImage(model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(f)
                    .memoryCacheKey(f.path + ":" + f.lastModified()).diskCacheKey(f.path + ":" + f.lastModified()).build(), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(taille).clip(CircleShape))
            }
        }
        LogoSource(source, taille * 0.42f, Modifier.align(Alignment.BottomEnd).border(2.dp, bordure, CircleShape))
        // Statut du contact : point vert « en ligne », orange « absent » (si la messagerie le transmet)
        if (cle != null && !groupe) {
            val presences by fr.messagerie.unifiee.Presences.etat.collectAsState()
            presences[cle]?.let { p ->
                Box(Modifier.align(Alignment.TopEnd).size(taille * 0.28f).border(2.dp, bordure, CircleShape)
                    .background(if (p == "online") Color(0xFF2F9E44) else Color(0xFFF59F00), CircleShape)
                    .semantics { contentDescription = if (p == "online") "En ligne" else "Absent" })
            }
        }
    }
}

@Composable
fun Pastille(n: Int) {
    if (n <= 0) return
    Text(if (n > 999) "999+" else "$n", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
}

@Composable
fun Etiquette(texte: String, couleur: Color, plein: Boolean = false) {
    Text(texte, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        color = if (plein) Color.White else couleur,
        modifier = Modifier.background(if (plein) couleur else couleur.copy(alpha = 0.15f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp))
}

/** Une action sur une conversation. */
/** Action sur une conversation ; confirmer = texte de la question à poser avant (actions définitives). */
class Action(val libelle: String, val icone: ImageVector, val confirmer: String? = null, val faire: () -> Unit)

/** Actions communes (liste et conversation). Toutes LOCALES sauf « Refaire le résumé » (Ollama). */
fun actionsConversation(e: Etat, c: Conversation): List<Action> = buildList {
    if (e.estNonLue(c)) add(Action("Marquer comme lu", Icons.Outlined.MarkEmailRead) { Moteur.marquerLue(c) })
    else add(Action("Marquer comme non lu", Icons.Outlined.MarkEmailUnread) { Moteur.marquerNonLue(c) })
    // Seule l'action qui a un sens dans l'état actuel est proposée
    if (e.aTraiter(c)) add(Action("Marquer comme traitée", Icons.Outlined.TaskAlt) { Moteur.marquerTraitee(c) })
    else add(Action("Ajouter à « À traiter »", Icons.Outlined.Flag) { Moteur.mettreATraiter(c, true) })
    if (e.etat(c).sourdine) add(Action("Réactiver les notifications", Icons.Outlined.Notifications) { Moteur.basculerSourdine(c) })
    else add(Action("Mettre en sourdine", Icons.Outlined.NotificationsOff) { Moteur.basculerSourdine(c) })
    if (Moteur.cfg.ollamaConfigure) add(Action("Refaire le résumé IA", Icons.Outlined.AutoAwesome) { Moteur.regenerer(c) })
    add(Action(if (e.estEpinglee(c)) "Désépingler" else "Épingler en haut", Icons.Outlined.PushPin) { Moteur.basculerEpingle(c) })
    add(Action(if (e.estArchivee(c)) "Désarchiver" else "Archiver", Icons.Outlined.Archive) { Moteur.basculerArchive(c) })
    val manuel = e.categories[c.cle]
    if (e.estPro(c)) add(Action("Ranger en Perso", Icons.Outlined.Person) { Moteur.classer(c, if (c.source in e.pro) "perso" else null) })
    else add(Action("Ranger en Pro", Icons.Outlined.Work) { Moteur.classer(c, if (c.source in e.pro) null else "pro") })
    if (manuel != null) add(Action("Classer selon la messagerie", Icons.Outlined.Restore) { Moteur.classer(c, null) })
    // Anti-spam local : rien n'est signalé à la messagerie
    if (e.estSpam(c)) add(Action("Ce n'est pas un spam", Icons.Outlined.VerifiedUser) { Moteur.marquerSpam(c, false) })
    else if (!c.estGroupe && c.messages.none { it.deMoi }) add(Action("Marquer comme spam", Icons.Outlined.Report) { Moteur.marquerSpam(c, true) })
    add(Action("Supprimer la conversation", Icons.Outlined.DeleteOutline,
        confirmer = "Supprimer « ${c.nom} » de Messagerie ? Les messages restent chez vos contacts et dans ${c.source.libelle}. " +
            "La conversation reviendra si un nouveau message arrive.") { Moteur.supprimerConversation(c) })
}

@Composable
fun ElementAction(icone: ImageVector, libelle: String, clic: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = clic).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(libelle, style = MaterialTheme.typography.bodyLarge)
    }
}


/** Choix du statut de la conversation (réponse attendue / pas de réponse / automatique). */
@Composable
fun ChoixStatut(e: Etat, c: Conversation, apres: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val actuel = Moteur.statutChoisi(c)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Statut", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Moteur.Statut.ATTENDUE to "Réponse attendue",
                Moteur.Statut.AUCUNE to "Pas de réponse",
                Moteur.Statut.AUTO to "Automatique",
            ).forEach { (st, libelle) ->
                val choisi = st == actuel
                Text(libelle, fontSize = 13.sp, fontWeight = if (choisi) FontWeight.Bold else FontWeight.Medium,
                    color = if (choisi) cs.onPrimary else cs.onSurface, maxLines = 1,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (choisi) cs.primary else cs.surfaceVariant)
                        .clickable { Moteur.choisirStatut(c, st); apres() }
                        .padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (actuel == Moteur.Statut.AUTO) Text(
            if (e.aTraiter(c)) "Automatique : l'IA pense qu'une réponse est attendue." else "Automatique : décidé par l'IA (et les mentions @ dans les groupes).",
            fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        if (actuel == Moteur.Statut.AUCUNE) Text("Jusqu'au prochain message reçu.", fontSize = 12.sp, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}


/** Demande de confirmation avant une action définitive. */
@Composable
fun ConfirmerAction(a: Action?, fermer: () -> Unit, apres: () -> Unit = {}) {
    if (a?.confirmer == null) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = fermer,
        title = { Text(a.libelle + " ?") },
        text = { Text(a.confirmer) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { fermer(); a.faire(); apres() }) {
            Text(a.libelle.substringBefore(' '), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = fermer) { Text("Annuler") } },
    )
}
