package fr.messagerie.unifiee.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Affichage
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.EtatSource
import fr.messagerie.unifiee.MiseAJour
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Ollama
import fr.messagerie.unifiee.ServiceVeille
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Page(val titre: String, val icone: ImageVector) {
    Matrix("Serveur Matrix", Icons.Outlined.Dns),
    Element("Relais Element", Icons.Outlined.Hub),
    IA("Intelligence artificielle", Icons.Outlined.AutoAwesome),
    Affichage("Affichage et taille", Icons.Outlined.TextFields),
    Notifications("Notifications et actualisation", Icons.Outlined.Notifications),
    Confidentialite("Confidentialité", Icons.Outlined.Shield),
    Fichier("Fichier de configuration", Icons.Outlined.Description),
    MiseAJour("Mise à jour de l'appli", Icons.Outlined.SystemUpdate),
}

/** État de la vérification des mises à jour, gardé tant que l'appli tourne. */
private object EtatMaj {
    var derniere by mutableStateOf<MiseAJour.Version?>(null)
    var erreur by mutableStateOf<String?>(null)
    var verifie by mutableStateOf(false)
    var progression by mutableStateOf<Float?>(null)
}

/**
 * Réglages : un menu de rubriques, chacune sur sa propre page.
 * Chaque changement est enregistré aussitôt ; les connexions sont relancées en quittant les Réglages.
 */
@Composable
fun EcranReglages(e: Etat, retour: (() -> Unit)?) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var connexionsModifiees by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }       // +1 après un import : les pages relisent les réglages

    fun quitter() {
        if (connexionsModifiees) Moteur.redemarrer()
        ServiceVeille.mettreAJour(ctx)
        connexionsModifiees = false
        retour?.invoke()
    }
    BackHandler(enabled = page != null || retour != null) { if (page != null) page = null else quitter() }

    LaunchedEffect(Unit) { if (cfg.verifierMaj && !EtatMaj.verifie) verifierMaj(ctx) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            else (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        label = "reglages",
    ) { p ->
        if (p == null) Menu(e, retour?.let { { quitter() } }, ouvrir = { page = it })
        else PageReglage(p.titre, retour = { page = null }) {
            when (p) {
                Page.Matrix -> PageMatrix(version) { connexionsModifiees = true }
                Page.Element -> PageElement(version) { connexionsModifiees = true }
                Page.IA -> PageIA(version)
                Page.Affichage -> PageAffichage()
                Page.Notifications -> PageNotifications(version)
                Page.Confidentialite -> PageConfidentialite(version)
                Page.Fichier -> PageFichier { version++; connexionsModifiees = true }
                Page.MiseAJour -> PageMiseAJour()
            }
        }
    }
}

private suspend fun verifierMaj(ctx: android.content.Context) {
    EtatMaj.erreur = null
    try { EtatMaj.derniere = MiseAJour.derniere() } catch (ex: Exception) { EtatMaj.erreur = ex.message ?: ex.javaClass.simpleName }
    EtatMaj.verifie = true
}

private fun majDisponible(ctx: android.content.Context) =
    EtatMaj.derniere?.let { it.numero > MiseAJour.versionInstallee(ctx) } == true

