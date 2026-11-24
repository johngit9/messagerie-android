package fr.messagerie.unifiee.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import fr.messagerie.unifiee.Message
import fr.messagerie.unifiee.Moteur
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.PlayArrow
import kotlinx.coroutines.launch
import java.io.File

/** Nom de fichier d'un média (« ref#mime#nom »). */
private fun nomMedia(m: Message) = m.media?.split('#')?.getOrNull(2)?.takeIf { it.isNotBlank() }
    ?: m.texte.removePrefix("[fichier] ").ifBlank { "fichier" }

/** Photo dans la bulle : téléchargée une fois (cache), affichée par Coil, plein écran au toucher. */
@Composable
fun PhotoMessage(m: Message, couleur: Color) {
    if (m.media == null) { Text("🖼 Photo", color = couleur); return }
    val fichier by produceState<Result<File>?>(null, m.cle) { value = runCatching { Moteur.fichierMedia(m) } }
    var pleinEcran by remember { mutableStateOf(false) }
    val f = fichier
    Box(
        Modifier.widthIn(min = 160.dp, max = 260.dp).heightIn(min = 120.dp, max = 340.dp)
            .clip(RoundedCornerShape(12.dp)).background(couleur.copy(alpha = 0.08f))
            .clickable(enabled = f?.isSuccess == true) { pleinEcran = true },
        contentAlignment = Alignment.Center,
    ) {
        when {
            f == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = couleur)
            f.isFailure -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Outlined.BrokenImage, null, tint = couleur)
                Text("Photo indisponible", fontSize = 12.sp, color = couleur)
            }
            else -> AsyncImage(model = f.getOrNull(), contentDescription = "Photo", contentScale = ContentScale.Fit,
                modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 340.dp))
        }
    }
    if (pleinEcran) f?.getOrNull()?.let { VisionneusePhoto(it, m) { pleinEcran = false } }
}

/** Autocollant : image seule, sans bulle, plus petite qu'une photo. */
@Composable
fun AutocollantMessage(m: Message, couleur: Color) {
    if (m.media == null) { Text(m.texte.removePrefix("[autocollant]").trim().ifEmpty { "Autocollant" }, color = couleur, fontSize = 32.sp); return }
    val fichier by produceState<Result<File>?>(null, m.cle) { value = runCatching { Moteur.fichierMedia(m) } }
    val f = fichier
    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        when {
            f == null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            f.isFailure -> Text(m.texte.removePrefix("[autocollant]").trim().ifEmpty { "Autocollant" }, fontSize = 28.sp)
            else -> AsyncImage(model = f.getOrNull(), contentDescription = "Autocollant", contentScale = ContentScale.Fit, modifier = Modifier.size(140.dp))
        }
    }
}

/** Position partagée : carte avec le lieu, ouverte dans l'appli de cartes au toucher. */
@Composable
fun PositionMessage(m: Message, couleur: Color) {
    val ctx = LocalContext.current
    val geo = m.geo
    val coords = geo?.removePrefix("geo:")?.substringBefore(';')?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }
    Row(
        Modifier.widthIn(min = 200.dp, max = 260.dp).clip(RoundedCornerShape(12.dp)).background(couleur.copy(alpha = 0.10f))
            .clickable(enabled = geo != null) {
                val lieu = m.texte.removePrefix("📍").trim()
                val uri = if (coords != null && coords.size >= 2) "geo:${coords[0]},${coords[1]}?q=${coords[0]},${coords[1]}(" + android.net.Uri.encode(lieu) + ")" else geo!!
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))) }
            }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Place, null, tint = couleur, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(m.texte.removePrefix("📍").trim().ifEmpty { "Position" }, color = couleur, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(if (coords != null && coords.size >= 2) "%.5f, %.5f · Ouvrir la carte".format(coords[0], coords[1]) else "Position partagée",
                fontSize = 12.sp, color = couleur.copy(alpha = 0.75f))
        }
    }
}

/** Visionneuse plein écran (pincer pour zoomer). */
@Composable
private fun VisionneusePhoto(f: File, m: Message, fermer: () -> Unit) {
    val ctx = LocalContext.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = fermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = f, contentDescription = "Photo", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, deplacement, z, _ ->
                            zoom = (zoom * z).coerceIn(1f, 5f)
                            if (zoom > 1f) { dx += deplacement.x; dy += deplacement.y } else { dx = 0f; dy = 0f }
                        }
                    }
                    .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = dx, translationY = dy),
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = fermer) { Icon(Icons.Outlined.Close, "Fermer", tint = Color.White) }
                Text(m.auteur + " · " + heure(m.date), color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { ouvrirAvec(ctx, f, m) }) { Icon(Icons.Outlined.OpenInNew, "Ouvrir avec…", tint = Color.White) }
            }
        }
    }
}

