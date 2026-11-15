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
import androidx.compose.ui.graphics.Color
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
        Affichage.charger(Moteur.cfg)
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
    // Palette de la marque (indigo), identique sur tous les téléphones.
    val couleurs = if (sombre) darkColorScheme(
        primary = Color(0xFFB9C3FF), onPrimary = Color(0xFF14267B), primaryContainer = Color(0xFF2E3F92), onPrimaryContainer = Color(0xFFDDE1FF),
        secondary = Color(0xFFC3C5DD), secondaryContainer = Color(0xFF424659), onSecondaryContainer = Color(0xFFDFE1F9),
        background = Color(0xFF111318), surface = Color(0xFF111318),
        surfaceContainer = Color(0xFF1D1F25), surfaceContainerHigh = Color(0xFF282A2F), surfaceContainerLow = Color(0xFF191B20),
    ) else lightColorScheme(
        primary = Color(0xFF3B5BDB), onPrimary = Color.White, primaryContainer = Color(0xFFE3E9FC), onPrimaryContainer = Color(0xFF1B2F86),
        secondary = Color(0xFF5A606D), secondaryContainer = Color(0xFFE6E8EE), onSecondaryContainer = Color(0xFF15171C),
        tertiaryContainer = Color(0xFFFDEEDC), onTertiaryContainer = Color(0xFFA04A0B),
        background = Color(0xFFF6F7F9), surface = Color(0xFFF6F7F9), onSurface = Color(0xFF15171C), onSurfaceVariant = Color(0xFF5A606D),
        surfaceVariant = Color(0xFFECEEF2), outlineVariant = Color(0xFFECEEF2),
        surfaceContainer = Color(0xFFEFF1F4), surfaceContainerHigh = Color(0xFFE9EBEF), surfaceContainerLow = Color.White,
    )
    val d = androidx.compose.ui.platform.LocalDensity.current
    val echelle = androidx.compose.ui.unit.Density(d.density * Affichage.zoom / 100f, d.fontScale * Affichage.texte / 100f)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides echelle) {
        MaterialTheme(colorScheme = couleurs, content = contenu)
    }
}

/** Échelle d'affichage choisie dans les Réglages (appliquée tout de suite). */
object Affichage {
    var zoom by androidx.compose.runtime.mutableIntStateOf(100)
    var texte by androidx.compose.runtime.mutableIntStateOf(100)
    fun charger(c: Config) { zoom = c.zoom; texte = c.tailleTexte }
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
