package fr.messagerie.unifiee.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fr.messagerie.unifiee.Affichage
import fr.messagerie.unifiee.Appairage
import fr.messagerie.unifiee.Moteur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private tailrec fun Context.activite(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activite()
    else -> null
}

private fun qrCode(texte: String): ImageBitmap {
    val m = QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return android.graphics.Bitmap.createBitmap(px, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Bouton « Scanner un QR code » : lit le QR affiché par un appareil déjà configuré,
 * retire la configuration chiffrée sur le relais et l'applique après confirmation.
 */
@Composable
internal fun ScannerAppairage(principal: Boolean = true, applique: () -> Unit) {
    val ctx = LocalContext.current
    val portee = rememberCoroutineScope()
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var recu by remember { mutableStateOf<Appairage.Recu?>(null) }

    fun lu(texte: String?) {
        val lien = texte?.let { Appairage.lire(it) }
        if (lien == null) { message = "Ce n'est pas un QR code de connexion de Messagerie."; return }
        enCours = true; message = null
        portee.launch {
            try { recu = withContext(Dispatchers.IO) { Appairage.recuperer(lien) } }
            catch (ex: Exception) { message = ex.message ?: ex.javaClass.simpleName }
            enCours = false
        }
    }

    fun scanner() {
        message = null
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        try {
            GmsBarcodeScanning.getClient(ctx.activite() ?: ctx, options).startScan()
                .addOnSuccessListener { lu(it.rawValue) }
                .addOnFailureListener { message = "Lecteur de QR code indisponible : ${it.message ?: "services Google Play requis"}" }
        } catch (ex: Exception) { message = "Lecteur de QR code indisponible : ${ex.message ?: ex.javaClass.simpleName}" }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (principal) Button(onClick = { scanner() }, enabled = !enCours, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Scanner un QR code")
        } else OutlinedButton(onClick = { scanner() }, enabled = !enCours, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Scanner un QR code")
        }
        if (enCours) { Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp) }
    }
    message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }

    recu?.let { r ->
        AlertDialog(
            onDismissRequest = { recu = null; message = "Configuration non appliquée." },
            title = { Text("Configuration reçue") },
            text = {
                Text("De « ${r.appareil} »" + (if (r.contenu.isNotEmpty()) " : ${r.contenu.joinToString(", ")}" else "") +
                    ".\n\nElle remplace les connexions de cet appareil. Ne l'acceptez que si vous venez de scanner votre propre appareil.")
            },
            confirmButton = {
                TextButton(onClick = {
                    message = try {
                        val faits = Moteur.cfg.importerJson(r.config)
                        Affichage.charger(Moteur.cfg)
                        applique()
                        if (faits.isEmpty()) "Aucun réglage reconnu." else "Connecté : ${faits.joinToString(", ")}."
                    } catch (ex: Exception) { "Configuration illisible : ${ex.message ?: ex.javaClass.simpleName}" }
                    recu = null
                }) { Text("Appliquer") }
            },
            dismissButton = { TextButton(onClick = { recu = null; message = "Configuration non appliquée." }) { Text("Annuler") } },
        )
    }
}

/** Réglages > Connecter un appareil : afficher le QR code (cet appareil configuré) ou en scanner un. */
@Composable
internal fun PageAppairage(applique: () -> Unit) {
    val cfg = Moteur.cfg
    var montrer by remember { mutableStateOf(false) }

    Section("Ajouter un autre appareil") {
        Explication("Sur le nouvel appareil : installez Messagerie, puis touchez « Scanner un QR code » sur l'écran de bienvenue " +
            "(ou ici). Il reçoit les connexions de celui-ci : serveur, relais, IA.")
        if (!cfg.relaisConfigure) Explication("Il faut que le relais Element soit configuré sur cet appareil : c'est lui qui transmet la configuration, chiffrée.")
        else if (!montrer) Button(onClick = { montrer = true }, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Outlined.QrCode2, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Afficher le QR code")
        }
        if (montrer) AfficheurQr { montrer = false }
    }
    Section("Configurer cet appareil") {
        Explication("Scannez le QR code affiché par un appareil déjà configuré (téléphone ou app Mac).")
        ScannerAppairage(principal = false, applique = applique)
    }
    Explication("Sécurité : la configuration est chiffrée sur cet appareil ; la clé n'est que dans le QR code. Le relais la garde 3 minutes " +
        "en mémoire, et elle ne peut être retirée qu'une fois. Les captures d'écran sont bloquées pendant l'affichage.")
}

@Composable
private fun AfficheurQr(fermer: () -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    var session by remember { mutableStateOf<Appairage.Session?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var recupere by remember { mutableStateOf(false) }
    var maintenant by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var essai by remember { mutableStateOf(0) }

    // Pas de capture d'écran ni d'aperçu dans les applis récentes pendant que le QR code est visible
    DisposableEffect(Unit) {
        val w = ctx.activite()?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    // Code non utilisé : annulé en quittant l'écran
    DisposableEffect(session) {
        val s = session
        onDispose { if (s != null && !recupere) Thread { Appairage.annuler(cfg, s) }.start() }
    }

    LaunchedEffect(essai) {
        session = null; image = null; erreur = null; recupere = false
        try {
            val s = withContext(Dispatchers.IO) { Appairage.creer(cfg, Appairage.nomAppareil()) }
            image = withContext(Dispatchers.Default) { qrCode(s.lien) }
            session = s
            while (true) {
                delay(2_000)
                maintenant = System.currentTimeMillis()
                val (ok, expire) = withContext(Dispatchers.IO) { runCatching { Appairage.etat(cfg, s) }.getOrDefault(false to false) }
                if (ok) { recupere = true; image = null; break }
                if (expire || maintenant > s.expire) { image = null; break }
            }
        } catch (ex: Exception) { erreur = ex.message ?: ex.javaClass.simpleName }
    }

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val s = session
            val img = image
            when {
                erreur != null -> Text(erreur!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                recupere -> {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF2F9E44), modifier = Modifier.size(48.dp))
                    Text("Le nouvel appareil a reçu la configuration.", fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text("Ce QR code ne fonctionne plus.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s == null -> { CircularProgressIndicator(); Text("Préparation du QR code…", fontSize = 13.sp) }
                img == null -> Text("QR code expiré.", fontWeight = FontWeight.Medium)
                else -> {
                    Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                        Image(img, "QR code de connexion", contentScale = ContentScale.FillWidth, filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxWidth())
                    }
                    val reste = ((s.expire - maintenant) / 1000).coerceAtLeast(0)
                    Text("Scannez-le avec le nouvel appareil · encore %d:%02d".format(reste / 60, reste % 60),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Text("Ne le montrez à personne d'autre : il donne accès à vos messages.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!recupere && (erreur != null || (s != null && image == null))) Button(onClick = { essai++ }) { Text("Nouveau code") }
                OutlinedButton(onClick = fermer) { Text(if (recupere) "Terminé" else "Fermer") }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
