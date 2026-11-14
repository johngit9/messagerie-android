package fr.messagerie.unifiee.ui

import androidx.compose.foundation.layout.Arrangement
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
    var test by remember { mutableStateOf<String?>(null) }

    fun enregistrer() {
        cfg.matrixUrls = matrixUrls; cfg.matrixUtilisateur = matrixUser; cfg.matrixJeton = matrixJeton; cfg.moiFantome = moi
        cfg.relaisUrls = relaisUrls; cfg.relaisJeton = relaisJeton
        cfg.ollamaUrls = ollamaUrls; cfg.ollamaModele = ollamaModele
        cfg.jours = jours; cfg.intervalle = intervalle; cfg.notifications = notifs; cfg.veilleArrierePlan = veille
        Moteur.redemarrer()
        ServiceVeille.mettreAJour(ctx)
        retour?.invoke()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Réglages") },
            navigationIcon = { if (retour != null) TextButton(onClick = retour) { Text("←", fontSize = 22.sp) } },
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

            Button(onClick = { enregistrer() }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }

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
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun Champ(libelle: String, valeur: String, aide: String, secret: Boolean = false, maj: (String) -> Unit) {
    OutlinedTextField(
        value = valeur, onValueChange = maj, label = { Text(libelle) },
        placeholder = { if (aide.isNotEmpty()) Text(aide, fontSize = 12.sp) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
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
