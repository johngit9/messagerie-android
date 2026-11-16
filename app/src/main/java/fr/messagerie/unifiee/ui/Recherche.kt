package fr.messagerie.unifiee.ui

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.Message
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Périodes proposées pour la recherche. */
private enum class Periode(val libelle: String, val jours: Int?) { Tout("Tout", null), Semaine("7 jours", 7), Mois("30 jours", 30), An("1 an", 365) }

/** Sans accents ni majuscules (pour comparer et surligner). */
internal fun simplifier(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * Recherche dans TOUS les messages archivés, toutes messageries confondues (sur le téléphone, rien n'est envoyé).
 * Filtres : messagerie, personne ou groupe, période, pièces jointes seulement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranRecherche(e: Etat, initiale: String, retour: () -> Unit, ouvrir: (conv: String, requete: String, message: String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var texte by rememberSaveable { mutableStateOf(initiale) }
    var personne by rememberSaveable { mutableStateOf("") }
    var source by remember { mutableStateOf<Source?>(null) }
    var periode by remember { mutableStateOf(Periode.Tout) }
    var piecesJointes by rememberSaveable { mutableStateOf(false) }
    var resultats by remember { mutableStateOf<List<Message>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val noms = remember(e.conversations) { e.conversations.associate { it.cle to it } }
    val sources = Source.entries.filter { s -> e.conversations.any { it.source == s } }

    LaunchedEffect(texte, personne, source, periode, piecesJointes) {
        delay(250)
        val p = simplifier(personne.trim())
        resultats = withContext(Dispatchers.IO) {
            // Personne : conversations dont le nom correspond ; sinon, auteur des messages
            val convs = if (p.isEmpty()) null else e.conversations.filter { simplifier(it.nom).contains(p) }.map { it.cle }.toSet().ifEmpty { null }
            val depuis = periode.jours?.let { System.currentTimeMillis() - it * 86_400_000L }
            val r = Moteur.archive.chercherPartout(texte.trim(), source, convs, depuis, piecesJointes, if (p.isNotEmpty() && convs == null) 1000 else 300)
            if (p.isNotEmpty() && convs == null) r.filter { simplifier(it.auteur).contains(p) }.take(300) else r
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                title = { Text("Rechercher partout", fontSize = 18.sp) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChampRecherche(texte, { texte = it }, "Mots à chercher", Icons.Outlined.Search, Modifier.focusRequester(focus))
                ChampRecherche(personne, { personne = it }, "Personne ou groupe (facultatif)", Icons.Outlined.Person)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = source == null, onClick = { source = null }, label = { Text("Toutes") })
                    sources.forEach { s -> FilterChip(selected = source == s, onClick = { source = if (source == s) null else s }, label = { Text(s.libelle) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Periode.entries.forEach { p -> FilterChip(selected = periode == p, onClick = { periode = p }, label = { Text(p.libelle) }) }
                    FilterChip(selected = piecesJointes, onClick = { piecesJointes = !piecesJointes }, label = { Text("Pièces jointes") },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) })
                }
            }
            val r = resultats
            val vide = texte.isBlank() && personne.isBlank() && !piecesJointes
            when {
                vide -> Text("Tapez des mots, un nom ou choisissez « Pièces jointes ». La recherche se fait sur ce téléphone, dans tous les messages gardés.",
                    color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                r == null -> {}
                r.isEmpty() -> Text("Aucun message trouvé.", color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
                else -> {
                    Text(if (r.size >= 300) "300 messages et plus (les plus récents)" else "${r.size} message" + if (r.size > 1) "s" else "",
                        fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                    val mots = texte.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map { simplifier(it) }
                    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(r, key = { it.cle }) { m ->
                            val c = noms[m.conv]
                            val s = c?.source ?: Source.depuis(m.conv.substringBefore(':'))
                            ResultatRecherche(m, c?.nom ?: m.conv.substringAfter(':'), s, c?.estGroupe ?: false, mots) {
                                ouvrir(m.conv, texte.trim(), m.cle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChampRecherche(valeur: String, changer: (String) -> Unit, indication: String, icone: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .background(cs.surfaceContainerLow, RoundedCornerShape(14.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (valeur.isEmpty()) Text(indication, color = cs.onSurfaceVariant, fontSize = 15.sp)
            BasicTextField(valeur, changer, singleLine = true, modifier = modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 15.sp, color = cs.onSurface), cursorBrush = SolidColor(cs.primary))
        }
        if (valeur.isNotEmpty()) IconButton(onClick = { changer("") }) { Icon(Icons.Outlined.Close, "Effacer") }
    }
}

@Composable
private fun ResultatRecherche(m: Message, nom: String, s: Source?, groupe: Boolean, mots: List<String>, ouvrir: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    Row(
        Modifier.fillMaxWidth().background(cs.surface, RoundedCornerShape(14.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = ouvrir).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (s != null) Avatar(nom, s, groupe, taille = 36.dp, cle = m.conv)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nom, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(dateCourte(m.date), fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
            Text((s?.libelle ?: "") + " · " + (if (m.deMoi) "Vous" else m.auteur), fontSize = 12.sp,
                color = s?.teinte() ?: cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val texte = when (m.type) {
                "image" -> "📷 " + m.texte.ifBlank { "Photo" }
                "video" -> "🎬 " + m.texte.ifBlank { "Vidéo" }
                "fichier" -> "📎 " + m.texte.ifBlank { "Fichier" }
                else -> m.texteUtile
            }
            Text(surligner(extrait(texte, mots), mots, SpanStyle(color = accent, fontWeight = FontWeight.Bold)),
                fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Passage du texte autour du premier mot trouvé (pour les longs messages). */
private fun extrait(t: String, mots: List<String>): String {
    val plat = t.replace(Regex("\\s+"), " ")
    if (plat.length <= 160 || mots.isEmpty()) return plat
    val s = simplifier(plat)
    val i = mots.mapNotNull { m -> s.indexOf(m).takeIf { it >= 0 } }.minOrNull() ?: return plat
    val debut = (i - 50).coerceAtLeast(0)
    return (if (debut > 0) "…" else "") + plat.substring(debut)
}

/** Met en évidence les mots cherchés (début de mot, sans tenir compte des accents). */
private fun surligner(t: String, mots: List<String>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(t)
    if (mots.isEmpty()) return@buildAnnotatedString
    // NFD peut changer la longueur : on ne surligne que si la version simplifiée garde les positions
    val s = simplifier(t)
    if (s.length != t.length) return@buildAnnotatedString
    for (m in mots) {
        var i = s.indexOf(m)
        while (i >= 0) {
            if (i == 0 || !s[i - 1].isLetterOrDigit()) addStyle(style, i, i + m.length)
            i = s.indexOf(m, i + m.length)
        }
    }
}