// MARK: Menu principal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Menu(e: Etat, retour: (() -> Unit)?, ouvrir: (Page) -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Réglages") },
            navigationIcon = { if (retour != null) IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (retour == null) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Bienvenue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text("Le plus simple : « Fichier de configuration » puis « Importer… ». " +
                            "Sinon, renseignez le serveur Matrix et/ou le relais Element. Rien n'est envoyé ailleurs que vers vos serveurs.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                    }
                }
            }

            if (majDisponible(ctx)) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth().clickable { ouvrir(Page.MiseAJour) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nouvelle version ${EtatMaj.derniere!!.nom}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Toucher pour l'installer", fontSize = 13.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            fun resume(s: Source) = when (val st = e.sources[s]) {
                is EtatSource.Ok -> "Connecté"
                is EtatSource.Erreur -> "Erreur : ${st.message}"
                else -> null
            }
            val sourcesMatrix = listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM, Source.LINKEDIN)
            val okMatrix = sourcesMatrix.count { e.sources[it] is EtatSource.Ok }

            Groupe("Connexions") {
                Rubrique(Page.Matrix, when {
                    !cfg.matrixConfigure -> "Non configuré"
                    okMatrix > 0 -> "Connecté · $okMatrix messagerie(s) : " + sourcesMatrix.filter { e.sources[it] is EtatSource.Ok }.joinToString(", ") { it.libelle }
                    else -> sourcesMatrix.firstNotNullOfOrNull { resume(it) } ?: "Configuré"
                }, ouvrir)
                Rubrique(Page.Element, if (!cfg.relaisConfigure) "Non configuré" else resume(Source.ELEMENT) ?: "Configuré", ouvrir)
                Rubrique(Page.IA, if (cfg.ollamaConfigure) "Résumés et suggestions · ${cfg.ollamaModele}" else "Désactivée", ouvrir)
            }
            Groupe("Préférences") {
                Rubrique(Page.Affichage, "Interface ${Affichage.zoom} % · texte ${Affichage.texte} %", ouvrir)
                Rubrique(Page.Notifications, (if (cfg.notifications) "Notifications activées" else "Notifications coupées") +
                    (if (cfg.veilleArrierePlan) " · à l'écoute en arrière-plan" else ""), ouvrir)
                Rubrique(Page.Confidentialite, if (cfg.envoyerReactions) "Réactions transmises · aucun accusé de lecture" else "Réactions gardées sur le téléphone", ouvrir)
            }
            Groupe("Appli") {
                val n = cfg.imports.size
                Rubrique(Page.Fichier, "Importer, exporter" + if (n > 0) " · $n import(s) mémorisé(s)" else "", ouvrir)
                Rubrique(Page.MiseAJour, when {
                    majDisponible(ctx) -> "Version ${EtatMaj.derniere!!.nom} disponible"
                    EtatMaj.derniere != null -> "Version ${MiseAJour.nomInstalle(ctx)} · à jour"
                    else -> "Version ${MiseAJour.nomInstalle(ctx)}"
                }, ouvrir, pastille = majDisponible(ctx))
            }

            if (e.statistiques.isNotEmpty()) Groupe("Messages archivés") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Source.entries.forEach { s ->
                        val st = e.statistiques[s] ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PastilleSource(s, 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(s.libelle, modifier = Modifier.weight(1f))
                            Text("${st.first} conv. · ${st.second} messages", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (retour == null) Button(
                onClick = {
                    Moteur.redemarrer(); ServiceVeille.mettreAJour(ctx)
                    (ctx as? android.app.Activity)?.recreate()
                },
                enabled = cfg.configure,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp), shape = RoundedCornerShape(14.dp),
            ) { Text(if (cfg.configure) "Commencer" else "Renseignez d'abord une connexion") }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun Groupe(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Text(titre, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(content = contenu)
    }
}

@Composable
private fun Rubrique(p: Page, resume: String, ouvrir: (Page) -> Unit, pastille: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().clickable { ouvrir(p) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(p.icone, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.titre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(resume, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (pastille) Box(Modifier.size(10.dp).padding(end = 0.dp)) {
            Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.error, modifier = Modifier.size(10.dp)) {}
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// MARK: Pages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageReglage(titre: String, retour: () -> Unit, contenu: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(titre) },
            navigationIcon = { IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } })
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = contenu,
        )
    }
}

@Composable
private fun Explication(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun PageMatrix(version: Int, modifie: () -> Unit) {
    val cfg = Moteur.cfg
    var urls by remember(version) { mutableStateOf(cfg.matrixUrls) }
    var user by remember(version) { mutableStateOf(cfg.matrixUtilisateur) }
    var jeton by remember(version) { mutableStateOf(cfg.matrixJeton) }
    var moi by remember(version) { mutableStateOf(cfg.moiFantome) }
    Explication("Passerelles SMS, WhatsApp, Telegram et LinkedIn de votre serveur. Enregistré automatiquement ; la connexion est relancée en quittant les Réglages.")
    Champ("Adresse(s) du serveur", urls, "http://serveur:8008  (plusieurs : séparées par des virgules)") { urls = it; cfg.matrixUrls = it; modifie() }
    Champ("Identifiant Matrix", user, "@utilisateur:domaine") { user = it; cfg.matrixUtilisateur = it; modifie() }
    Champ("Jeton d'accès", jeton, "", secret = true) { jeton = it; cfg.matrixJeton = it; modifie() }
    Champ("Fantôme « moi » (optionnel)", moi, "@gmessages_…:domaine — détecté automatiquement sinon") { moi = it; cfg.moiFantome = it; modifie() }
}

@Composable
private fun PageElement(version: Int, modifie: () -> Unit) {
    val cfg = Moteur.cfg
    var urls by remember(version) { mutableStateOf(cfg.relaisUrls) }
    var jeton by remember(version) { mutableStateOf(cfg.relaisJeton) }
    Explication("Relais de votre serveur pour Element (et la correspondance des salons WhatsApp).")
    Champ("Adresse(s) du relais", urls, "http://serveur:8787") { urls = it; cfg.relaisUrls = it; modifie() }
    Champ("Jeton du relais", jeton, "", secret = true) { jeton = it; cfg.relaisJeton = it; modifie() }
}

@Composable
private fun PageIA(version: Int) {
    val cfg = Moteur.cfg
    val portee = rememberCoroutineScope()
    var urls by remember(version) { mutableStateOf(cfg.ollamaUrls) }
    var modele by remember(version) { mutableStateOf(cfg.ollamaModele) }
    var jours by remember(version) { mutableIntStateOf(cfg.jours) }
    var test by remember { mutableStateOf<String?>(null) }
    Explication("Ollama sur votre serveur : résumés, réponses suggérées et amélioration du texte. Laissez l'adresse vide pour désactiver.")
    Champ("Adresse(s) d'Ollama", urls, "http://serveur:11434") { urls = it; cfg.ollamaUrls = it }
    Champ("Modèle", modele, "qwen3:8b") { modele = it; cfg.ollamaModele = it }
    OutlinedButton(onClick = {
        test = "Test…"
        portee.launch { test = withContext(Dispatchers.IO) { Ollama(cfg).tester() } }
    }) { Text("Tester la connexion") }
    test?.let { Text(it, fontSize = 13.sp) }
    HorizontalDivider()
    Text("Résumer les conversations des :")
    Choix(listOf(1 to "24 h", 3 to "3 jours", 7 to "7 jours", 14 to "14 jours"), jours) { jours = it; cfg.jours = it; Moteur.redemarrer() }
}

@Composable
private fun PageAffichage() {
    val cfg = Moteur.cfg
    Explication("Réduisez ou agrandissez l'appli. Le changement s'applique tout de suite.")
    Text("Taille de l'interface", fontWeight = FontWeight.Medium)
    Choix(listOf(80 to "80 %", 90 to "90 %", 100 to "100 %", 110 to "110 %", 120 to "120 %"), Affichage.zoom) {
        Affichage.zoom = it; cfg.zoom = it
    }
    Text("Taille du texte", fontWeight = FontWeight.Medium)
    Choix(listOf(85 to "Petit", 100 to "Normal", 115 to "Grand", 130 to "Très grand"), Affichage.texte) {
        Affichage.texte = it; cfg.tailleTexte = it
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Aperçu", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Bonjour ! On se voit demain à 10 h ?")
        }
    }
    TextButton(onClick = { Affichage.zoom = 100; Affichage.texte = 100; cfg.zoom = 100; cfg.tailleTexte = 100 }) { Text("Revenir à la taille d'origine") }
}

