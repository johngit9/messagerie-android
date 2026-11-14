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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
fun EcranConversation(e: Etat, c: Conversation, retour: () -> Unit) {
    val portee = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var historique by remember(c.cle) { mutableStateOf(c.messages) }
    var total by remember(c.cle) { mutableIntStateOf(0) }
    var chargement by remember { mutableStateOf(false) }
    var brouillon by rememberSaveable(c.cle) { mutableStateOf("") }
    var brouillonAvant by remember(c.cle) { mutableStateOf<String?>(null) }
    var reponseA by remember(c.cle) { mutableStateOf<Message?>(null) }
    var envoi by remember { mutableStateOf(false) }
    var iaEnCours by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var menuIA by remember { mutableStateOf(false) }
    // Date de lecture à l'ouverture : place le repère « Nouveaux messages ».
    val luAvant = remember(c.cle) { if (e.estNonLue(c)) e.luJusqua(c) else null }

    LaunchedEffect(c.cle) { Moteur.marquerLue(c) }   // lu dans l'appli seulement : aucun accusé envoyé
    LaunchedEffect(c.derniere) {
        val n = maxOf(200, historique.size)
        historique = withContext(Dispatchers.IO) { Moteur.archive.messages(c.cle, null, n) }
        total = withContext(Dispatchers.IO) { Moteur.archive.nombreMessages(c.cle) }
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
                navigationIcon = { TextButton(onClick = retour) { Text("←", fontSize = 22.sp) } },
                title = {
                    Column {
                        Text(c.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.libelleSource(c) + if (c.estGroupe) " · groupe" else "",
                            style = MaterialTheme.typography.labelMedium, color = c.source.teinte())
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { MenuConversation(e, c) { menu = false } }
                    }
                },
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
                            repondre = { reponseA = l.m },
                            copier = { copier(it, l.m.texteUtile); afficher("Texte copié") })
                    }
                }
                if (historique.size < total) item(key = "plus") {
                    OutlinedButton(onClick = {
                        if (!chargement) portee.launch {
                            chargement = true
                            val plus = withContext(Dispatchers.IO) { Moteur.archive.messages(c.cle, historique.firstOrNull()?.date, 200) }
                            historique = plus + historique
                            chargement = false
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
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
                            TextButton(onClick = { reponseA = null }) { Text("✕") }
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
                                if (iaEnCours) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("✨")
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
                            if (envoi) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Text("Envoyer")
                        }
                    }
                }
            }
        }
    }
}

private fun copier(ctx: Context, texte: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", texte))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bulle(m: Message, c: Conversation, afficherAuteur: Boolean, repondre: () -> Unit, copier: (Context) -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val moi = m.deMoi
    Column(Modifier.fillMaxWidth().padding(top = if (afficherAuteur) 6.dp else 1.dp),
        horizontalAlignment = if (moi) Alignment.End else Alignment.Start) {
        if (c.estGroupe && !moi && afficherAuteur) {
            Text(m.auteur, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.source.teinte(), modifier = Modifier.padding(start = 10.dp, bottom = 2.dp))
        }
        Box {
            Column(
                Modifier.widthIn(max = 300.dp)
                    .background(if (moi) c.source.teinte() else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                val couleur = if (moi) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                when (m.type) {
                    "audio" -> Text("🎤 Message vocal" + (m.duree?.let { d -> " · %d:%02d".format(d.toInt() / 60, d.toInt() % 60) } ?: ""),
                        color = couleur, fontStyle = FontStyle.Italic)
                    "image" -> Text("🖼 " + m.texte, color = couleur)
                    "video" -> Text("🎬 " + m.texte, color = couleur)
                    "fichier" -> Text("📄 " + m.texte.removePrefix("[fichier] "), color = couleur)
                    else -> Text(m.texte, color = couleur)
                }
                Text(heure(m.date), fontSize = 10.sp, color = couleur.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.End))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Répondre") }, onClick = { menu = false; repondre() })
                DropdownMenuItem(text = { Text("Copier le texte") }, onClick = { menu = false; copier(ctx) })
            }
        }
    }
}

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
