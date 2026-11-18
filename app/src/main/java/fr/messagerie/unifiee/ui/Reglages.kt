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
import androidx.compose.material.icons.outlined.Forum
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
import androidx.compose.runtime.key
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Palette
import fr.messagerie.unifiee.Plantages
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
    Messageries("Messageries", Icons.Outlined.Forum),
    Affichage("Apparence et couleurs", Icons.Outlined.Palette),
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
fun EcranReglages(e: Etat, accueil: Boolean, retour: () -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var connexionsModifiees by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }       // +1 après un import : les pages relisent les réglages

    fun quitter() {
        if (connexionsModifiees) Moteur.redemarrer()
        fr.messagerie.unifiee.SynchroArrierePlan.programmer(ctx)
        ServiceVeille.mettreAJour(ctx)
        connexionsModifiees = false
        retour()
    }
    BackHandler(enabled = page != null || Moteur.cfg.configure) { if (page != null) page = null else quitter() }

    LaunchedEffect(Unit) { if (cfg.verifierMaj && !EtatMaj.verifie) verifierMaj(ctx) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            else (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        label = "reglages",
    ) { p ->
        if (p == null) Menu(e, accueil, quitter = { quitter() }, ouvrir = { page = it })
        else PageReglage(p.titre, retour = { page = null }) {
            when (p) {
                Page.Matrix -> PageMatrix(version) { connexionsModifiees = true }
                Page.Element -> PageElement(version) { connexionsModifiees = true }
                Page.IA -> PageIA(version)
                Page.Messageries -> PageMessageries(e) { page = it }
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Menu(e: Etat, accueil: Boolean, quitter: () -> Unit, ouvrir: (Page) -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Réglages") },
            navigationIcon = { if (!accueil) IconButton(onClick = quitter) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (accueil) {
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

            RapportPlantage()

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
            val sourcesMatrix = listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM, Source.LINKEDIN, Source.SIGNAL)
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
                val actives = Source.entries.filter { e.statistiques[it] != null }
                Rubrique(Page.Messageries, if (actives.isEmpty()) "Pro / Perso, notifications, délai de réception"
                    else actives.joinToString(" · ") { it.id + if (e.pro.contains(it)) " (pro)" else "" }, ouvrir)
                Rubrique(Page.Affichage, listOf("Thème clair", "Thème sombre").getOrNull(Affichage.theme - 1).let { it ?: "Thème du téléphone" } +
                    " · texte ${Affichage.texte} %" + (if (Affichage.dynamique) " · Material You" else if (Affichage.accent != 0L) " · couleur perso" else ""), ouvrir)
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

            // Latence de réception : heure du message sur le serveur → arrivée sur ce téléphone
            val latences by androidx.compose.runtime.produceState(emptyList<Pair<Source, List<Long>>>(), e.derniereActualisation) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Source.entries.mapNotNull { src -> Moteur.delais(src).takeIf { it.isNotEmpty() }?.let { src to it } }
                }
            }
            Groupe("Latence de réception (serveur → téléphone)") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (latences.isEmpty()) Text("Pas encore mesurée : elle s'affichera après les prochains messages reçus.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latences.forEach { (src, _) -> BlocLatence(src, e.derniereActualisation) }
                    if (latences.isNotEmpty()) Text("Médiane sur les 50 derniers messages reçus « en direct » (imports d'historique exclus). " +
                        "« 9 sur 10 » : 90 % des messages arrivent plus vite que ce délai.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Appli fermée, Android ne la réveille que toutes les 15 min environ : d'où des délais de plusieurs minutes.
                    if (latences.any { it.second.average() > 60_000 } && !cfg.veilleArrierePlan) {
                        Text("Délai élevé : appli fermée, Android ne vérifie que toutes les 15 min environ. Pour recevoir en quelques secondes, " +
                            "activez « à l'écoute en arrière-plan » (Notifications) et retirez l'appli des économies de batterie.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { ouvrir(Page.Notifications) }) { Text("Réglage Notifications") }
                            TextButton(onClick = {
                                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }) { Text("Économies de batterie") }
                        }
                    }
                }
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

            if (accueil) Button(
                onClick = { Moteur.redemarrer(); quitter() },
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
internal fun Explication(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
    HorizontalDivider()
    var teams by remember(version) { mutableStateOf(cfg.teamsUrls) }
    var avecTeams by remember(version) { mutableStateOf(cfg.afficherTeams) }
    Interrupteur("Afficher Teams (lecture seule)", avecTeams, "Service Teams du serveur, même jeton que le relais.") { avecTeams = it; cfg.afficherTeams = it; modifie() }
    if (avecTeams) {
        Champ("Adresse(s) du service Teams (facultatif)", teams, "vide = hôte du relais, port 8789") { teams = it; cfg.teamsUrls = it; modifie() }
        val portee = rememberCoroutineScope()
        var test by remember { mutableStateOf<String?>(null) }
        Explication("Adresses essayées : " + cfg.teamsAdresses.joinToString(", ").ifEmpty { "aucune (renseignez le relais)" })
        OutlinedButton(onClick = {
            test = "Test…"
            portee.launch { test = withContext(Dispatchers.IO) { fr.messagerie.unifiee.Teams.derniereBonne = null; fr.messagerie.unifiee.Teams(cfg).tester() } }
        }) { Text("Tester Teams") }
        test?.let { Text(it, fontSize = 13.sp) }
    }
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

/** Médiane et « 9 sur 10 » (90e centile) d'une liste de délais. */
private fun centile(l: List<Long>, q: Double): Long = l.sorted().let { t -> t[((t.size - 1) * q).toInt().coerceIn(0, t.size - 1)] }

/**
 * Latence d'une messagerie : total (envoi sur le réseau officiel → ce téléphone), découpé quand c'est mesuré
 * en réseau officiel → serveur (passerelle du MacBook Pro) et serveur → téléphone.
 */
@Composable
fun BlocLatence(src: Source, cle: Any?) {
    val donnees by androidx.compose.runtime.produceState(Triple(emptyList<Long>(), emptyList<Long>(), emptyList<Long>()), src, cle) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Triple(Moteur.delais(src), Moteur.delaisPasserelle(src), Moteur.delaisTelephone(src))
        }
    }
    val (total, pont, tel) = donnees
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoSource(src, 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(src.libelle, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (total.isNotEmpty()) Text(duree(centile(total, 0.5)), fontWeight = FontWeight.Bold,
                color = when { centile(total, 0.5) < 10_000 -> Color(0xFF2F9E44); centile(total, 0.5) < 120_000 -> Color(0xFFE8590C); else -> cs.error })
        }
        if (total.isEmpty()) { Text("Pas encore mesurée", fontSize = 12.sp, color = cs.onSurfaceVariant); return@Column }
        val nomReseau = when (src) { Source.SMS -> "Réseau mobile"; Source.ELEMENT -> "Serveur Element"; Source.TEAMS -> "Teams"; else -> src.id }
        if (pont.isNotEmpty() && tel.isNotEmpty() && pont.any { it > 0 }) {
            val a = centile(pont, 0.5).coerceAtLeast(0); val b = centile(tel, 0.5).coerceAtLeast(0)
            val part = if (a + b > 0) (a.toFloat() / (a + b)).coerceIn(0.04f, 0.96f) else 0.5f
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                Box(Modifier.weight(part).fillMaxHeight().background(src.teinte()))
                Box(Modifier.weight(1f - part).fillMaxHeight().background(cs.primary))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(src.teinte(), CircleShape)); Spacer(Modifier.width(6.dp))
                Text("$nomReseau → serveur (MacBook Pro)", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(duree(a), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(cs.primary, CircleShape)); Spacer(Modifier.width(6.dp))
                Text("Serveur → ce téléphone", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(duree(b), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        } else Text("Depuis l'envoi sur $nomReseau jusqu'à ce téléphone (étape serveur pas encore mesurée)", fontSize = 12.sp, color = cs.onSurfaceVariant)
        Text("Médiane ${duree(centile(total, 0.5))} · 9 sur 10 en moins de ${duree(centile(total, 0.9))} · dernier ${duree(total.last())} · ${total.size} mesures",
            fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PageNotifications(version: Int) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    val e by Moteur.etat.collectAsState()
    var maj by remember { mutableIntStateOf(0) }
    var intervalle by remember(version) { mutableIntStateOf(cfg.intervalle) }
    var notifs by remember(version) { mutableStateOf(cfg.notifications) }
    var veille by remember(version) { mutableStateOf(cfg.veilleArrierePlan) }
    var fond by remember(version) { mutableStateOf(cfg.synchroArrierePlan) }

    EtatNotifications()
    HorizontalDivider()
    Text("Style des notifications", fontWeight = FontWeight.SemiBold)
    Explication("Comme WhatsApp ou Messages : photo du contact, boutons, bulle. S'applique aux prochaines notifications.")
    var bulles by remember(version) { mutableStateOf(cfg.notifBulles) }
    var boutons by remember(version) { mutableStateOf(cfg.notifBoutons) }
    var photo by remember(version) { mutableStateOf(cfg.notifPhoto) }
    var apercu by remember(version) { mutableIntStateOf(cfg.notifApercu) }
    var verrou by remember(version) { mutableIntStateOf(cfg.notifVerrouille) }
    Interrupteur("Photo du contact", photo, "Sinon, son initiale.") { photo = it; cfg.notifPhoto = it }
    Interrupteur("Boutons Répondre, Marquer comme lu, Silence", boutons) { boutons = it; cfg.notifBoutons = it }
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        Interrupteur("Bulles de conversation", bulles, "La conversation s'ouvre dans une bulle ronde par-dessus les autres applis (icône en bas à droite de la notification).") {
            bulles = it; cfg.notifBulles = it
        }
        if (bulles) TextButton(onClick = {
            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Autoriser les bulles dans Android") }
    }
    Text("Contenu affiché", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Choix(listOf(0 to "Le message", 1 to "« Nouveau message »"), apercu) { apercu = it; cfg.notifApercu = it }
    Text("Sur l'écran verrouillé", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Choix(listOf(0 to "Tout afficher", 1 to "Masquer le contenu", 2 to "Ne rien afficher"), verrou) { verrou = it; cfg.notifVerrouille = it }
    HorizontalDivider()
    Text("Par messagerie", fontWeight = FontWeight.SemiBold)
    Explication("« Réponse attendue » : seulement quand l'IA juge qu'on attend votre réponse.")
    key(maj) {
        Source.entries.filter { e.statistiques[it] != null }.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoSource(s, 26.dp); Spacer(Modifier.width(10.dp))
                Text(s.libelle, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            val modes = listOf("tous" to "Chaque message", "attendue" to "Réponse attendue", "aucun" to "Aucune")
            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (v, l) ->
                    SegmentedButton(selected = cfg.notif(s) == v, onClick = { cfg.definirNotif(s, v); maj++ },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, modes.size), icon = {},
                        label = { Text(l, fontSize = 12.sp, maxLines = 1) })
                }
            }
            // Son, vibration, pop-up, écran verrouillé : réglages Android du canal de cette messagerie
            if (android.os.Build.VERSION.SDK_INT >= 26) TextButton(onClick = {
                val canal = Moteur.canalMessages(s)
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, canal).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Son, vibration et pop-up ${s.id}") }
        }
    }
    HorizontalDivider()
    Text("Heures de silence", fontWeight = FontWeight.SemiBold)
    Explication("Les notifications arrivent sans son ni vibration pendant ces heures.")
    var debut by remember { mutableIntStateOf(cfg.silenceDebut) }
    var fin by remember { mutableIntStateOf(cfg.silenceFin) }
    Choix(listOf(-1 to "Désactivé", 20 to "Dès 20 h", 21 to "Dès 21 h", 22 to "Dès 22 h", 23 to "Dès 23 h"), debut) { debut = it; cfg.silenceDebut = it }
    if (debut >= 0) {
        Choix(listOf(6 to "Jusqu'à 6 h", 7 to "7 h", 8 to "8 h", 9 to "9 h"), fin) { fin = it; cfg.silenceFin = it }
        Text("Messageries concernées", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var majSilence by remember { mutableIntStateOf(0) }
        key(majSilence) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Source.entries.filter { e.statistiques[it] != null }.forEach { s ->
                    FilterChip(selected = cfg.silencePour(s), onClick = { cfg.definirSilencePour(s, !cfg.silencePour(s)); majSilence++ },
                        leadingIcon = { LogoSource(s, 18.dp) }, label = { Text(s.id, fontSize = 13.sp) })
                }
            }
        }
        Explication("Une messagerie décochée sonne normalement, même pendant les heures de silence.")
    }
    HorizontalDivider()
    Text("Réception", fontWeight = FontWeight.SemiBold)
    Interrupteur("Rester à l'écoute en arrière-plan", veille, "Messages reçus en quelques secondes, appli fermée. Affiche une notification permanente.") {
        veille = it; cfg.veilleArrierePlan = it; ServiceVeille.mettreAJour(ctx)
    }
    Interrupteur("Vérifier régulièrement appli fermée", fond, "Environ toutes les 15 min (géré par Android), sans notification permanente.") {
        fond = it; cfg.synchroArrierePlan = it; fr.messagerie.unifiee.SynchroArrierePlan.programmer(ctx)
    }
    Interrupteur("Notifier quand une réponse est attendue", notifs, "Selon le résumé de l'IA, même pour une messagerie en « Aucune ».") { notifs = it; cfg.notifications = it }
    TextButton(onClick = {
        runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { Text("Retirer l'appli des économies de batterie") }
    Text("Vérification de secours toutes les :")
    Choix(listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min", 900 to "15 min"), intervalle) { intervalle = it; cfg.intervalle = it }
    HorizontalDivider()
    Text("Chiffre sur l'icône", fontWeight = FontWeight.SemiBold)
    Explication("Le chiffre suit le total des messages non lus, et baisse dès que vous les lisez. Il est porté par une notification discrète " +
        "« N messages non lus » (sans son). Si l'icône montre un point, choisissez « Nombre » pour les pastilles d'icône dans les réglages Android.")
    TextButton(onClick = {
        runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }) { Text("Réglages Android des notifications (son, vibration, pastille)") }
    val sourdine = e.conversations.filter { e.etat(it).sourdine }
    if (sourdine.isNotEmpty()) {
        HorizontalDivider()
        Text("Conversations en sourdine (${sourdine.size})", fontWeight = FontWeight.SemiBold)
        sourdine.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoSource(c.source, 22.dp); Spacer(Modifier.width(10.dp))
                Text(c.nom, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = { Moteur.basculerSourdine(c) }) { Text("Réactiver") }
            }
        }
    }
}

private fun duree(ms: Long) = when {
    ms < 1000 -> "$ms ms"
    ms < 60_000 -> "%.0f s".format(ms / 1000.0)
    ms < 3_600_000 -> "${ms / 60_000} min ${(ms / 1000) % 60} s"
    else -> "${ms / 3_600_000} h ${(ms / 60_000) % 60} min"
}

/** Chaque messagerie : état de connexion, dernier message, Pro / Perso, couleur, latence. */
@Composable
private fun PageMessageries(e: Etat, ouvrir: (Page) -> Unit) {
    val cfg = Moteur.cfg
    var maj by remember { mutableIntStateOf(0) }
    Explication("Pro / Perso sert aux vues de la liste ; une conversation peut aussi être classée à la main (appui long sur la conversation).")
    val sources = Source.entries.filter { e.statistiques[it] != null || e.sources[it] is EtatSource.Ok || e.sources[it] is EtatSource.Erreur }
    if (sources.isEmpty()) Explication("Aucune messagerie connectée pour l'instant.")
    sources.forEach { s ->
        key(s, maj) {
            val st = e.sources[s]
            val dernier = e.conversations.filter { it.source == s }.maxOfOrNull { it.derniere }
            val nonLus = e.nombreMessagesNonLus(s)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoSource(s, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.libelle, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (st is EtatSource.Erreur) MaterialTheme.colorScheme.error else Color(0xFF2F9E44), CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(if (st is EtatSource.Erreur) "Erreur : ${st.message}"
                                    else "Synchronisée" + (dernier?.let { " · dernier message ${dateCourte(it)}" } ?: ""),
                                    fontSize = 12.sp, maxLines = 2,
                                    color = if (st is EtatSource.Erreur) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (nonLus > 0) Surface(shape = CircleShape, color = s.teinte()) {
                            Text("$nonLus", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    BlocConnexion(e, s)
                    e.statistiques[s]?.let { Text("${it.first} conversations · ${it.second} messages archivés", fontSize = 13.sp) }
                    Interrupteur("Ranger en « Pro »", cfg.pro(s), if (cfg.pro(s)) "Visible dans la vue Pro" else "Visible dans la vue Perso") {
                        cfg.definirPro(s, it); Moteur.chargerOrganisation(); maj++
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications : " + when (cfg.notif(s)) { "tous" -> "chaque message"; "attendue" -> "réponse attendue"; else -> "aucune" },
                            fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { ouvrir(Page.Notifications) }) { Text("Changer") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).background(s.teinte(), CircleShape)); Spacer(Modifier.width(8.dp))
                        Text("Couleur", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { ouvrir(Page.Affichage) }) { Text("Changer") }
                    }
                    HorizontalDivider()
                    BlocLatence(s, e.derniereActualisation)
                }
            }
        }
    }
}

@Composable
private fun LigneConnexion(couleur: Color, titre: String, detail: String?) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).background(couleur, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(titre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        }
    }
}

/** Connexion d'une messagerie : serveur joint, état de la passerelle (message de son robot), dernier message reçu. */
@Composable
private fun BlocConnexion(e: Etat, s: Source) {
    val cs = MaterialTheme.colorScheme
    val st = e.sources[s]
    val passerelle by androidx.compose.runtime.produceState<Moteur.EtatPasserelle?>(null, s, e.derniereActualisation) { value = Moteur.etatPasserelle(s) }
    val recu = e.conversations.filter { it.source == s }.mapNotNull { c -> c.messages.lastOrNull { !it.deMoi }?.date }.maxOrNull()
    val vert = Color(0xFF2F9E44); val orange = Color(0xFFD9822B)
    Column(Modifier.fillMaxWidth().background(cs.background, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Connexion", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
        val serveur = if (s == Source.ELEMENT) "Relais Element (MacBook Pro)" else if (s == Source.TEAMS) "Service Teams (MacBook Pro)" else "Serveur Matrix (MacBook Pro)"
        if (st is EtatSource.Erreur) LigneConnexion(cs.error, "$serveur injoignable", st.message)
        else LigneConnexion(vert, "$serveur joint", e.derniereActualisation?.let { "Dernière synchro " + dateCourte(it) + " à " + heure(it) })
        val surServeur = fr.messagerie.unifiee.Surveillance.serveur.collectAsState().value?.get(s)
        val sauvegarde = fr.messagerie.unifiee.Surveillance.derniereSauvegarde.collectAsState().value
        LaunchedEffect(s) { fr.messagerie.unifiee.Surveillance.verifier(force = true) }
        if (surServeur != null) {
            // État surveillé par le serveur chaque minute
            val depuis = surServeur.depuis?.let { " depuis " + dateCourte(it) + " à " + heure(it) } ?: ""
            val verifie = surServeur.verifie?.let { "Vérifié à " + heure(it) } ?: ""
            when (surServeur.ok) {
                true -> LigneConnexion(vert, "Passerelle ${s.libelle} connectée$depuis", listOf(surServeur.detail, verifie).filter { it.isNotBlank() }.joinToString(" · "))
                false -> LigneConnexion(cs.error, "Passerelle ${s.libelle} déconnectée$depuis",
                    surServeur.detail.ifBlank { "Reconnectez ${s.libelle} sur le serveur." })
                null -> LigneConnexion(orange, "Passerelle ${s.libelle} : état inconnu", listOf(surServeur.detail, verifie).filter { it.isNotBlank() }.joinToString(" · "))
            }
        } else if (s != Source.ELEMENT && s != Source.TEAMS) {
            val p = passerelle
            when {
                p == null -> LigneConnexion(cs.outline, "Passerelle ${s.libelle} : vérification…", null)
                p.message == null -> LigneConnexion(cs.outline, "Passerelle ${s.libelle} : état inconnu", "Le robot de la passerelle n'a pas encore écrit.")
                p.ok == false -> LigneConnexion(cs.error, "Passerelle ${s.libelle} déconnectée",
                    "« ${p.message} » (${p.date?.let { dateCourte(it) + " " + heure(it) } ?: ""}). Reconnectez ${s.libelle} sur le serveur.")
                p.ok == true -> LigneConnexion(vert, "Passerelle ${s.libelle} connectée", "« ${p.message} » · ${p.date?.let { dateCourte(it) + " " + heure(it) } ?: ""}")
                else -> LigneConnexion(orange, "Passerelle ${s.libelle} : à surveiller", "Dernier message du robot : « ${p.message} »")
            }
        }
        val heures = recu?.let { (System.currentTimeMillis() - it) / 3_600_000 }
        LigneConnexion(if (heures == null || heures >= 24) orange else vert,
            "Dernier message reçu : " + (recu?.let { dateCourte(it) + " à " + heure(it) } ?: "aucun"),
            if (heures != null && heures >= 24) "Rien reçu depuis plus d'un jour : vérifiez la connexion de ${s.libelle}." else null)
        if (sauvegarde != null) LigneConnexion(if (System.currentTimeMillis() - sauvegarde < 2 * 86_400_000L) vert else orange,
            "Configuration du serveur sauvegardée le " + dateCourte(sauvegarde) + " à " + heure(sauvegarde), "Sauvegarde automatique chaque nuit, 14 jours gardés.")
    }
}

@Composable
private fun PageConfidentialite(version: Int) {
    val cfg = Moteur.cfg
    var reactions by remember(version) { mutableStateOf(cfg.envoyerReactions) }
    Interrupteur("Transmettre mes réactions aux contacts", reactions,
        "Désactivé, vos émoticônes restent sur ce téléphone. Rien ne part sans votre appui.") { reactions = it; cfg.envoyerReactions = it }
    HorizontalDivider()
    // Statut de présence choisi par vous
    val portee = rememberCoroutineScope()
    var presence by remember(version) { mutableStateOf(cfg.presence) }
    var retour by remember { mutableStateOf<String?>(null) }
    Text("Mon statut de présence", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val choix = listOf("offline" to "Hors ligne", "unavailable" to "Absent", "online" to "En ligne")
        choix.forEachIndexed { i, (v, t) ->
            SegmentedButton(selected = presence == v, onClick = {
                presence = v; retour = "Application…"
                portee.launch {
                    val (ok, ko) = Moteur.definirPresence(v)
                    retour = (if (ok.isNotEmpty()) "Appliqué : " + ok.joinToString(", ") + "." else "") +
                        (if (ko.isNotEmpty()) " Non appliqué : " + ko.joinToString(", ") + "." else "")
                }
            }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, choix.size), icon = {}) { Text(t) }
        }
    }
    Explication("Ce que vos contacts voient de vous (« en ligne », « absent »), là où la messagerie le transmet. " +
        "Hors ligne par défaut. WhatsApp ne reprend pas toujours ce statut.")
    retour?.let { Text(it.trim(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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

@Composable internal fun Interrupteur(libelle: String, valeur: Boolean, aide: String? = null, maj: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { maj(!valeur) }.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(libelle)
            if (aide != null) Text(aide, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = valeur, onCheckedChange = maj)
    }
}

/** Rapport des erreurs et arrêts de l'appli : à voir, copier (pour le transmettre) ou effacer. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RapportPlantage() {
    val ctx = LocalContext.current
    var rapport by remember { mutableStateOf<String?>(null) }
    var arret by remember { mutableStateOf(false) }
    var ouvert by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { rapport = Plantages.rapport(); arret = Plantages.aUnArret() }
    }
    val r = rapport ?: return
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = if (arret) cs.errorContainer else cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (arret) "L'appli s'est arrêtée" else "Des erreurs ont été rattrapées", fontWeight = FontWeight.SemiBold,
                color = if (arret) cs.onErrorContainer else cs.onSurface)
            Text("Le rapport reste sur ce téléphone. Copiez-le pour le transmettre (aucun message n'y figure).", fontSize = 13.sp,
                color = if (arret) cs.onErrorContainer else cs.onSurfaceVariant)
            if (ouvert) Text(r.take(4000), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = if (arret) cs.onErrorContainer else cs.onSurface)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { ouvert = !ouvert }) { Text(if (ouvert) "Masquer" else "Voir") }
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Rapport de plantage", r))
                    android.widget.Toast.makeText(ctx, "Rapport copié", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Copier") }
                TextButton(onClick = { Plantages.effacer(); rapport = null }) { Text("Effacer") }
            }
        }
    }
}

/** Vérification : autorisations Android, canaux, batterie, et notification d'essai locale. */
@Composable
private fun EtatNotifications() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var verif by remember { mutableIntStateOf(0) }
    // Revérifie au retour des réglages Android
    val cycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(cycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) verif++ }
        cycle.lifecycle.addObserver(obs); onDispose { cycle.lifecycle.removeObserver(obs) }
    }
    val nm = androidx.core.app.NotificationManagerCompat.from(ctx)
    val autorisees = remember(verif) { nm.areNotificationsEnabled() }
    val coupes = remember(verif) {
        Source.entries.filter { s -> nm.getNotificationChannel("${Moteur.CANAL_MESSAGES}.${s.id}")?.importance == android.app.NotificationManager.IMPORTANCE_NONE }
    }
    val batterieLibre = remember(verif) { ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    @Composable fun Ligne(ok: Boolean, texte: String, action: String?, faire: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (ok) Color(0xFF2F9E44) else cs.error, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(texte, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = faire) { Text(action) }
        }
    }
    fun ouvrirAppli() = runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Text("Vérification", fontWeight = FontWeight.SemiBold)
    Ligne(autorisees, if (autorisees) "Notifications autorisées par Android" else "Notifications bloquées par Android", if (autorisees) null else "Autoriser") { ouvrirAppli() }
    Ligne(coupes.isEmpty(), if (coupes.isEmpty()) "Aucune messagerie coupée dans Android" else "Coupées dans Android : " + coupes.joinToString { it.libelle },
        if (coupes.isEmpty()) null else "Ouvrir") { ouvrirAppli() }
    Ligne(batterieLibre, if (batterieLibre) "Batterie sans restriction" else "Économie de batterie active : réception retardée", if (batterieLibre) null else "Changer") {
        runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Ligne(Moteur.cfg.veilleArrierePlan, if (Moteur.cfg.veilleArrierePlan) "À l'écoute en arrière-plan" else "Appli fermée : vérification toutes les 15 min environ", null) {}
    Explication("Appli ouverte, aucune notification n'est affichée : les messages apparaissent directement dans la liste.")
    OutlinedButton(onClick = {
        Moteur.notificationEssai(Source.WHATSAPP)
        android.widget.Toast.makeText(ctx, if (autorisees) "Notification d'essai affichée" else "Bloquée : autorisez d'abord les notifications", android.widget.Toast.LENGTH_SHORT).show()
    }) { Text("Tester une notification") }
}
