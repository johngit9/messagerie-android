package fr.messagerie.unifiee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.messagerie.unifiee.Affichage
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Source

/** Couleurs proposées (principale et messageries). */
private val PALETTE = listOf(0xFF3B5BDB, 0xFF5B5FC7, 0xFF7048E8, 0xFFAE3EC9, 0xFFD6336C, 0xFFE03131,
    0xFFE8590C, 0xFFF59F00, 0xFF2F9E44, 0xFF0C8599, 0xFF1971C2, 0xFF495057)

/**
 * Réglages > Apparence et couleurs : un aperçu en direct en haut, puis quatre rubriques
 * (écran des messages, thème et couleur, taille, couleur de chaque messagerie). Tout s'applique aussitôt.
 */
@Composable
internal fun PageAffichage() {
    val cfg = Moteur.cfg
    var messagerieOuverte by remember { mutableStateOf<Source?>(null) }
    val nav = LocalSousMenu.current
    val sous = nav.actuel

    if (sous == null || sous == "Écran des messages") ApercuAccueil()
    if (sous == null) ListeSousMenus(null, listOf(
        ElementSous("Écran des messages", listOf("Classique", "Bandeau", "Épuré").getOrElse(Affichage.haut) { "Bandeau" } + " · " +
            listOf("Cartes", "Lignes", "Colorée", "Aérée").getOrElse(Affichage.design) { "Cartes" }, Icons.Outlined.ViewAgenda, 0xFF3B5BDB),
        ElementSous("Thème et couleur", listOf("Auto", "Clair", "Sombre").getOrElse(Affichage.theme) { "Auto" } +
            if (Affichage.dynamique) " · Material You" else "", Icons.Outlined.Palette, 0xFF9C36B5),
        ElementSous("Taille", "Texte ${Affichage.texte} % · interface ${Affichage.zoom} %", Icons.Outlined.FormatSize, 0xFF0C8599),
        ElementSous("Couleur de chaque messagerie", Affichage.couleurs.size.let { if (it == 0) "Couleurs d'origine" else "$it personnalisée(s)" },
            Icons.Outlined.ColorLens, 0xFFE8590C),
    ))

    if (sous == "Écran des messages") CarteRubrique(Icons.Outlined.ViewAgenda, "Écran des messages") {
        SousTitre("En-tête")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(1 to "Bandeau", 2 to "Épuré", 0 to "Classique").forEach { (v, nom) ->
                Tuile(nom, Affichage.haut == v, Modifier.weight(1f), clic = {
                    Affichage.haut = v; cfg.haut = v
                    // Épuré va avec la liste en lignes ; Bandeau avec les cartes
                    if (v == 2) { Affichage.design = 1; cfg.design = 1 } else if (v == 1 && Affichage.design == 1) { Affichage.design = 0; cfg.design = 0 }
                }) { Reduit(v, Affichage.design, Affichage.barreBas) }
            }
        }
        if (Affichage.haut == 1) {
            SousTitre("Couleur du bandeau")
            RangeeCouleurs(Affichage.bandeau, 0xFF22306E) { Affichage.bandeau = it; cfg.couleurBandeau = it }
        }
        SousTitre("Liste")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0 to "Cartes", 1 to "Lignes", 2 to "Colorée", 3 to "Aérée").forEach { (v, nom) ->
                Tuile(nom, Affichage.design == v, Modifier.weight(1f), clic = { Affichage.design = v; cfg.design = v }) { Reduit(Affichage.haut, v, Affichage.barreBas) }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 2.dp))
        Interrupteur("Barre des messageries en bas", Affichage.barreBas,
            "Désactivée, la liste prend tout l'écran ; le filtre par messagerie reste dans le menu ☰.") { Affichage.barreBas = it; cfg.barreBas = it }
    }

    if (sous == "Thème et couleur") CarteRubrique(Icons.Outlined.Palette, "Thème et couleur") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0 to "Auto", 1 to "Clair", 2 to "Sombre").forEach { (v, nom) ->
                Tuile(nom, Affichage.theme == v, Modifier.weight(1f), clic = { Affichage.theme = v; cfg.theme = v }) { MiniTheme(v) }
            }
        }
        Explication("Auto suit le mode clair ou sombre du téléphone.")
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            Interrupteur("Couleurs du fond d'écran", Affichage.dynamique,
                "Material You : l'app prend les teintes de votre fond d'écran.") { Affichage.dynamique = it; cfg.couleursDynamiques = it }
        }
        if (!Affichage.dynamique) {
            SousTitre("Couleur principale")
            RangeeCouleurs(Affichage.accent, 0xFF3B5BDB) { Affichage.accent = it; cfg.accent = it }
        }
    }

    if (sous == "Taille") CarteRubrique(Icons.Outlined.FormatSize, "Taille") {
        Curseur("Texte", listOf(85 to "Petit", 100 to "Normal", 115 to "Grand", 130 to "Très grand"), Affichage.texte) {
            Affichage.texte = it; cfg.tailleTexte = it
        }
        Curseur("Interface (boutons, marges)", listOf(80 to "80 %", 90 to "90 %", 100 to "100 %", 110 to "110 %", 120 to "120 %"), Affichage.zoom) {
            Affichage.zoom = it; cfg.zoom = it
        }
    }

    if (sous == "Couleur de chaque messagerie") CarteRubrique(Icons.Outlined.ColorLens, "Couleur de chaque messagerie") {
        Explication("Pour le logo, vos bulles, les boutons et les notifications. Touchez une messagerie pour la changer.")
        Source.entries.forEachIndexed { i, src ->
            if (i > 0) HorizontalDivider()
            val ouverte = messagerieOuverte == src
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)
                .clickable { messagerieOuverte = if (ouverte) null else src }) {
                LogoSource(src, 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(src.libelle, fontWeight = FontWeight.Medium)
                    if (Affichage.couleurs[src] != null) Text("Couleur personnalisée", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(20.dp).background(src.teinte(), CircleShape))
                Spacer(Modifier.width(8.dp))
                Icon(if (ouverte) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ouverte) Box(Modifier.padding(bottom = 8.dp)) {
                RangeeCouleurs(Affichage.couleurs[src] ?: 0L, src.couleur) { c ->
                    if (c == 0L) Affichage.couleurs.remove(src) else Affichage.couleurs[src] = c
                    cfg.definirCouleur(src, c)
                }
            }
        }
    }

    if (sous == null) OutlinedButton(onClick = {
        Affichage.zoom = 100; Affichage.texte = 100; Affichage.accent = 0L; Affichage.theme = 0; Affichage.couleurs.clear()
        Affichage.design = 0; Affichage.haut = 1; Affichage.barreBas = true; Affichage.bandeau = 0L
        cfg.zoom = 100; cfg.tailleTexte = 100; cfg.accent = 0L; cfg.theme = 0; cfg.design = 0; cfg.haut = 1; cfg.barreBas = true; cfg.couleurBandeau = 0L
        Source.entries.forEach { cfg.definirCouleur(it, 0L) }
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Text("Tout remettre comme à l'origine")
    }
}

