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
import fr.messagerie.unifiee.ui.ConversationVM
import fr.messagerie.unifiee.ui.ListeVM
import fr.messagerie.unifiee.ui.EcranNouveau
import fr.messagerie.unifiee.ui.EcranRecherche
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    private val cleDemandee = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cleDemandee.value = intent?.getStringExtra("cle")
        lireRaccourci(intent)
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
        lireRaccourci(intent)
    }

    /** Raccourci « messagerie » de l'icône : ouvre la liste filtrée sur cette messagerie. */
    private fun lireRaccourci(i: Intent?) {
        val s = i?.getStringExtra("source")?.let { n -> Source.entries.firstOrNull { it.name == n } } ?: return
        Raccourcis.sourceDemandee.value = s
    }

    override fun onPause() {
        super.onPause()
        Moteur.auPremierPlan = false
    }

    override fun onResume() {
        super.onResume()
        Moteur.auPremierPlan = true
        if (Moteur.cfg.configure) {
            Moteur.demarrer()
            Moteur.actualiserMaintenant()
        }
        ServiceVeille.mettreAJour(this)
    }
}

@Composable
fun Theme(contenu: @Composable () -> Unit) {
    val sombre = when (Affichage.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
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
    val base = if (Affichage.dynamique && Build.VERSION.SDK_INT >= 31)
        (if (sombre) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)) else couleurs
    // Couleur principale choisie dans Réglages > Apparence
    val schema = Affichage.accent.takeIf { it != 0L && !Affichage.dynamique }?.let { a ->
        val c = Color(a)
        if (sombre) base.copy(
            primary = androidx.compose.ui.graphics.lerp(c, Color.White, 0.35f), onPrimary = Color(0xFF111318),
            primaryContainer = androidx.compose.ui.graphics.lerp(c, Color(0xFF111318), 0.55f), onPrimaryContainer = Color.White)
        else base.copy(
            primary = c, onPrimary = Color.White,
            primaryContainer = androidx.compose.ui.graphics.lerp(c, Color.White, 0.85f), onPrimaryContainer = androidx.compose.ui.graphics.lerp(c, Color.Black, 0.45f))
    } ?: base
    val d = androidx.compose.ui.platform.LocalDensity.current
    val echelle = androidx.compose.ui.unit.Density(d.density * Affichage.zoom / 100f, d.fontScale * Affichage.texte / 100f)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides echelle) {
        MaterialTheme(colorScheme = schema, content = contenu)
    }
}

/** Échelle d'affichage choisie dans les Réglages (appliquée tout de suite). */
object Affichage {
    var zoom by androidx.compose.runtime.mutableIntStateOf(100)
    var texte by androidx.compose.runtime.mutableIntStateOf(100)
    var dynamique by mutableStateOf(false)
    var theme by androidx.compose.runtime.mutableIntStateOf(0)
    var accent by androidx.compose.runtime.mutableLongStateOf(0L)
    /** Présentation de la liste : 0 cartes, 1 compacte, 2 colorée, 3 aérée. */
    var design by androidx.compose.runtime.mutableIntStateOf(0)
    /** Haut de la liste : 0 classique, 1 Bandeau, 2 Épuré. */
    var haut by androidx.compose.runtime.mutableIntStateOf(1)
    /** Couleurs choisies par messagerie (absentes = couleur d'origine). */
    val couleurs = androidx.compose.runtime.mutableStateMapOf<Source, Long>()
    fun charger(c: Config) {
        zoom = c.zoom; texte = c.tailleTexte; dynamique = c.couleursDynamiques; theme = c.theme; accent = c.accent; design = c.design; haut = c.haut
        couleurs.clear(); Source.entries.forEach { s -> c.couleur(s).takeIf { it != 0L }?.let { couleurs[s] = it } }
    }
}

