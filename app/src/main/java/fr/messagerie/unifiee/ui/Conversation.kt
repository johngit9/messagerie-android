package fr.messagerie.unifiee.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.DeleteOutline
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
    var brouillonAvant by remember(c.cle) { mutableStateOf<String?>(null) }
    var reponseA by remember(c.cle) { mutableStateOf<Message?>(null) }
    var envoi by remember { mutableStateOf(false) }
    var iaEnCours by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var menuIA by remember { mutableStateOf(false) }
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
            TopAppBar(
                navigationIcon = { IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.nom, c.source, c.estGroupe, taille = 38.dp, cle = c.cle)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(c.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.libelleSource(c) + if (c.estGroupe) " · groupe" else "",
                            style = MaterialTheme.typography.labelMedium, color = c.source.teinte())
                    }
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true },
                        colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Icon(Icons.Outlined.MoreVert, "Actions")
                    }
                    Spacer(Modifier.width(4.dp))
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                item(key = "resume") { CarteResume(e, c) { brouillon = it } }
                items(lignes.asReversed(), key = { it.cle }) { l ->
                    when (l) {
                        is Ligne.Jour -> Text(jourComplet(l.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Ligne.NonLus -> Text("— Nouveaux messages —", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Ligne.Msg -> Bulle(l.m, c, l.auteur,
                            reactions = reactions[l.m.cle].orEmpty(), maReaction = mesReactions[l.m.cle],
                            repondre = { reponseA = l.m },
                            copier = { copier(it, l.m.texteUtile); afficher("Texte copié") },
                            reagir = { emoji -> vm.reagir(l.m, c, emoji) { afficher(it) } },
                            afficherMessage = { afficher(it) })
                    }
                }
                if (historique.size < total) item(key = "plus") {
                    OutlinedButton(onClick = { vm.plusAnciens() }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(if (chargement) "Chargement…" else "Messages plus anciens (${total - historique.size})")
                    }
                }
            }

            // --- Compositeur
            Surface(tonalElevation = 3.dp) {
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
                    if (brouillonAvant != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨ Texte amélioré par l'IA", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { brouillon = brouillonAvant!!; brouillonAvant = null }) { Text("Revenir à mon texte", fontSize = 12.sp) }
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = brouillon, onValueChange = { brouillon = it }, enabled = !iaEnCours,
                            placeholder = { Text(if (c.source == Source.SMS) "Écrire un SMS…" else "Écrire une réponse…") },
                            maxLines = 6, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f),
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
                                            if (r != null) { brouillonAvant = brouillonAvant ?: avant; brouillon = r } else afficher("L'IA n'a pas répondu")
                                        }
                                    })
                                }
                            }
                        }
                        Button(
                            onClick = {
                                envoi = true
                                portee.launch {
                                    val err = Moteur.envoyer(brouillon, c, reponseA)
                                    envoi = false
                                    if (err == null) {
                                        brouillon = ""; brouillonAvant = null; reponseA = null
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
    if (menu) FeuilleActions(e, c) { menu = false }
}

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
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val couleur = if (moi) Color.White else MaterialTheme.colorScheme.onSurface
            when (m.type) {
                "audio" -> if (m.media != null) LecteurVocal(m, couleur) else
                    Text("🎤 Message vocal" + (m.duree?.let { d -> " · " + minSec(d.toInt() * 1000) } ?: ""),
                        color = couleur, fontStyle = FontStyle.Italic)
                "image" -> PhotoMessage(m, couleur)
                "video", "fichier" -> FichierMessage(m, couleur, afficherMessage)
                else -> Text(m.texte, color = couleur)
            }
            Text(heure(m.date), fontSize = 10.sp, color = couleur.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
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
        reagir = { menu = false; reagir(it) }, repondre = { menu = false; repondre() }, copier = { menu = false; copier(ctx) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleMessage(m: Message, c: Conversation, monEmoji: String?, fermer: () -> Unit,
                           reagir: (String?) -> Unit, repondre: () -> Unit, copier: () -> Unit) {
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
