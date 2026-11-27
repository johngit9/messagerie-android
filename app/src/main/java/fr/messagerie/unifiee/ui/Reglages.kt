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
import androidx.compose.material.icons.outlined.QrCode2
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
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

private enum class Page(val titre: String, val icone: ImageVector, val couleur: Long, val motsCles: String) {
    Connexions("Serveur et relais", Icons.Outlined.Dns, 0xFF0C8599, "matrix synapse passerelle whatsapp sms telegram signal linkedin element relais teams adresse jeton connexion"),
    Profil("Mon profil", Icons.Outlined.Person, 0xFF0DBD8C, "nom profil pseudo affiché element whatsapp telegram signal linkedin"),
    IA("Intelligence artificielle", Icons.Outlined.AutoAwesome, 0xFFE67700, "ollama ia résumé suggestion modèle"),
    Messageries("Messageries, Pro et Perso", Icons.Outlined.Forum, 0xFF3B5BDB, "pro perso espace source whatsapp sms telegram signal linkedin element teams notification couleur silence"),
    Affichage("Apparence et couleurs", Icons.Outlined.Palette, 0xFF9C36B5, "thème sombre clair couleur taille texte zoom bandeau material you design police typo caractères icône fond"),
    Notifications("Notifications et réception", Icons.Outlined.Notifications, 0xFFE8590C, "bulle son vibration batterie temps réel arrière-plan économie silence verrouillage vacances"),
    Confidentialite("Confidentialité", Icons.Outlined.Shield, 0xFF2F9E44, "lu accusé lecture présence réaction google messages vie privée"),
    Statistiques("Réception et statistiques", Icons.Outlined.Insights, 0xFF1971C2, "latence délai archive messages"),
    Appairage("Connecter un appareil", Icons.Outlined.QrCode2, 0xFF2B8A3E, "qr code nouveau téléphone tablette appareil scanner connexion transférer"),
    Fichier("Fichier de configuration", Icons.Outlined.Description, 0xFF5C677D, "importer exporter json sauvegarde"),
    MiseAJour("Mise à jour de l'appli", Icons.Outlined.SystemUpdate, 0xFF6741D9, "version apk installer"),
}

/** État de la vérification des mises à jour, gardé tant que l'appli tourne. */
private object EtatMaj {
    var derniere by mutableStateOf<MiseAJour.Version?>(null)
    var erreur by mutableStateOf<String?>(null)
    var verifie by mutableStateOf(false)
    var progression by mutableStateOf<Float?>(null)
    var nouveautes by mutableStateOf<List<Pair<String, String>>>(emptyList())
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
    var sous by rememberSaveable { mutableStateOf<String?>(null) }     // sous-menu ouvert dans la page
    fun aller(p: Page?) { page = p; sous = null }
    BackHandler(enabled = page != null || Moteur.cfg.configure) {
        when { sous != null -> sous = null; page != null -> aller(null); else -> quitter() }
    }

    LaunchedEffect(Unit) { if (cfg.verifierMaj && !EtatMaj.verifie) verifierMaj(ctx) }