/** Vidéo ou document : téléchargé au toucher puis ouvert avec l'appli de votre choix. */
@Composable
fun FichierMessage(m: Message, couleur: Color, afficher: (String) -> Unit) {
    val ctx = LocalContext.current
    val portee = rememberCoroutineScope()
    var enCours by remember { mutableStateOf(false) }
    val video = m.type == "video"
    var essai by remember { mutableStateOf(0) }
    // Aperçu : vignette de la messagerie, ou 1re page d'un PDF déjà téléchargé
    val apercu by produceState<File?>(null, m.cle, essai) { value = if (video) null else Moteur.apercuFichier(m) }
    val nom = if (video) "Vidéo" else nomMedia(m)
    val ext = nom.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.uppercase()
        ?: m.media?.split('#')?.getOrNull(1)?.substringAfter('/')?.take(4)?.uppercase() ?: "FICHIER"
    Column(
        Modifier.width(250.dp).clip(RoundedCornerShape(12.dp)).background(couleur.copy(alpha = 0.10f))
            .clickable(enabled = m.media != null && !enCours) {
                enCours = true
                portee.launch {
                    runCatching { Moteur.fichierMedia(m) }
                        .onSuccess { ouvrirAvec(ctx, it, m); if (apercu == null) essai++ }
                        .onFailure { afficher("Téléchargement impossible : ${it.message ?: it.javaClass.simpleName}") }
                    enCours = false
                }
            },
    ) {
        apercu?.let {
            AsyncImage(model = it, contentDescription = "Aperçu de la pièce jointe", contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).aspectRatio(1.4f).background(Color.White))
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 40.dp, height = 46.dp).background(couleurType(ext), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                if (enCours) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                else if (video) Icon(Icons.Outlined.PlayCircle, null, tint = Color.White)
                else Text(ext.take(4), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(nom, color = couleur, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (m.media == null) "Indisponible" else "Toucher pour ouvrir", fontSize = 11.sp, color = couleur.copy(alpha = 0.75f))
            }
        }
    }
}

/** Couleur de l'étiquette selon le type de fichier. */
private fun couleurType(ext: String): Color = when (ext.lowercase()) {
    "pdf" -> Color(0xFFD93A3A)
    "doc", "docx", "odt", "rtf", "txt", "pages" -> Color(0xFF2B6CD9)
    "xls", "xlsx", "ods", "csv", "numb" -> Color(0xFF1E8A4C)
    "ppt", "pptx", "odp", "key" -> Color(0xFFD9701E)
    "zip", "rar", "7z", "gz", "tar" -> Color(0xFF6B6F7A)
    "jpg", "jpeg", "png", "heic", "gif", "webp" -> Color(0xFF8A4BD6)
    else -> Color(0xFF5A606D)
}

private fun ouvrirAvec(ctx: Context, f: File, m: Message) {
    val mime = m.media?.split('#')?.getOrNull(1) ?: "*/*"
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", f)
    try {
        ctx.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Ouvrir avec")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

/** Vidéo dans la bulle : aperçu (vignette) avec bouton lecture ; lecture dans l'appli au toucher. */
@Composable
fun VideoMessage(m: Message, couleur: Color, afficher: (String) -> Unit) {
    if (m.media == null) { FichierMessage(m, couleur, afficher); return }
    val portee = rememberCoroutineScope()
    var essai by remember { mutableStateOf(0) }
    val apercu by produceState<File?>(null, m.cle, essai) { value = Moteur.vignetteVideo(m, telecharger = essai > 0) }
    var chargement by remember { mutableStateOf(false) }
    var lecture by remember { mutableStateOf<File?>(null) }
    Box(
        Modifier.width(240.dp).heightIn(min = 140.dp, max = 320.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF15171C))
            .clickable(enabled = !chargement) {
                chargement = true
                portee.launch {
                    runCatching { Moteur.fichierMedia(m) }
                        .onSuccess { lecture = it; if (apercu == null) essai++ }
                        .onFailure { afficher("Vidéo indisponible : ${it.message ?: it.javaClass.simpleName}") }
                    chargement = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        apercu?.let { AsyncImage(model = it, contentDescription = "Aperçu de la vidéo", contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()) }
        Box(Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
            if (chargement) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = Color.White)
            else Icon(Icons.Outlined.PlayArrow, "Lire la vidéo", tint = Color.White, modifier = Modifier.size(34.dp))
        }
        m.duree?.takeIf { it > 0 }?.let { d ->
            val s = d.toInt()
            Text("${s / 60}:${"%02d".format(s % 60)}", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (apercu == null && !chargement) Text("Vidéo", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
    }
    lecture?.let { f -> LecteurVideo(f, m) { lecture = null } }
}

/** Lecteur vidéo plein écran (commandes Android standard). */
@Composable
private fun LecteurVideo(f: File, m: Message, fermer: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = fermer, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { c ->
                    android.widget.VideoView(c).apply {
                        val commandes = android.widget.MediaController(c)
                        commandes.setAnchorView(this)
                        setMediaController(commandes)
                        setVideoPath(f.absolutePath)
                        setOnPreparedListener { start() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().align(Alignment.TopStart).statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = fermer) { Icon(Icons.Outlined.Close, "Fermer", tint = Color.White) }
                Text(m.auteur + " · " + heure(m.date), color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { ouvrirAvec(ctx, f, m) }) { Icon(Icons.Outlined.OpenInNew, "Ouvrir avec…", tint = Color.White) }
            }
        }
    }
}