// MARK: Briques

/** Carte de rubrique : icône + titre, puis le contenu. */
@Composable
private fun CarteRubrique(icone: ImageVector, titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(cs.primaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(icone, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Text(titre, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            contenu()
        }
    }
}

@Composable
private fun SousTitre(t: String) = Text(t, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Choix illustré par une miniature ; le choix actif est encadré de la couleur principale. */
@Composable
private fun Tuile(nom: String, actif: Boolean, modifier: Modifier, clic: () -> Unit, miniature: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .border(if (actif) 2.dp else 1.dp, if (actif) cs.primary else cs.outlineVariant, RoundedCornerShape(12.dp))
        .background(if (actif) cs.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
        .clickable(onClick = clic).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(cs.background)) { miniature() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actif) { Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), tint = cs.primary); Spacer(Modifier.width(2.dp)) }
            Text(nom, fontSize = 12.sp, fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
                color = if (actif) cs.primary else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TuileIcone(nom: String, icone: ImageVector, actif: Boolean, modifier: Modifier, clic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.height(64.dp).clip(RoundedCornerShape(12.dp))
        .border(if (actif) 2.dp else 1.dp, if (actif) cs.primary else cs.outlineVariant, RoundedCornerShape(12.dp))
        .background(if (actif) cs.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
        .clickable(onClick = clic),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icone, null, tint = if (actif) cs.primary else cs.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(nom, fontSize = 12.sp, fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal, color = if (actif) cs.primary else cs.onSurface)
    }
}

/** Rangée de pastilles qui défile ; la première (« A ») remet la couleur d'origine. */
@Composable
private fun RangeeCouleurs(choisie: Long, origine: Long, choisir: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(0L) + PALETTE.filter { it != origine }).forEach { c ->
                val actif = c == choisie
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { choisir(c) }
                    .border(2.dp, if (actif) cs.onSurface else Color.Transparent, CircleShape)
                    .padding(5.dp).background(Color(if (c == 0L) origine else c), CircleShape),
                    contentAlignment = Alignment.Center) {
                    if (actif) Icon(Icons.Outlined.Check, "Choisie", Modifier.size(18.dp), tint = Color.White)
                    else if (c == 0L) Text("A", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("« A » = couleur d'origine. Faites défiler pour voir toutes les couleurs.", fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

/** Curseur à crans avec le nom du cran choisi. */
@Composable
private fun Curseur(libelle: String, crans: List<Pair<Int, String>>, valeur: Int, maj: (Int) -> Unit) {
    val i = crans.indexOfFirst { it.first == valeur }.let { if (it < 0) crans.indexOfFirst { c -> c.first == 100 }.coerceAtLeast(0) else it }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(libelle, modifier = Modifier.weight(1f))
            Text(crans[i].second, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = i.toFloat(), onValueChange = { v ->
            val n = v.toInt().coerceIn(0, crans.size - 1)
            if (crans[n].first != valeur) maj(crans[n].first)
        }, valueRange = 0f..(crans.size - 1).toFloat(), steps = (crans.size - 2).coerceAtLeast(0))
    }
}

// MARK: Aperçu en direct

private val EXEMPLES = listOf(
    Triple(Source.WHATSAPP, "Camille", "On se voit demain à 10 h ?"),
    Triple(Source.SMS, "Club de vélo", "Sortie dimanche matin"),
    Triple(Source.TELEGRAM, "Julien", "Merci pour l'info !"),
    Triple(Source.SIGNAL, "Sophie", "Photo"),
    Triple(Source.ELEMENT, "Équipe projet", "Réunion déplacée à 14 h"),
)

/** Mini écran des messages qui suit en direct les réglages choisis. */
@Composable
private fun ApercuAccueil() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Aperçu", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EcranMini(Affichage.haut, Affichage.design, Affichage.barreBas, Modifier.fillMaxWidth().height(250.dp))
    }
}

/** L'écran des messages en vrai (vrais textes, logos, couleurs), pour l'aperçu et les vignettes. */
@Composable
private fun EcranMini(haut: Int, design: Int, barreBas: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
        Column(modifier.clip(RoundedCornerShape(20.dp))
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp)).background(cs.background)) {
            // En-tête
            when (haut) {
                1 -> Column(Modifier.fillMaxWidth().background(couleurBandeau()).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Messages", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Tout", "Non lues", "À traiter").forEachIndexed { j, t ->
                            Text(t, fontSize = 11.sp, color = if (j == 0) couleurBandeau() else Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.background(if (j == 0) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxWidth().background(cs.surface).padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
                    Text("Messages", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("Tout", "Non lues", "À traiter").forEachIndexed { j, t ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(t, fontSize = 11.sp, color = if (j == 0) cs.primary else cs.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.width(24.dp).height(2.dp).background(if (j == 0) cs.primary else Color.Transparent))
                            }
                        }
                    }
                    HorizontalDivider()
                }
                else -> Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Messages", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Tout", "Non lues", "À traiter").forEachIndexed { j, t ->
                            Text(t, fontSize = 11.sp, color = if (j == 0) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.background(if (j == 0) cs.primaryContainer else cs.surfaceContainerHigh, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            // Liste
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (design == 1) 0.dp else 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(if (design == 1) 0.dp else if (design == 3) 8.dp else 5.dp)) {
                EXEMPLES.forEachIndexed { j, (src, nom, texte) ->
                    val forme = RoundedCornerShape(10.dp)
                    val fond = when (design) { 1 -> Color.Transparent; 2 -> src.teinte().copy(alpha = 0.08f); else -> cs.surfaceContainerLow }
                    Row(Modifier.fillMaxWidth().clip(forme).background(fond, forme)
                        .then(if (design == 1) Modifier else Modifier.border(1.dp, if (design == 2) src.teinte().copy(alpha = 0.25f) else cs.outlineVariant, forme))
                        .padding(horizontal = 10.dp, vertical = if (design == 3) 9.dp else if (design == 1) 5.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        LogoSource(src, if (design == 3) 26.dp else 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(nom, fontSize = 12.sp, fontWeight = if (j == 0) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                            Text(texte, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (j == 0) Box(Modifier.size(16.dp).background(cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text("2", fontSize = 9.sp, color = cs.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (design == 1 && j < EXEMPLES.lastIndex) HorizontalDivider(Modifier.padding(start = 40.dp))
                }
            }
            // Barre du bas
            if (barreBas) Row(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM, Source.SIGNAL, Source.ELEMENT).forEach { LogoSource(it, 16.dp) }
            }
        }
}

/** Vignette : le vrai écran, réduit (comme une capture). */
@Composable
private fun Reduit(haut: Int, design: Int, barreBas: Boolean) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(300f / 420f).clipToBounds()) {
        val s = maxWidth / 300.dp
        Box(Modifier.requiredSize(300.dp, 420.dp).graphicsLayer { scaleX = s; scaleY = s }) {
            EcranMini(haut, design, barreBas, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun EcranTheme(sombre: Boolean) {
    val p = fr.messagerie.unifiee.paletteMessagerie(sombre)
    MaterialTheme(colorScheme = p) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides p.onSurface) {
            Reduit(Affichage.haut, Affichage.design, Affichage.barreBas)
        }
    }
}

/** Vignette du thème : clair, sombre, ou moitié-moitié pour « Auto ». */
@Composable
private fun MiniTheme(v: Int) {
    when (v) {
        1 -> EcranTheme(false)
        2 -> EcranTheme(true)
        else -> Box {
            EcranTheme(false)
            Box(Modifier.matchParentSize().drawWithContent { clipRect(left = size.width / 2) { this@drawWithContent.drawContent() } }) { EcranTheme(true) }
        }
    }
}

/** Notification telle qu'elle apparaîtra, selon les réglages choisis. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ApercuNotification(photo: Boolean, boutons: Boolean, bulles: Boolean, apercu: Int) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoSource(Source.WHATSAPP, 16.dp); Spacer(Modifier.width(6.dp))
            Text("Messagerie · WhatsApp · maintenant", fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(
                if (photo) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF4A261), Color(0xFFE76F51)))
                else androidx.compose.ui.graphics.SolidColor(Source.WHATSAPP.teinte())), contentAlignment = Alignment.Center) {
                if (photo) Icon(Icons.Filled.Face, null, Modifier.size(34.dp), tint = Color.White.copy(alpha = 0.95f))
                else Text("C", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Camille", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(if (apercu == 1) "Nouveau message" else "On se voit demain à 10 h ?", fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
        }
        if (boutons || bulles) Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (boutons) listOf("Répondre", "Marquer comme lu", "Silence").forEach {
                    Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.background(cs.surfaceContainerHighest, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            if (bulles) Box(Modifier.size(34.dp).background(cs.surfaceContainerHighest, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BubbleChart, "Bulle", Modifier.size(18.dp))
            }
        }
    }
}

// MARK: Miniatures des tuiles

@Composable
private fun Trait(largeur: Float, couleur: Color, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth(largeur).height(3.dp).background(couleur, RoundedCornerShape(2.dp)))

@Composable
private fun MiniEntete(v: Int) {
    val cs = MaterialTheme.colorScheme
    val gris = cs.onSurfaceVariant.copy(alpha = 0.35f)
    Column(Modifier.fillMaxSize()) {
        when (v) {
            1 -> Column(Modifier.fillMaxWidth().background(couleurBandeau()).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Trait(0.5f, Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.width(14.dp).height(5.dp).background(Color.White, RoundedCornerShape(3.dp)))
                    Box(Modifier.width(14.dp).height(5.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp)))
                }
            }
            2 -> Column(Modifier.fillMaxWidth().padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Trait(0.5f, cs.onSurface.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.width(12.dp).height(2.dp).background(cs.primary))
                    Box(Modifier.width(12.dp).height(2.dp).background(gris))
                }
            }
            else -> Column(Modifier.fillMaxWidth().padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth(0.65f).height(6.dp).background(cs.onSurface.copy(alpha = 0.7f), RoundedCornerShape(2.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.width(12.dp).height(5.dp).background(cs.primaryContainer, RoundedCornerShape(2.dp)))
                    Box(Modifier.width(12.dp).height(5.dp).background(cs.surfaceContainerHigh, RoundedCornerShape(2.dp)))
                }
            }
        }
        Column(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Trait(0.9f, gris); Trait(0.7f, gris)
        }
    }
}

@Composable
private fun MiniListe(v: Int) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(if (v == 1) 1.dp else 3.dp, Alignment.CenterVertically)) {
        repeat(3) { i ->
            val coul = listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM)[i].teinte()
            Row(Modifier.fillMaxWidth().height(if (v == 3) 13.dp else if (v == 1) 10.dp else 11.dp)
                .background(when (v) { 1 -> Color.Transparent; 2 -> coul.copy(alpha = 0.15f); else -> cs.surfaceContainerHigh }, RoundedCornerShape(3.dp))
                .padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (v == 3) 9.dp else 7.dp).background(coul, CircleShape))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.weight(1f).height(3.dp).background(cs.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
            }
        }
    }
}