    fun profondeur(x: Pair<Page?, String?>) = (if (x.first != null) 1 else 0) + (if (x.second != null) 1 else 0)
    AnimatedContent(
        targetState = page to sous,
        transitionSpec = {
            if (profondeur(targetState) > profondeur(initialState)) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            else (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        label = "reglages",
    ) { (p, sm) ->
        if (p == null) Menu(e, accueil, quitter = { quitter() }, ouvrir = { aller(it) })
        else androidx.compose.runtime.CompositionLocalProvider(LocalSousMenu provides NavSous(sm) { sous = it }) {
            PageReglage(sm ?: p.titre, retour = { if (sm != null) sous = null else aller(null) }) {
                when (p) {
                    Page.Connexions -> PageConnexions(e, version) { connexionsModifiees = true }
                    Page.IA -> PageIA(version)
                    Page.Profil -> PageProfil()
                    Page.Messageries -> PageMessageries(e) { aller(it) }
                    Page.Affichage -> PageAffichage()
                    Page.Notifications -> PageNotifications(version)
                    Page.Confidentialite -> PageConfidentialite(version)
                    Page.Fichier -> PageFichier { version++; connexionsModifiees = true }
                    Page.Appairage -> PageAppairage { version++; connexionsModifiees = true }
                    Page.MiseAJour -> PageMiseAJour()
                    Page.Statistiques -> PageStatistiques(e) { aller(it) }
                }
            }
        }
    }
}

private suspend fun verifierMaj(ctx: android.content.Context) {
    EtatMaj.erreur = null
    try { EtatMaj.derniere = MiseAJour.derniere() } catch (ex: Exception) { EtatMaj.erreur = ex.message ?: ex.javaClass.simpleName }
    // Nouveautés depuis la version installée ; à jour : celles de la version installée
    runCatching {
        val installee = MiseAJour.versionInstallee(ctx)
        val dispo = (EtatMaj.derniere?.numero ?: 0) > installee
        EtatMaj.nouveautes = MiseAJour.nouveautes(if (dispo) installee else installee - 1)
    }
    EtatMaj.verifie = true
}

private fun majDisponible(ctx: android.content.Context) =
    EtatMaj.derniere?.let { it.numero > MiseAJour.versionInstallee(ctx) } == true

// MARK: Menu principal

/** Une rubrique trouvée par la recherche ou affichée dans un groupe. */
private class Entree(val page: Page, val resume: String, val pastille: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Menu(e: Etat, accueil: Boolean, quitter: () -> Unit, ouvrir: (Page) -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    val defilement = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var recherche by rememberSaveable { mutableStateOf("") }

    fun resume(s: Source) = when (val st = e.sources[s]) {
        is EtatSource.Ok -> "Connecté"
        is EtatSource.Erreur -> "Erreur : ${st.message}"
        else -> null
    }
    val sourcesMatrix = listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM, Source.LINKEDIN, Source.SIGNAL)
    val okMatrix = sourcesMatrix.filter { e.sources[it] is EtatSource.Ok }
    val actives = Source.entries.filter { e.statistiques[it] != null }
    val n = cfg.imports.size
    var recu by remember { mutableIntStateOf(0) }      // +1 après un QR code : « Commencer » relit la configuration

    val entrees = mapOf(
        Page.Messageries to Entree(Page.Messageries, if (actives.isEmpty()) "Pro / Perso, couleurs, délai de réception"
            else actives.joinToString(" · ") { it.id + if (e.pro.contains(it)) " (pro)" else "" }),
        Page.Notifications to Entree(Page.Notifications, (if (cfg.vacances) "Mode vacances activé · " else "") + when {
            !cfg.veilleArrierePlan -> "Économie max · vérification toutes les 15 min"
            cfg.economie -> "Temps réel économe · bulles, boutons, sons"
            else -> "Temps réel · bulles, boutons, sons"
        }, pastille = cfg.vacances),
        Page.Confidentialite to Entree(Page.Confidentialite, if (cfg.envoyerReactions) "Aucun accusé de lecture · réactions transmises" else "Aucun accusé de lecture · réactions gardées ici"),
        Page.Affichage to Entree(Page.Affichage, listOf("Thème clair", "Thème sombre").getOrNull(Affichage.theme - 1).let { it ?: "Thème du téléphone" } +
            " · texte ${Affichage.texte} %" + (if (Affichage.dynamique) " · Material You" else if (Affichage.accent != 0L) " · couleur perso" else "")),
        Page.Connexions to Entree(Page.Connexions, listOf(
            "Matrix " + when {
                !cfg.matrixConfigure -> "non configuré"
                okMatrix.isNotEmpty() -> "connecté"
                else -> sourcesMatrix.firstNotNullOfOrNull { resume(it) }?.lowercase() ?: "configuré"
            },
            "Element " + if (!cfg.relaisConfigure) "non configuré" else (resume(Source.ELEMENT)?.lowercase() ?: "configuré"),
        ).joinToString(" · ")),
        Page.Profil to Entree(Page.Profil, "Votre nom affiché sur chaque messagerie"),
        Page.IA to Entree(Page.IA, if (cfg.ollamaConfigure) "Résumés et suggestions · ${cfg.ollamaModele}" else "Désactivée"),
        Page.Statistiques to Entree(Page.Statistiques, "${e.statistiques.values.sumOf { it.second }} messages archivés · délai de réception"),
        Page.Appairage to Entree(Page.Appairage, "Nouveau téléphone ou tablette par QR code"),
        Page.Fichier to Entree(Page.Fichier, "Importer, exporter" + if (n > 0) " · $n import(s) mémorisé(s)" else ""),
        Page.MiseAJour to Entree(Page.MiseAJour, when {
            majDisponible(ctx) -> "Version ${EtatMaj.derniere!!.nom} disponible"
            EtatMaj.derniere != null -> "Version ${MiseAJour.nomInstalle(ctx)} · à jour"
            else -> "Version ${MiseAJour.nomInstalle(ctx)}"
        }, pastille = majDisponible(ctx)),
    )

    Scaffold(
        modifier = Modifier.nestedScroll(defilement.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("Réglages", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { if (!accueil) IconButton(onClick = quitter) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
                scrollBehavior = defilement,
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChampRecherche(recherche) { recherche = it }

            val q = sansAccents(recherche.trim())
            if (q.isNotEmpty()) {
                val trouves = Page.entries.filter { p ->
                    val ent = entrees[p] ?: return@filter false
                    sansAccents(p.titre + " " + ent.resume + " " + p.motsCles).contains(q)
                }
                if (trouves.isEmpty()) Text("Aucun réglage ne correspond à « ${recherche.trim()} ».",
                    modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Groupe("Résultats") { trouves.forEachIndexed { i, p -> Rubrique(entrees.getValue(p), ouvrir, separateur = i > 0) } }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
                return@Column
            }

            if (accueil) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Bienvenue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text("Le plus simple : sur un appareil déjà configuré (téléphone ou app Mac), affichez le QR code de connexion " +
                            "(Réglages > Connecter un appareil), puis scannez-le ici. Sinon : « Fichier de configuration » puis « Importer… », " +
                            "ou renseignez le serveur Matrix et/ou le relais Element. Rien n'est envoyé ailleurs que vers vos serveurs.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        ScannerAppairage { recu++ }
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

            if (cfg.configure) CarteEtat(e) { ouvrir(Page.Messageries) }
            Raccourcis()

            Groupe("Messages") {
                Rubrique(entrees.getValue(Page.Messageries), ouvrir)
                Rubrique(entrees.getValue(Page.Notifications), ouvrir, separateur = true)
                Rubrique(entrees.getValue(Page.Confidentialite), ouvrir, separateur = true)
                Rubrique(entrees.getValue(Page.Profil), ouvrir, separateur = true)
            }
            Groupe("Apparence") {
                Rubrique(entrees.getValue(Page.Affichage), ouvrir)
            }
            Groupe("Connexions") {
                Rubrique(entrees.getValue(Page.Connexions), ouvrir)
                Rubrique(entrees.getValue(Page.IA), ouvrir, separateur = true)
            }
            Groupe("Appli") {
                Rubrique(entrees.getValue(Page.Statistiques), ouvrir)
                Rubrique(entrees.getValue(Page.Appairage), ouvrir, separateur = true)
                Rubrique(entrees.getValue(Page.Fichier), ouvrir, separateur = true)
                Rubrique(entrees.getValue(Page.MiseAJour), ouvrir, separateur = true)
            }

            val pret = recu >= 0 && cfg.configure
            if (accueil) Button(
                onClick = { Moteur.redemarrer(); quitter() },
                enabled = pret,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp), shape = RoundedCornerShape(14.dp),
            ) { Text(if (pret) "Commencer" else "Renseignez d'abord une connexion") }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

private fun sansAccents(t: String) =
    java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Mode vacances, tout en haut des Réglages : coupe les notifications de tout ce qui est rangé en Pro. */
@Composable
private fun CarteVacances(e: Etat) {
    val actif by Moteur.vacances.collectAsState()
    val cs = MaterialTheme.colorScheme
    val fond = if (actif) Color(0xFFFDEEDC) else cs.surfaceContainerLow
    val texte = if (actif) Color(0xFF7A3A08) else cs.onSurface
    val pro = Source.entries.filter { it in e.pro && (e.statistiques[it] != null || e.sources[it] != null) }
    fun basculer(v: Boolean) { Moteur.definirVacances(v) }
    Surface(shape = RoundedCornerShape(20.dp), color = fond,
        border = if (actif) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE8590C)) else null,
        modifier = Modifier.fillMaxWidth().clickable { basculer(!actif) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8590C)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BeachAccess, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Mode vacances", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = texte)
                Text(if (actif) "Activé : aucune notification Pro" + (if (pro.isNotEmpty()) " (" + pro.joinToString(", ") { it.id } + ")" else "")
                    else "Coupe les notifications des messageries et conversations rangées en Pro",
                    fontSize = 13.sp, color = if (actif) texte else cs.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = actif, onCheckedChange = { basculer(it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Color(0xFFE8590C)))
        }
    }
}

@Composable
private fun ChampRecherche(valeur: String, maj: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (valeur.isEmpty()) Text("Rechercher un réglage", color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.text.BasicTextField(
                    value = valeur, onValueChange = maj, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (valeur.isNotEmpty()) IconButton(onClick = { maj("") }) { Icon(Icons.Outlined.Close, "Effacer") }
        }
    }
}

/** En-tête : chaque messagerie avec un point vert (connectée), rouge (erreur) ou gris. */
@Composable
private fun CarteEtat(e: Etat, ouvrir: () -> Unit) {
    val suivies = Source.entries.filter { e.sources[it] != null }
    if (suivies.isEmpty()) return
    val ok = suivies.count { e.sources[it] is EtatSource.Ok }
    val erreurs = suivies.filter { e.sources[it] is EtatSource.Erreur }
    val vert = Color(0xFF2F9E44); val rouge = MaterialTheme.colorScheme.error
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = ouvrir)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (erreurs.isEmpty()) vert else rouge))
                Spacer(Modifier.width(10.dp))
                Text(if (erreurs.isEmpty()) "Tout fonctionne" else "${erreurs.size} messagerie(s) en erreur",
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("$ok / ${suivies.size} connectées", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                suivies.forEach { s ->
                    Box {
                        LogoSource(s, 30.dp)
                        val c = when (e.sources[s]) { is EtatSource.Ok -> vert; is EtatSource.Erreur -> rouge; else -> Color.Gray }
                        Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(2.dp).clip(CircleShape).background(c))
                    }
                }
            }
            erreurs.firstOrNull()?.let { s ->
                Text("${s.libelle} : ${(e.sources[s] as EtatSource.Erreur).message}", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

/** Raccourcis : thème et mode de réception, changés d'un appui. */
@Composable
private fun Raccourcis() {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    var mode by remember { mutableIntStateOf(if (!cfg.veilleArrierePlan) 2 else if (cfg.economie) 1 else 0) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val themes = listOf("Auto", "Clair", "Sombre")
        val iconesTheme = listOf(Icons.Outlined.BrightnessAuto, Icons.Outlined.LightMode, Icons.Outlined.DarkMode)
        TuileRapide(iconesTheme[Affichage.theme.coerceIn(0, 2)], "Thème", themes[Affichage.theme.coerceIn(0, 2)],
            Affichage.theme != 0, Modifier.weight(1f)) {
            val v = (Affichage.theme + 1) % 3; Affichage.theme = v; cfg.theme = v
        }
        val libelles = listOf("Temps réel", "Économe", "Économie max")
        TuileRapide(if (mode == 2) Icons.Outlined.BatterySaver else Icons.Outlined.Bolt, "Réception", libelles[mode],
            mode != 2, Modifier.weight(1f)) {
            mode = when (mode) { 1 -> 0; 0 -> 2; else -> 1 }
            cfg.veilleArrierePlan = mode != 2
            if (mode != 2) cfg.economie = mode == 1
            ServiceVeille.mettreAJour(ctx)
        }
    }
}

@Composable
private fun TuileRapide(icone: ImageVector, titre: String, valeur: String, actif: Boolean, modifier: Modifier, clic: () -> Unit) {
    val fond = if (actif) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val texte = if (actif) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(shape = RoundedCornerShape(20.dp), color = fond, modifier = modifier.clickable(onClick = clic)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, null, tint = texte, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(titre, fontSize = 12.sp, color = texte.copy(alpha = 0.75f))
                Text(valeur, fontWeight = FontWeight.SemiBold, color = texte, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Groupe(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Text(titre, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(content = contenu)
    }
}

@Composable
private fun Rubrique(ent: Entree, ouvrir: (Page) -> Unit, separateur: Boolean = false) {
    val p = ent.page
    if (separateur) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        Modifier.fillMaxWidth().clickable { ouvrir(p) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(p.couleur)), contentAlignment = Alignment.Center) {
            Icon(p.icone, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.titre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(ent.resume, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (ent.pastille) Box(Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Réception et statistiques : chiffres clés, délai par messagerie, volume archivé en barres. */
@Composable
private fun PageStatistiques(e: Etat, ouvrir: (Page) -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Latence de réception : heure du message sur le serveur → arrivée sur ce téléphone
    val latences by androidx.compose.runtime.produceState(emptyList<Pair<Source, List<Long>>>(), e.derniereActualisation) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Source.entries.mapNotNull { src -> Moteur.delais(src).takeIf { it.isNotEmpty() }?.let { src to it } }
        }
    }
    val toutes = latences.flatMap { it.second }
    val totalConv = e.statistiques.values.sumOf { it.first }
    val totalMsg = e.statistiques.values.sumOf { it.second }
    // Chiffres clés
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TuileChiffre("Messages", compteLong(totalMsg), Icons.Outlined.Forum, 0xFF3B5BDB, Modifier.weight(1f))
        TuileChiffre("Conversations", compteLong(totalConv), Icons.Outlined.Insights, 0xFF9C36B5, Modifier.weight(1f))
        TuileChiffre("Délai médian", if (toutes.isEmpty()) "—" else duree(centile(toutes, 0.5)), Icons.Outlined.Bolt,
            when { toutes.isEmpty() -> 0xFF5C677D; centile(toutes, 0.5) < 10_000 -> 0xFF2F9E44; centile(toutes, 0.5) < 120_000 -> 0xFFE8590C; else -> 0xFFC92A2A },
            Modifier.weight(1f))
    }
    Section("Délai de réception", aide = "Du serveur jusqu'à ce téléphone. Médiane des 50 derniers messages reçus en direct ; " +
        "« 9 sur 10 » : 90 % arrivent plus vite.") {
        if (latences.isEmpty()) Explication("Pas encore mesuré : il s'affichera après les prochains messages reçus.")
        latences.forEachIndexed { i, (src, _) ->
            if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            BlocLatence(src, e.derniereActualisation)
        }
    }
    if (latences.any { it.second.average() > 60_000 } && !cfg.veilleArrierePlan) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFDEEDC), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Délai élevé", fontWeight = FontWeight.SemiBold, color = Color(0xFF7A3A08))
                Text("Appli fermée, Android ne vérifie que toutes les 15 min environ. Pour recevoir en quelques secondes, " +
                    "choisissez « Temps réel » et retirez l'appli des économies de batterie.", fontSize = 13.sp, color = Color(0xFF7A3A08))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { ouvrir(Page.Notifications) }) { Text("Réception") }
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Économies de batterie") }
                }
            }
        }
    }
    if (e.statistiques.isNotEmpty()) Section("Messages archivés") {
        val max = e.statistiques.values.maxOf { it.second }.coerceAtLeast(1)
        Source.entries.filter { e.statistiques[it] != null }.sortedByDescending { e.statistiques[it]!!.second }.forEach { s ->
            val st = e.statistiques.getValue(s)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoSource(s, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(s.libelle, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(compteLong(st.second), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("  · ${st.first} conv.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(cs.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth((st.second.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp)).background(s.teinte()))
                }
            }
        }
    }
}

private fun compteLong(n: Int) = when {
    n >= 1_000_000 -> "%.1f M".format(n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000} k"
    else -> "%,d".format(n).replace(',', ' ')
}

@Composable
private fun TuileChiffre(titre: String, valeur: String, icone: ImageVector, couleur: Long, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(couleur).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icone, null, tint = Color(couleur), modifier = Modifier.size(18.dp))
            }
            Text(valeur, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
            Text(titre, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
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

/** Serveur Matrix et relais Element sur une même page : une carte chacun, le détail en sous-menu. */
@Composable
private fun PageConnexions(e: Etat, version: Int, modifie: () -> Unit) {
    val cfg = Moteur.cfg
    val nav = LocalSousMenu.current
    when (nav.actuel) {
        "Serveur Matrix" -> PageMatrix(version, modifie)
        "Relais Element" -> PageElement(version, modifie)
        else -> {
            Explication("Vos deux points d'accès sur le serveur. Touchez une carte pour ses adresses et jetons ; tout est enregistré aussitôt.")
            CarteConnexion("Serveur Matrix", "Passerelles de votre serveur Synapse", Icons.Outlined.Dns, 0xFF0C8599,
                cfg.matrixConfigure, listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM, Source.SIGNAL, Source.LINKEDIN), e)
            CarteConnexion("Relais Element", "Element et Teams (lecture seule)", Icons.Outlined.Hub, 0xFF0DBD8C,
                cfg.relaisConfigure, listOfNotNull(Source.ELEMENT, Source.TEAMS.takeIf { cfg.afficherTeams }), e)
        }
    }
}

@Composable
private fun CarteConnexion(titre: String, sousTitre: String, icone: ImageVector, couleur: Long, configure: Boolean, sources: List<Source>, e: Etat) {
    val nav = LocalSousMenu.current
    val cs = MaterialTheme.colorScheme
    val vert = Color(0xFF2F9E44)
    val suivies = sources.filter { e.sources[it] != null }
    val erreurs = suivies.filter { e.sources[it] is EtatSource.Erreur }
    val (pointCouleur, etat) = when {
        !configure -> cs.outline to "Non configuré"
        suivies.isEmpty() -> cs.outline to "Configuré, en attente de synchro"
        erreurs.isEmpty() -> vert to "Connecté"
        erreurs.size == suivies.size -> cs.error to "Injoignable"
        else -> Color(0xFFE8590C) to "${erreurs.size} en erreur"
    }
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { nav.ouvrir(titre) }) {
        Column {
            // Bandeau coloré
            Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(Color(couleur), Color(couleur).copy(alpha = 0.75f)))).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(icone, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(titre, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color.White)
                    Text(sousTitre, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(pointCouleur, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(etat, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    e.derniereActualisation?.takeIf { configure }?.let { Text("Synchro " + heure(it), fontSize = 12.sp, color = cs.onSurfaceVariant) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    sources.forEach { s ->
                        val c = when (e.sources[s]) { is EtatSource.Ok -> vert; is EtatSource.Erreur -> cs.error; else -> cs.outline }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                LogoSource(s, 34.dp)
                                Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(cs.surfaceContainerLow).padding(2.dp).clip(CircleShape).background(c))
                            }
                            Text(s.id, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                erreurs.firstOrNull()?.let { s -> Text("${s.libelle} : ${(e.sources[s] as EtatSource.Erreur).message}", fontSize = 12.sp, color = cs.error, maxLines = 2) }
            }
        }
    }
}

@Composable
private fun PageMatrix(version: Int, modifie: () -> Unit) {
    val cfg = Moteur.cfg
    var urls by remember(version) { mutableStateOf(cfg.matrixUrls) }
    var user by remember(version) { mutableStateOf(cfg.matrixUtilisateur) }
    var jeton by remember(version) { mutableStateOf(cfg.matrixJeton) }
    var moi by remember(version) { mutableStateOf(cfg.moiFantome) }
    Explication("Passerelles SMS, WhatsApp, Telegram et LinkedIn de votre serveur. Enregistré automatiquement ; la connexion est relancée en quittant les Réglages.")
    Section {
    Champ("Adresse(s) du serveur", urls, "http://serveur:8008  (plusieurs : séparées par des virgules)") { urls = it; cfg.matrixUrls = it; modifie() }
    Champ("Identifiant Matrix", user, "@utilisateur:domaine") { user = it; cfg.matrixUtilisateur = it; modifie() }
    Champ("Jeton d'accès", jeton, "", secret = true) { jeton = it; cfg.matrixJeton = it; modifie() }
    Champ("Fantôme « moi » (optionnel)", moi, "@gmessages_…:domaine — détecté automatiquement sinon") { moi = it; cfg.moiFantome = it; modifie() }
    }
}

@Composable
private fun PageElement(version: Int, modifie: () -> Unit) {
    val cfg = Moteur.cfg
    var urls by remember(version) { mutableStateOf(cfg.relaisUrls) }
    var jeton by remember(version) { mutableStateOf(cfg.relaisJeton) }
    Explication("Relais de votre serveur pour Element (et la correspondance des salons WhatsApp).")
    Section {
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
    Section {
    Champ("Adresse(s) d'Ollama", urls, "http://serveur:11434") { urls = it; cfg.ollamaUrls = it }
    Champ("Modèle", modele, "qwen3:8b") { modele = it; cfg.ollamaModele = it }
    OutlinedButton(onClick = {
        test = "Test…"
        portee.launch { test = withContext(Dispatchers.IO) { Ollama(cfg).tester() } }
    }) { Text("Tester la connexion") }
    test?.let { Text(it, fontSize = 13.sp) }
    }
    Section("Résumer les conversations des") {
    Choix(listOf(1 to "24 h", 3 to "3 jours", 7 to "7 jours", 14 to "14 jours"), jours) { jours = it; cfg.jours = it; Moteur.redemarrer() }
    }
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

    var bulles by remember(version) { mutableStateOf(cfg.notifBulles) }
    var boutons by remember(version) { mutableStateOf(cfg.notifBoutons) }
    var photo by remember(version) { mutableStateOf(cfg.notifPhoto) }
    var apercu by remember(version) { mutableIntStateOf(cfg.notifApercu) }
    var verrou by remember(version) { mutableIntStateOf(cfg.notifVerrouille) }
    val nav = LocalSousMenu.current
    when (nav.actuel) {
        null -> {
            CarteVacances(e)
            EtatNotifications()
            ApercuNotification(photo, boutons, bulles, apercu)
            val nSourdine = e.conversations.count { e.etat(it).sourdine }
            // Toutes les messageries dans une seule carte : un menu déroulant chacune, et son bouton son / vibration
            Text("Par messagerie", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column {
                    key(maj) {
                        Source.entries.filter { e.statistiques[it] != null }.forEachIndexed { i, s ->
                            LigneDeroulante({ LogoSource(s, 30.dp) }, s.libelle, cfg.notif(s),
                                listOf("tous" to "Chaque message", "attendue" to "Réponse attendue", "aucun" to "Aucune"), i > 0,
                                choisir = { cfg.definirNotif(s, it); maj++ },
                                extra = if (android.os.Build.VERSION.SDK_INT >= 26) ({
                                    // Son, vibration, pop-up : réglages Android du canal de cette messagerie
                                    IconButton(onClick = {
                                        runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, Moteur.canalMessages(s))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }) { Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Son et vibration ${s.libelle}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }) else null)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Interrupteur("Toujours notifier une réponse attendue", notifs, "Selon le résumé de l'IA, même pour une messagerie en « Aucune ».") { notifs = it; cfg.notifications = it }
                    }
                }
            }
            Explication("« Réponse attendue » : seulement quand l'IA juge qu'on attend votre réponse. 🔊 : son, vibration et pop-up de la messagerie.")
            var appels by remember(version) { mutableStateOf(cfg.appelsOfficiels) }
            Section {
                Interrupteur("Appels : ouvrir l'appli officielle", appels,
                    "Un appel audio ou vidéo reçu sur WhatsApp, Signal, Telegram… ouvre l'appli officielle pour décrocher " +
                        "(Messagerie ne gère pas les appels). Appli fermée : notification d'appel en plein écran.") { appels = it; cfg.appelsOfficiels = it }
            }
            ListeSousMenus("Plus de réglages", listOfNotNull(
                ElementSous("Style des notifications", listOfNotNull(if (photo) "Photo" else "Initiale", if (boutons) "boutons" else null,
                    if (apercu == 0) "message affiché" else "contenu masqué").joinToString(" · "), Icons.Outlined.Style, 0xFF3B5BDB),
                if (android.os.Build.VERSION.SDK_INT >= 30) ElementSous("Bulles", if (bulles) "Conversations en bulle par-dessus les applis" else "Désactivées",
                    Icons.Outlined.ChatBubbleOutline, 0xFF0C8599) else null,
                ElementSous("Heures de silence", if (cfg.silenceDebut < 0) "Désactivées" else "De ${cfg.silenceDebut} h à ${cfg.silenceFin} h",
                    Icons.Outlined.Bedtime, 0xFF6741D9),
                ElementSous("Réception et batterie", when { !veille -> "Économie max"; cfg.economie -> "Temps réel économe"; else -> "Temps réel" },
                    Icons.Outlined.BatteryChargingFull, 0xFFE8590C),
                ElementSous("Icône et sourdine", "Chiffre sur l'icône" + if (nSourdine > 0) " · $nSourdine en sourdine" else "",
                    Icons.Outlined.NotificationsPaused, 0xFF5C677D),
            ))
        }
        "Style des notifications" -> {
            ApercuNotification(photo, boutons, bulles, apercu)
            Section(aide = "Comme WhatsApp ou Messages. S'applique aux prochaines notifications.") {
            Interrupteur("Photo du contact", photo, "Sinon, son initiale.") { photo = it; cfg.notifPhoto = it }
            Interrupteur("Boutons Répondre, Marquer comme lu, Silence", boutons) { boutons = it; cfg.notifBoutons = it }
            }
            Section {
            Text("Contenu affiché", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choix(listOf(0 to "Le message", 1 to "« Nouveau message »"), apercu) { apercu = it; cfg.notifApercu = it }
            Text("Sur l'écran verrouillé", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choix(listOf(0 to "Tout afficher", 1 to "Masquer le contenu", 2 to "Ne rien afficher"), verrou) { verrou = it; cfg.notifVerrouille = it }
            }
        }
        "Bulles" -> Section {
            Interrupteur("Bulles de conversation", bulles, "La conversation s'ouvre dans une bulle ronde par-dessus les autres applis (icône en bas à droite de la notification).") {
                bulles = it; cfg.notifBulles = it
            }
            if (bulles) TextButton(onClick = {
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Autoriser les bulles dans Android") }
            var etatBulles by remember { mutableStateOf(fr.messagerie.unifiee.Bulles.etat(ctx)) }
            var essai by remember { mutableStateOf<String?>(null) }
            // Revérifié au retour des réglages Android
            val cycleBulles = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(cycleBulles) {
                val o = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) etatBulles = fr.messagerie.unifiee.Bulles.etat(ctx) }
                cycleBulles.lifecycle.addObserver(o); onDispose { cycleBulles.lifecycle.removeObserver(o) }
            }
            Explication(etatBulles)
            var essais by remember { mutableIntStateOf(0) }
            var journal by remember { mutableStateOf("") }
            OutlinedButton(onClick = {
                essai = fr.messagerie.unifiee.Bulles.essayer(ctx)
                    ?: "Bulle d'essai affichée (rien n'est envoyé). Si elle n'apparaît pas, autorisez les bulles pour « Toutes les conversations »."
                essais++
            }) { Text("Essayer une bulle") }
            essai?.let { Explication(it) }
            // Diagnostic : ce que devient la bulle pendant 8 s (à copier pour le transmettre)
            LaunchedEffect(essais) {
                if (essais == 0) return@LaunchedEffect
                for (t in listOf(500L, 1500L, 3000L, 3000L)) { kotlinx.coroutines.delay(t); journal = fr.messagerie.unifiee.Bulles.diagnostic(ctx) }
            }
            if (journal.isNotEmpty()) {
                Text(journal, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)).padding(10.dp))
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostic bulle", journal))
                }) { Text("Copier le diagnostic") }
            }
        }
        "Heures de silence" -> Section(aide = "Les notifications arrivent sans son ni vibration pendant ces heures.") {
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
        }
        "Réception et batterie" -> Section {
        var economie by remember(version) { mutableStateOf(cfg.economie) }
        val mode = if (!veille) 2 else if (economie) 1 else 0
        val modes = listOf(1 to "Temps réel économe", 0 to "Temps réel", 2 to "Économie max")
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (v, l) ->
                SegmentedButton(selected = mode == v, onClick = {
                    veille = v != 2; cfg.veilleArrierePlan = veille
                    if (v != 2) { economie = v == 1; cfg.economie = economie }
                    ServiceVeille.mettreAJour(ctx)
                }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, modes.size), icon = {},
                    label = { Text(l, fontSize = 12.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center) })
            }
        }
        Explication(when (mode) {
            1 -> "Recommandé. Messages reçus tout de suite, appli fermée comprise. En arrière-plan, l'appli se réveille moins souvent et ignore les accusés de lecture. Notification permanente discrète."
            0 -> "Messages reçus tout de suite, et ✓✓ / présences mis à jour en continu. Consomme un peu plus."
            else -> "Rien ne tourne appli fermée : messages vérifiés environ toutes les 15 min. Le moins de batterie, mais avec du retard."
        })
        Interrupteur("Vérifier régulièrement appli fermée", fond, "Environ toutes les 15 min (géré par Android), sans notification permanente.") {
            fond = it; cfg.synchroArrierePlan = it; fr.messagerie.unifiee.SynchroArrierePlan.programmer(ctx)
        }
        TextButton(onClick = {
            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Retirer l'appli des économies de batterie") }
        Text("Vérification de secours toutes les :")
        Choix(listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min", 900 to "15 min"), intervalle) { intervalle = it; cfg.intervalle = it }
        }
        "Icône et sourdine" -> {
            Section("Chiffre sur l'icône") {
            Explication("Le chiffre suit le total des messages non lus, et baisse dès que vous les lisez. Il est porté par une notification discrète " +
                "« N messages non lus » (sans son). Si l'icône montre un point, choisissez « Nombre » pour les pastilles d'icône dans les réglages Android.")
            TextButton(onClick = {
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Réglages Android des notifications (son, vibration, pastille)") }
            }
            Section("Conversations en sourdine") {
                if (e.conversations.none { e.etat(it).sourdine }) Explication("Aucune conversation en sourdine. Appui long sur une conversation pour la mettre en sourdine.")
            val sourdine = e.conversations.filter { e.etat(it).sourdine }
            if (sourdine.isNotEmpty()) {
                sourdine.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoSource(c.source, 22.dp); Spacer(Modifier.width(10.dp))
                        Text(c.nom, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Moteur.basculerSourdine(c) }) { Text("Réactiver") }
                    }
                }
            }
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

private val CHOIX_NOTIF = listOf("tous" to "Chaque message", "attendue" to "Réponse attendue", "aucun" to "Aucune")

/** Chaque messagerie et chaque espace (Pro, Perso) a sa page de réglages : notifications, silence, couleur, rangement, connexion. */
@Composable
private fun PageMessageries(e: Etat, ouvrir: (Page) -> Unit) {
    val cfg = Moteur.cfg
    var maj by remember { mutableIntStateOf(0) }
    val sources = Source.entries.filter { e.statistiques[it] != null || e.sources[it] is EtatSource.Ok || e.sources[it] is EtatSource.Erreur }
    val nav = LocalSousMenu.current
    if (sources.isEmpty()) { Explication("Aucune messagerie connectée pour l'instant."); return }
    fun libelleNotif(v: String) = CHOIX_NOTIF.firstOrNull { it.first == v }?.second ?: v
    when (nav.actuel) {
        null -> key(maj) {
            // Espaces Pro et Perso : réglages groupés
            ListeSousMenus("Espaces", listOf(false, true).map { pro ->
                val membres = sources.filter { cfg.pro(it) == pro }
                val notifs = membres.map { cfg.notif(it) }.distinct()
                ElementSous(if (pro) "Pro" else "Perso",
                    (if (membres.isEmpty()) "Aucune messagerie" else membres.joinToString(", ") { it.id }) +
                        (notifs.singleOrNull()?.let { " · " + libelleNotif(it).lowercase() } ?: "") +
                        (if (pro && cfg.vacances) " · vacances" else ""),
                    if (pro) Icons.Outlined.Work else Icons.Outlined.Person, if (pro) 0xFF1971C2 else 0xFF2F9E44,
                    pastille = if (pro && cfg.vacances) Color(0xFFE8590C) else null)
            })
            // Rangement Pro / Perso de toutes les messageries dans une seule carte
            Text("Rangement", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column {
                    sources.forEachIndexed { i, s ->
                        LigneDeroulante({ LogoSource(s, 30.dp) }, s.libelle, cfg.pro(s), listOf(false to "Perso", true to "Pro"), i > 0,
                            choisir = { cfg.definirPro(s, it); Moteur.chargerOrganisation(); maj++ })
                    }
                }
            }
            ListeSousMenus("Par messagerie", sources.map { s ->
                val st = e.sources[s]
                ElementSous(s.libelle,
                    if (st is EtatSource.Erreur) "Erreur : ${st.message}"
                    else listOf(if (cfg.pro(s)) "Pro" else "Perso", "notifications : " + libelleNotif(cfg.notif(s)).lowercase()).joinToString(" · "),
                    logo = { LogoSource(s, 36.dp) }, pastille = if (st is EtatSource.Erreur) MaterialTheme.colorScheme.error else null)
            })
            Explication("Une conversation peut aussi être classée à la main en Pro ou Perso (appui long sur la conversation).")
        }
        "Perso", "Pro" -> key(maj) { PageEspace(e, nav.actuel == "Pro", sources) { maj++ } }
        else -> sources.filter { it.libelle == nav.actuel }.forEach { s -> key(s, maj) { PageSource(e, s) { maj++ } } }
    }
}

/** Réglages communs à toutes les messageries d'un espace. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PageEspace(e: Etat, pro: Boolean, sources: List<Source>, maj: () -> Unit) {
    val cfg = Moteur.cfg
    val membres = sources.filter { cfg.pro(it) == pro }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (pro) "Espace Pro" else "Espace Perso", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (membres.isEmpty()) Explication("Aucune messagerie rangée ici. Changez le rangement sur la page précédente.")
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                membres.forEach { s ->
                    Row(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        LogoSource(s, 24.dp); Spacer(Modifier.width(6.dp))
                        Text(s.libelle, fontSize = 13.sp)
                        val n = e.nombreMessagesNonLus(s)
                        if (n > 0) Text("  $n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = s.teinte())
                    }
                }
            }
        }
    }
    if (membres.isEmpty()) return
    if (pro) CarteVacances(e)
    Section("Notifications", aide = "S'applique à toutes les messageries " + (if (pro) "Pro" else "Perso") + ". Chacune reste réglable à part.") {
        val actuel = membres.map { cfg.notif(it) }.distinct().singleOrNull()
        ChoixTexte(CHOIX_NOTIF, actuel) { v -> membres.forEach { cfg.definirNotif(it, v) }; maj() }
    }
    Section("Heures de silence") {
        val toutes = membres.all { cfg.silencePour(it) }
        Interrupteur("Concernées par les heures de silence", toutes,
            if (cfg.silenceDebut < 0) "Les heures de silence sont désactivées (Notifications > Heures de silence)."
            else "De ${cfg.silenceDebut} h à ${cfg.silenceFin} h, sans son ni vibration.") { v -> membres.forEach { cfg.definirSilencePour(it, v) }; maj() }
    }
}

/** Tous les réglages d'une messagerie sur une page. */
@Composable
private fun PageSource(e: Etat, s: Source, maj: () -> Unit) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val st = e.sources[s]
    val dernier = e.conversations.filter { it.source == s }.maxOfOrNull { it.derniere }
    val nonLus = e.nombreMessagesNonLus(s)
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoSource(s, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.libelle, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (st is EtatSource.Erreur) cs.error else Color(0xFF2F9E44), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(if (st is EtatSource.Erreur) "Erreur : ${st.message}"
                        else "Synchronisée" + (dernier?.let { " · dernier message ${dateCourte(it)}" } ?: ""),
                        fontSize = 12.sp, maxLines = 2, color = if (st is EtatSource.Erreur) cs.error else cs.onSurfaceVariant)
                }
            }
            if (nonLus > 0) Surface(shape = CircleShape, color = s.teinte()) {
                Text("$nonLus", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
    Section("Espace") {
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "Perso", true to "Pro").forEachIndexed { i, (v, t) ->
                SegmentedButton(selected = cfg.pro(s) == v, onClick = { cfg.definirPro(s, v); Moteur.chargerOrganisation(); maj() },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 2), icon = {}) { Text(t) }
            }
        }
        if (cfg.pro(s) && cfg.vacances) Explication("Mode vacances activé : aucune notification pour ${s.libelle}.")
    }
    Section("Notifications") {
        ChoixTexte(CHOIX_NOTIF, cfg.notif(s)) { cfg.definirNotif(s, it); maj() }
        Interrupteur("Concernée par les heures de silence", cfg.silencePour(s),
            if (cfg.silenceDebut < 0) "Heures de silence désactivées pour l'instant." else "De ${cfg.silenceDebut} h à ${cfg.silenceFin} h.") {
            cfg.definirSilencePour(s, it); maj()
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) TextButton(onClick = {
            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, Moteur.canalMessages(s))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Son, vibration et pop-up")
        }
    }
    Section("Couleur") {
        RangeeCouleurs(Affichage.couleurs[s] ?: 0L, s.couleur) { c ->
            if (c == 0L) Affichage.couleurs.remove(s) else Affichage.couleurs[s] = c
            cfg.definirCouleur(s, c); maj()
        }
    }
    BlocConnexion(e, s)
    Section("Réception") {
        e.statistiques[s]?.let { Text("${it.first} conversations · ${it.second} messages archivés", fontSize = 13.sp) }
        BlocLatence(s, e.derniereActualisation)
    }
}

/** Puces à choix unique sur des valeurs texte (aucune choisie si valeur = null). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChoixTexte(options: List<Pair<String, String>>, valeur: String?, maj: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, l) -> FilterChip(selected = v == valeur, onClick = { maj(v) }, label = { Text(l, fontSize = 13.sp) }) }
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
    var lusSms by remember(version) { mutableStateOf(cfg.lusSmsGoogle) }
    Section("Lecture et réactions") {
    Interrupteur("Transmettre mes réactions aux contacts", reactions,
        "Désactivé, vos émoticônes restent sur ce téléphone. Rien ne part sans votre appui.") { reactions = it; cfg.envoyerReactions = it }
    Interrupteur("SMS lus aussi dans Google Messages", lusSms,
        "Un SMS lu ici (ou sur le Mac) passe en lu dans Google Messages. Gardez « Confirmations de lecture » désactivé dans Google Messages > Paramètres > RCS pour que vos contacts ne le voient pas. WhatsApp : toujours synchronisé, en « lu par moi » invisible des contacts. Jamais pour Telegram, Signal ni LinkedIn.") {
        lusSms = it; cfg.lusSmsGoogle = it
    }
    }
    // Statut de présence choisi par vous
    Section("Anti-spam", aide = "Les messages suspects d'expéditeurs inconnus (faux colis, banque, amende, liens douteux) vont dans " +
        "la vue « Spam » de la liste, sans notification. Tout est calculé sur ce téléphone : rien n'est signalé aux messageries.") {
        var antiSpam by remember(version) { mutableStateOf(cfg.antiSpam) }
        var spamIA by remember(version) { mutableStateOf(cfg.spamIA) }
        var appris by remember { mutableIntStateOf(fr.messagerie.unifiee.Spam.nombreAppris) }
        Interrupteur("Détecter le spam", antiSpam) { antiSpam = it; cfg.antiSpam = it; Moteur.rafraichirSpam() }
        if (antiSpam && cfg.ollamaConfigure) Interrupteur("Demander l'avis de l'IA pour les cas douteux", spamIA,
            "Ollama sur votre serveur relit les messages entre 30 et 60 % de risque.") { spamIA = it; cfg.spamIA = it; Moteur.rafraichirSpam() }
        Explication(if (appris == 0) "Touchez « C'est un spam » ou « Ce n'est pas un spam » dans une conversation : l'appli apprend de vos choix."
            else "Appris de $appris conversation(s) marquée(s) par vous.")
        if (appris > 0) TextButton(onClick = { fr.messagerie.unifiee.Spam.oublier(); appris = 0; Moteur.rafraichirSpam() }) { Text("Oublier ce qui a été appris") }
    }
    Section("Statut montré aux autres") { ChoixStatuts() }
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
        }
    }
    if (EtatMaj.nouveautes.isNotEmpty()) Section(if (dispo) "Ce qui change" else "Nouveautés de votre version") {
        EtatMaj.nouveautes.forEachIndexed { i, (tag, texte) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(tag, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            texte.lines().filter { it.isNotBlank() }.forEach { l ->
                val niveau = l.takeWhile { it == ' ' }.length / 2
                val t = l.trim().removePrefix("- ")
                Row(Modifier.padding(start = (niveau * 14).dp)) {
                    Text(if (niveau == 0) "•  " else if (l.trim().startsWith("- ")) "–  " else "    ", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t, fontSize = 14.sp, fontWeight = if (niveau == 0) FontWeight.Medium else FontWeight.Normal)
                }
            }
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