@Composable
private fun Racine(cleDemandee: androidx.compose.runtime.MutableState<String?>) {
    val etat by Moteur.etat.collectAsState()
    val nav = rememberNavController()
    val depart = remember { if (Moteur.cfg.configure) "liste" else "accueil" }

    val raccourci by Raccourcis.sourceDemandee.collectAsState()
    androidx.compose.runtime.LaunchedEffect(raccourci) {
        if (raccourci != null && Moteur.cfg.configure && nav.currentDestination?.route != "liste")
            nav.navigate("liste") { popUpTo("liste") { inclusive = false }; launchSingleTop = true }
    }

    // Ouverture depuis une notification
    val demande = cleDemandee.value
    androidx.compose.runtime.LaunchedEffect(demande) {
        if (demande != null && Moteur.cfg.configure) {
            nav.navigate("conversation/" + Uri.encode(demande)) { launchSingleTop = true }
            cleDemandee.value = null
        }
    }

    NavHost(
        navController = nav, startDestination = depart,
        enterTransition = { slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260)) },
        exitTransition = { slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(200)) },
        popEnterTransition = { slideInHorizontally(tween(260)) { -it / 8 } + fadeIn(tween(260)) },
        popExitTransition = { slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(200)) },
    ) {
        composable("liste") {
            val vm: ListeVM = viewModel()
            val demandee by Raccourcis.sourceDemandee.collectAsState()
            androidx.compose.runtime.LaunchedEffect(demandee) {
                demandee?.let { vm.source.value = it; vm.vue.value = fr.messagerie.unifiee.ui.Vue.Toutes; Raccourcis.sourceDemandee.value = null }
            }
            EcranListe(etat, vm,
                ouvrir = { nav.navigate("conversation/" + Uri.encode(it)) { launchSingleTop = true } },
                ouvrirReglages = { nav.navigate("reglages") { launchSingleTop = true } },
                nouveau = { nav.navigate("nouveau") { launchSingleTop = true } },
                rechercher = { q -> nav.navigate("recherche?q=" + Uri.encode(q)) { launchSingleTop = true } })
        }
        composable("recherche?q={q}", arguments = listOf(androidx.navigation.navArgument("q") { nullable = true; defaultValue = null })) { entree ->
            EcranRecherche(etat, entree.arguments?.getString("q").orEmpty(), retour = { nav.popBackStack() },
                ouvrir = { conv, q, m -> nav.navigate("conversation/" + Uri.encode(conv) + "?q=" + Uri.encode(q) + "&m=" + Uri.encode(m)) })
        }
        composable("nouveau") {
            EcranNouveau(etat, retour = { nav.popBackStack() }, ouvrir = { cle ->
                nav.navigate("conversation/" + Uri.encode(cle)) { popUpTo("nouveau") { inclusive = true } }
            })
        }
        composable("conversation/{cle}?q={q}&m={m}", arguments = listOf(
            androidx.navigation.navArgument("q") { nullable = true; defaultValue = null },
            androidx.navigation.navArgument("m") { nullable = true; defaultValue = null })) { entree ->
            val cle = entree.arguments?.getString("cle") ?: ""
            val conv = etat.conversations.firstOrNull { it.cle == cle }
            if (conv == null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Text("Conversation introuvable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val vm: ConversationVM = viewModel(key = cle) { ConversationVM(cle) }
                EcranConversation(etat, conv, vm, retour = { nav.popBackStack() },
                    rechercheInitiale = entree.arguments?.getString("q")?.takeIf { it.isNotBlank() },
                    messageCible = entree.arguments?.getString("m"),
                    ouvrir = { nav.navigate("conversation/" + Uri.encode(it)) { launchSingleTop = true } })
            }
        }
        composable("reglages") {
            EcranReglages(etat, accueil = false, retour = { nav.popBackStack() })
        }
        composable("accueil") {
            EcranReglages(etat, accueil = true, retour = {
                nav.navigate("liste") { popUpTo("accueil") { inclusive = true } }
            })
        }
    }
}
