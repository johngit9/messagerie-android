package fr.messagerie.unifiee.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Etat
import fr.messagerie.unifiee.EtatSource
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Ollama
import fr.messagerie.unifiee.ServiceVeille
import fr.messagerie.unifiee.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranReglages(e: Etat, retour: (() -> Unit)?) {
    val cfg = Moteur.cfg
    val ctx = LocalContext.current
    val portee = rememberCoroutineScope()
    var matrixUrls by remember { mutableStateOf(cfg.matrixUrls) }
    var matrixUser by remember { mutableStateOf(cfg.matrixUtilisateur) }
    var matrixJeton by remember { mutableStateOf(cfg.matrixJeton) }
    var moi by remember { mutableStateOf(cfg.moiFantome) }
    var relaisUrls by remember { mutableStateOf(cfg.relaisUrls) }
    var relaisJeton by remember { mutableStateOf(cfg.relaisJeton) }
    var ollamaUrls by remember { mutableStateOf(cfg.ollamaUrls) }
    var ollamaModele by remember { mutableStateOf(cfg.ollamaModele) }
    var jours by remember { mutableStateOf(cfg.jours) }
    var intervalle by remember { mutableStateOf(cfg.intervalle) }
    var notifs by remember { mutableStateOf(cfg.notifications) }
    var veille by remember { mutableStateOf(cfg.veilleArrierePlan) }
    var reactions by remember { mutableStateOf(cfg.envoyerReactions) }
    var test by remember { mutableStateOf<String?>(null) }
    var fichier by remember { mutableStateOf<String?>(null) }

    fun recharger() {
        matrixUrls = cfg.matrixUrls; matrixUser = cfg.matrixUtilisateur; matrixJeton = cfg.matrixJeton; moi = cfg.moiFantome
        relaisUrls = cfg.relaisUrls; relaisJeton = cfg.relaisJeton
        ollamaUrls = cfg.ollamaUrls; ollamaModele = cfg.ollamaModele
        jours = cfg.jours; intervalle = cfg.intervalle; notifs = cfg.notifications; veille = cfg.veilleArrierePlan; reactions = cfg.envoyerReactions
    }

    fun enregistrerSansQuitter() {
        cfg.matrixUrls = matrixUrls; cfg.matrixUtilisateur = matrixUser; cfg.matrixJeton = matrixJeton; cfg.moiFantome = moi
        cfg.relaisUrls = relaisUrls; cfg.relaisJeton = relaisJeton
        cfg.ollamaUrls = ollamaUrls; cfg.ollamaModele = ollamaModele
        cfg.jours = jours; cfg.intervalle = intervalle; cfg.notifications = notifs; cfg.veilleArrierePlan = veille; cfg.envoyerReactions = reactions
    }

    fun enregistrer() {
        enregistrerSansQuitter()
        Moteur.redemarrer()
        ServiceVeille.mettreAJour(ctx)
        retour?.invoke()
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        fichier = try {
            val texte = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            val faits = cfg.importerJson(texte)
            recharger()
            if (faits.isEmpty()) "Ce fichier ne contient aucun réglage reconnu."
            else "Importé : ${faits.joinToString(", ")}. Vérifiez puis touchez « Enregistrer »."
        } catch (ex: Exception) {
            "Fichier illisible : ${ex.message ?: ex.javaClass.simpleName}"
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        fichier = try {
            enregistrerSansQuitter()
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(cfg.exporterJson().toByteArray()) }
            "Configuration exportée. Ce fichier contient vos jetons : gardez-le pour vous."
        } catch (ex: Exception) {
            "Export impossible : ${ex.message ?: ex.javaClass.simpleName}"
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Réglages") },
            navigationIcon = { if (retour != null) IconButton(onClick = retour) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Retour") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (retour == null) Text(
                "Bienvenue ! Renseignez l'adresse de votre serveur Matrix (passerelles SMS, WhatsApp, Telegram, LinkedIn) " +
                    "et/ou de votre relais Element. Rien n'est envoyé ailleurs que vers ces serveurs.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Fichier de configuration")
            Text(
                "Remplissez tout d'un coup avec un fichier .json (format de messagerie-config.exemple.json, " +
                    "ou le client.json / element.json de l'app Mac).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Importer…") }
                OutlinedButton(onClick = { exporter.launch("messagerie-config.json") }) { Text("Exporter…") }
            }
            fichier?.let { Text(it, fontSize = 13.sp) }

            Section("Serveur Matrix (Synapse)")
            Champ("Adresse(s) du serveur", matrixUrls, "http://serveur:8008  (plusieurs : séparées par des virgules)") { matrixUrls = it }
            Champ("Identifiant Matrix", matrixUser, "@utilisateur:domaine") { matrixUser = it }
            Champ("Jeton d'accès", matrixJeton, "", secret = true) { matrixJeton = it }
            Champ("Fantôme « moi » (optionnel)", moi, "@gmessages_…:domaine — détecté automatiquement sinon") { moi = it }

            Section("Relais Element")
            Champ("Adresse(s) du relais", relaisUrls, "http://serveur:8787") { relaisUrls = it }
            Champ("Jeton du relais", relaisJeton, "", secret = true) { relaisJeton = it }

            Section("IA (Ollama)")
            Champ("Adresse(s) d'Ollama", ollamaUrls, "http://serveur:11434  — vide = pas de résumés") { ollamaUrls = it }
            Champ("Modèle", ollamaModele, "qwen3:8b") { ollamaModele = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    cfg.ollamaUrls = ollamaUrls; cfg.ollamaModele = ollamaModele
                    test = "Test…"
                    portee.launch { test = withContext(Dispatchers.IO) { Ollama(cfg).tester() } }
                }) { Text("Tester la connexion") }
            }
            test?.let { Text(it, fontSize = 13.sp) }
            Text("Résumer les conversations des :")
            Choix(listOf(1 to "24 h", 3 to "3 jours", 7 to "7 jours", 14 to "14 jours"), jours) { jours = it }

            Section("Actualisation et notifications")
            Text("Vérification de secours toutes les :")
            Choix(listOf(30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min", 900 to "15 min"), intervalle) { intervalle = it }
            Interrupteur("Notifier quand une réponse est attendue", notifs) { notifs = it }
            Interrupteur("Rester à l'écoute en arrière-plan (notification permanente)", veille) { veille = it }
            Interrupteur("Transmettre mes réactions (émoticônes) aux contacts", reactions) { reactions = it }

            Button(onClick = { enregistrer() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(8.dp)); Text("Enregistrer", style = MaterialTheme.typography.titleSmall)
            }

            Section("Discrétion")
            Text(
                "Aucun accusé de lecture n'est envoyé : lu / non lu reste sur ce téléphone. " +
                    "Rien ne part sans votre appui sur « Envoyer ».",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (e.statistiques.isNotEmpty() || e.sources.values.any { it !is EtatSource.Inactif }) {
                Section("État des messageries")
                Source.entries.forEach { s ->
                    val st = e.sources[s]
                    val texte = when (st) {
                        is EtatSource.Ok -> "✅ ${st.conversations} conversation(s)"
                        is EtatSource.Erreur -> "⚠️ ${st.message}"
                        EtatSource.NonConfigure -> "non configuré"
                        else -> "…"
                    }
                    val stats = e.statistiques[s]?.let { " · ${it.second} messages" } ?: ""
                    Text("${s.libelle} : $texte$stats", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable private fun Section(t: String) {
    val icone = when {
        t.startsWith("Fichier") -> Icons.Outlined.Description
        t.startsWith("Serveur") -> Icons.Outlined.Dns
        t.startsWith("Relais") -> Icons.Outlined.Lock
        t.startsWith("IA") -> Icons.Outlined.AutoAwesome
        t.startsWith("Actualisation") -> Icons.Outlined.Notifications
        t.startsWith("Discrétion") -> Icons.Outlined.Shield
        else -> Icons.Outlined.Info
    }
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icone, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun Champ(libelle: String, valeur: String, aide: String, secret: Boolean = false, maj: (String) -> Unit) {
    OutlinedTextField(
        value = valeur, onValueChange = maj, label = { Text(libelle) },
        placeholder = { if (aide.isNotEmpty()) Text(aide, fontSize = 12.sp) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
    )
}

@Composable private fun Choix(options: List<Pair<Int, String>>, valeur: Int, maj: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, l) -> FilterChip(selected = v == valeur, onClick = { maj(v) }, label = { Text(l, fontSize = 12.sp) }) }
    }
}

@Composable private fun Interrupteur(libelle: String, valeur: Boolean, maj: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(libelle, modifier = Modifier.weight(1f))
        Switch(checked = valeur, onCheckedChange = maj)
    }
}