@Composable
private fun PageNotifications(version: Int) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    var intervalle by remember(version) { mutableIntStateOf(cfg.intervalle) }
    var notifs by remember(version) { mutableStateOf(cfg.notifications) }
    var veille by remember(version) { mutableStateOf(cfg.veilleArrierePlan) }
    Interrupteur("Notifier quand une réponse est attendue", notifs) { notifs = it; cfg.notifications = it }
    Interrupteur("Rester à l'écoute en arrière-plan", veille, "Affiche une notification permanente ; utile pour être prévenu appli fermée.") {
        veille = it; cfg.veilleArrierePlan = it; ServiceVeille.mettreAJour(ctx)
    }
    HorizontalDivider()
    Text("Vérification de secours toutes les :")
    Explication("Les nouveaux messages arrivent en temps réel ; cette vérification sert de filet de sécurité.")
    Choix(listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min", 900 to "15 min"), intervalle) { intervalle = it; cfg.intervalle = it }
}

@Composable
private fun PageConfidentialite(version: Int) {
    val cfg = Moteur.cfg
    var reactions by remember(version) { mutableStateOf(cfg.envoyerReactions) }
    Interrupteur("Transmettre mes réactions aux contacts", reactions,
        "Désactivé, vos émoticônes restent sur ce téléphone. Rien ne part sans votre appui.") { reactions = it; cfg.envoyerReactions = it }
    HorizontalDivider()
    Explication("Aucun accusé de lecture ni indicateur de saisie n'est envoyé : lu / non lu reste sur ce téléphone. " +
        "Aucun message ne part sans votre appui sur « Envoyer ».")
}

