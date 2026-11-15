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
    Row(
        Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(couleur.copy(alpha = 0.10f))
            .clickable(enabled = m.media != null && !enCours) {
                enCours = true
                portee.launch {
                    runCatching { Moteur.fichierMedia(m) }
                        .onSuccess { ouvrirAvec(ctx, it, m) }
                        .onFailure { afficher("Téléchargement impossible : ${it.message ?: it.javaClass.simpleName}") }
                    enCours = false
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (enCours) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = couleur)
        else Icon(if (video) Icons.Outlined.PlayCircle else Icons.Outlined.Description, null, tint = couleur, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(if (video) "Vidéo" else nomMedia(m), color = couleur, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (m.media == null) "Indisponible" else "Toucher pour ouvrir", fontSize = 11.sp, color = couleur.copy(alpha = 0.75f))
        }
    }
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
