package fr.messagerie.unifiee

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Appel vidéo Jitsi Meet DANS l'appli (page web Jitsi avec caméra et micro).
 * En secours : bouton « Ouvrir dans le navigateur » (ou l'appli Jitsi si elle est installée).
 */
class AppelJitsi : ComponentActivity() {
    companion object {
        private const val LIEN = "lien"
        fun ouvrir(ctx: Context, lien: String) {
            ctx.startActivity(Intent(ctx, AppelJitsi::class.java).putExtra(LIEN, lien).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        /** Nouveau lien d'appel, au hasard, sur le serveur Jitsi choisi dans les Réglages. */
        fun nouveauLien(cfg: Config): String {
            val lettres = "abcdefghijkmnpqrstuvwxyz23456789"
            return cfg.jitsi + "/Messagerie-" + (1..12).map { lettres.random() }.joinToString("")
        }
    }

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lien = intent.getStringExtra(LIEN) ?: run { finish(); return }
        setContent { Theme { EcranAppel(lien) } }
    }

    override fun onDestroy() {
        webView?.apply { loadUrl("about:blank"); destroy() }   // raccroche : coupe caméra et micro
        webView = null
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun EcranAppel(lien: String) {
        val requis = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        var pret by remember { mutableStateOf(requis.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) }
        var demande by remember { mutableStateOf(false) }
        val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { pret = true; demande = true }
        LaunchedEffect(Unit) { if (!pret) lanceur.launch(requis) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Appel vidéo", fontSize = 17.sp)
                            Text(lien.substringAfter("://"), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            getSystemService(android.content.ClipboardManager::class.java)
                                .setPrimaryClip(android.content.ClipData.newPlainText("Lien d'appel", lien))
                            android.widget.Toast.makeText(this@AppelJitsi, "Lien copié", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Outlined.ContentCopy, "Copier le lien") }
                        IconButton(onClick = {
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lien)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            finish()
                        }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Ouvrir dans le navigateur") }
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Outlined.CallEnd, "Raccrocher", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            },
        ) { pad ->
            if (pret) AndroidView(
                modifier = Modifier.padding(pad).fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.databaseEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                val u = req.url
                                // Liens vers l'appli Jitsi ou le magasin : on reste dans la page
                                return u.scheme != "https" && u.scheme != "http"
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) {
                                // Caméra et micro accordés seulement à la page de l'appel
                                val hote = Uri.parse(lien).host
                                if (request.origin.host == hote) request.grant(request.resources) else request.deny()
                            }
                        }
                        // Sans renvoi vers l'appli Jitsi : l'appel s'ouvre directement dans la page
                        loadUrl("$lien#config.disableDeepLinking=true&config.prejoinConfig.enabled=true")
                    }
                },
            ) else Text("Autorisez la caméra et le micro pour l'appel vidéo.", modifier = Modifier.padding(pad).padding(24.dp))
        }
    }
}