@Composable
private fun PageFichier(importe: () -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var imports by remember { mutableStateOf(cfg.imports) }

    fun appliquer(nom: String, texte: String) {
        message = try {
            val faits = cfg.importerJson(texte)
            if (faits.isEmpty()) "Ce fichier ne contient aucun réglage reconnu."
            else {
                cfg.memoriserImport(nom, faits, texte)
                imports = cfg.imports
                Affichage.charger(cfg)
                importe()
                "Importé : ${faits.joinToString(", ")}. Les connexions seront relancées en quittant les Réglages."
            }
        } catch (ex: Exception) { "Fichier illisible : ${ex.message ?: ex.javaClass.simpleName}" }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val texte = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            appliquer(nomFichier(ctx, uri), texte)
        } catch (ex: Exception) { message = "Fichier illisible : ${ex.message ?: ex.javaClass.simpleName}" }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = try {
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(cfg.exporterJson().toByteArray()) }
            "Configuration exportée. Ce fichier contient vos jetons : gardez-le pour vous."
        } catch (ex: Exception) { "Export impossible : ${ex.message ?: ex.javaClass.simpleName}" }
    }

    Explication("Remplissez tout d'un coup avec un fichier .json (messagerie-config.json, ou le client.json / element.json de l'app Mac). " +
        "Chaque import est mémorisé ici pour pouvoir le réappliquer sans rechercher le fichier.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Importer…") }
        OutlinedButton(onClick = { exporter.launch("messagerie-config.json") }) { Text("Exporter…") }
    }
    message?.let { Text(it, fontSize = 13.sp) }

    if (imports.isNotEmpty()) {
        Text("Imports mémorisés", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        val f = SimpleDateFormat("d MMM yyyy 'à' HH:mm", Locale.FRANCE)
        imports.forEach { i ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 4.dp)) {
                    Text(i.nom, fontWeight = FontWeight.Medium)
                    Text(f.format(Date(i.date)) + " · " + i.faits, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { cfg.oublierImport(i); imports = cfg.imports }) { Text("Oublier") }
                        TextButton(onClick = { appliquer(i.nom, i.contenu) }) { Text("Réappliquer") }
                    }
                }
            }
        }
        Explication("Ces copies restent dans les données privées de l'appli (comme vos réglages). Elles sont conservées lors des mises à jour.")
    }
}

private fun nomFichier(ctx: android.content.Context, uri: Uri): String =
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "configuration.json"

@Composable
private fun PageMiseAJour() {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    val portee = rememberCoroutineScope()
    var auto by remember { mutableStateOf(cfg.verifierMaj) }
    var enCours by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val d = EtatMaj.derniere
    val dispo = majDisponible(ctx)

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Version installée : ${MiseAJour.nomInstalle(ctx)}", fontWeight = FontWeight.Medium)
            Text(when {
                enCours && EtatMaj.progression == null -> "Recherche…"
                d == null && EtatMaj.erreur != null -> "Impossible de vérifier : ${EtatMaj.erreur}"
                d == null -> "Pas encore vérifié"
                dispo -> "Nouvelle version disponible : ${d.nom}" + if (d.taille > 0) " (%.1f Mo)".format(d.taille / 1_048_576.0) else ""
                else -> "Vous avez la dernière version (${d.nom})."
            }, color = if (dispo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (dispo && d!!.notes.isNotEmpty()) Text(d.notes, fontSize = 13.sp, maxLines = 8)
        }
    }

    EtatMaj.progression?.let { p ->
        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        Text("Téléchargement… ${(p * 100).toInt()} %", fontSize = 13.sp)
    }

    if (dispo) Button(
        enabled = !enCours,
        onClick = {
            if (!MiseAJour.peutInstaller(ctx)) {
                message = "Autorisez « Messagerie » à installer des applis, puis revenez et touchez à nouveau ce bouton."
                MiseAJour.demanderAutorisation(ctx)
                return@Button
            }
            enCours = true; message = null
            portee.launch {
                try {
                    EtatMaj.progression = 0f
                    val f = MiseAJour.telecharger(ctx, d!!) { EtatMaj.progression = it }
                    EtatMaj.progression = null
                    MiseAJour.installer(ctx, f)
                    message = "Confirmez l'installation dans la fenêtre d'Android. Vos réglages et messages sont conservés."
                } catch (ex: Exception) {
                    EtatMaj.progression = null
                    message = "Échec : ${ex.message ?: ex.javaClass.simpleName}"
                }
                enCours = false
            }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
    ) { Text("Télécharger et installer ${d!!.nom}") }

    OutlinedButton(
        enabled = !enCours,
        onClick = { enCours = true; portee.launch { verifierMaj(ctx); enCours = false } },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Rechercher une mise à jour") }

    message?.let { Text(it, fontSize = 13.sp) }
    HorizontalDivider()
    Interrupteur("Vérifier à l'ouverture des Réglages", auto, "Consulte seulement la page publique des versions sur GitHub.") { auto = it; cfg.verifierMaj = it }
}

// MARK: Composants

@Composable private fun Champ(libelle: String, valeur: String, aide: String, secret: Boolean = false, maj: (String) -> Unit) {
    OutlinedTextField(
        value = valeur, onValueChange = maj, label = { Text(libelle) },
        placeholder = { if (aide.isNotEmpty()) Text(aide, fontSize = 12.sp) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable private fun Choix(options: List<Pair<Int, String>>, valeur: Int, maj: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, l) -> FilterChip(selected = v == valeur, onClick = { maj(v) }, label = { Text(l, fontSize = 13.sp) }) }
    }
}

@Composable private fun Interrupteur(libelle: String, valeur: Boolean, aide: String? = null, maj: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { maj(!valeur) }.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(libelle)
            if (aide != null) Text(aide, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = valeur, onCheckedChange = maj)
    }
}
