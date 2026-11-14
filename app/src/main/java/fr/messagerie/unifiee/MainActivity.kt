package fr.messagerie.unifiee

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.messagerie.unifiee.ui.EcranConversation
import fr.messagerie.unifiee.ui.EcranListe
import fr.messagerie.unifiee.ui.EcranReglages
import fr.messagerie.unifiee.ui.Filtre

class MainActivity : ComponentActivity() {
    private val cleDemandee = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cleDemandee.value = intent?.getStringExtra("cle")
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { Theme { Racine(cleDemandee) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("cle")?.let { cleDemandee.value = it }
    }

    override fun onResume() {
        super.onResume()
        if (Moteur.cfg.configure) {
            Moteur.demarrer()
            Moteur.actualiserMaintenant()
        }
        ServiceVeille.mettreAJour(this)
    }
}

@Composable
fun Theme(contenu: @Composable () -> Unit) {
    val sombre = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val couleurs = when {
        Build.VERSION.SDK_INT >= 31 -> if (sombre) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        sombre -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = couleurs, content = contenu)
}

@Composable
private fun Racine(cleDemandee: androidx.compose.runtime.MutableState<String?>) {
    val etat by Moteur.etat.collectAsState()
    var reglages by rememberSaveable { mutableStateOf(!Moteur.cfg.configure) }
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    var filtre by remember { mutableStateOf<Filtre>(Filtre.ATraiter) }

    val demande = cleDemandee.value
    androidx.compose.runtime.LaunchedEffect(demande) {
        if (demande != null) { selection = demande; filtre = Filtre.Tout; reglages = false; cleDemandee.value = null }
    }

    val conv = selection?.let { s -> etat.conversations.firstOrNull { it.cle == s } }
    when {
        reglages -> {
            BackHandler(enabled = Moteur.cfg.configure) { reglages = false }
            EcranReglages(etat, retour = if (Moteur.cfg.configure) ({ reglages = false }) else null)
        }
        conv != null -> {
            BackHandler { selection = null }
            EcranConversation(etat, conv, retour = { selection = null })
        }
        else -> EcranListe(
            etat, filtre, onFiltre = { filtre = it },
            ouvrir = { selection = it }, ouvrirReglages = { reglages = true },
            selection = selection,
        )
    }
}
