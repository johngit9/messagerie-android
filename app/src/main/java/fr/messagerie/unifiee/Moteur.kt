package fr.messagerie.unifiee

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Instantané de tout ce que l'interface affiche. */
data class Etat(
    val conversations: List<Conversation> = emptyList(),
    val etats: Map<String, EtatConv> = emptyMap(),
    val syntheses: Map<String, Synthese> = emptyMap(),
    val sources: Map<Source, EtatSource> = Source.entries.associateWith { EtatSource.Inactif },
    val statistiques: Map<Source, Pair<Int, Int>> = emptyMap(),
    val rcs: Set<String> = emptySet(),
    val salonsWhatsApp: Map<String, String> = emptyMap(),   // jid WhatsApp → salon Matrix
    val enCours: Boolean = false,
    val derniereActualisation: Long? = null,
    val importEnCours: String? = null,
    val resumesEnCours: Set<String> = emptySet(),
    val jours: Int = 3,
    val debutLecture: Long = 0,
    val pro: Set<Source> = emptySet(),
    val categories: Map<String, String> = emptyMap(),
    val archivees: Map<String, Long> = emptyMap(),
    val epinglees: List<String> = emptyList(),
) {
    fun estPro(c: Conversation) = categories[c.cle]?.let { it == "pro" } ?: (c.source in pro)
    /** Archivée tant qu'aucun message n'est reçu après l'archivage. */
    fun estArchivee(c: Conversation) = archivees[c.cle]?.let { a -> c.messages.none { !it.deMoi && it.date > a } } ?: false
    fun estEpinglee(c: Conversation) = c.cle in epinglees

    fun etat(c: Conversation) = etats[c.cle] ?: EtatConv()

    fun synthese(c: Conversation): Synthese? = syntheses[c.cle]?.takeIf { it.pourLeMessageDu == c.derniere }

    /** Date jusqu'à laquelle la conversation est lue (vos propres réponses valent lecture). */
    fun luJusqua(c: Conversation): Long =
        maxOf(etat(c).lue, debutLecture, c.messages.lastOrNull { it.deMoi }?.date ?: 0)

    fun nombreNonLus(c: Conversation): Int { val d = luJusqua(c); return c.messages.count { !it.deMoi && it.date > d } }
    fun estForcee(c: Conversation) = etat(c).forcee == c.derniere
    fun estNonLue(c: Conversation) = estForcee(c) || nombreNonLus(c) > 0
    /** Non-lus (messages) d'une messagerie, en option seulement Pro (true) ou Perso (false) ; archivées exclues. */
    fun nombreMessagesNonLus(s: Source?, pro: Boolean? = null) =
        conversations.filter { (s == null || it.source == s) && (pro == null || estPro(it) == pro) && !estArchivee(it) }
            .sumOf { maxOf(nombreNonLus(it), if (estForcee(it)) 1 else 0) }

    val depuis get() = System.currentTimeMillis() - jours * 86_400_000L

    /** « À traiter » : l'IA attend une réponse de vous, vous n'avez pas répondu en dernier, pas déjà traitée. */
    fun aTraiter(c: Conversation): Boolean {
        val e = etat(c)
        if (e.manuel) return true
        if (c.dernierDeMoi || c.derniere < depuis) return false
        val s = synthese(c) ?: return false
        return s.actionRequise && e.traitee != c.derniere
    }

    val nombreATraiter get() = conversations.count { aTraiter(it) && !estArchivee(it) }
    fun nombreATraiter(s: Source?, pro: Boolean?) =
        conversations.count { (s == null || it.source == s) && (pro == null || estPro(it) == pro) && aTraiter(it) && !estArchivee(it) }
    fun libelleSource(c: Conversation) = if (c.cle in rcs) "RCS" else c.source.id
}

object Moteur {
    lateinit var app: Context
    lateinit var cfg: Config
    lateinit var archive: Archive

    /** Une erreur dans une tâche de fond est notée dans le rapport, sans fermer l'appli. */
    private val gardien = CoroutineExceptionHandler { _, t -> Plantages.noter(t, fatal = false) }
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO + gardien)
    /** Au plus 2 passerelles importent en même temps (mémoire et base de données). */
    private val passerelles = Semaphore(2)
    private val _etat = MutableStateFlow(Etat())
    val etat: StateFlow<Etat> = _etat
    private val verrou = Mutex()
    private var boucle: Job? = null
    private var veilleMatrix: Job? = null
    private var veilleElement: Job? = null
    private var fileIA: Job? = null

    const val CANAL = "attendue"

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        cfg = Config(app)
        archive = Archive(app)
        chargerOrganisation()
        _etat.update { it.copy(jours = cfg.jours, debutLecture = cfg.debutLecture,
            rcs = archive.curseur("sms.rcs")?.split("\n")?.filter { s -> s.isNotEmpty() }?.toSet() ?: emptySet()) }
        if (Build.VERSION.SDK_INT >= 26) {
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CANAL, "Réponse attendue", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) })
            runCatching { app.getSystemService(NotificationManager::class.java).deleteNotificationChannel("reponse_attendue") }
        }
        portee.launch {
            // Correspondance WhatsApp de la dernière synchro : l'envoi marche avant la première actualisation
            runCatching {
                archive.curseur("whatsapp.salons")?.let { t ->
                    val j = org.json.JSONObject(t)
                    val m = j.keys().asSequence().associateWith { j.getString(it) }
                    if (_etat.value.salonsWhatsApp.isEmpty()) _etat.update { it.copy(salonsWhatsApp = m.entries.associate { (salon, jid) -> jid to salon }) }
                }
            }
            recharger()
        }
        // Non-lus par messagerie dans les raccourcis de l'icône (appui long)
        portee.launch {
            _etat.debounce(1500).collect { e ->
                runCatching { Raccourcis.mettreAJour(app, e) }
                runCatching { Pastille.afficher(app, e.nombreMessagesNonLus(null)) }
                runCatching { WidgetNonLus.mettreAJour(app, e) }
            }
        }
    }

    /** Démarre l'actualisation périodique et la réception en temps réel (idempotent). */
    fun demarrer() {
        if (boucle?.isActive != true) boucle = portee.launch {
            while (isActive) {
                actualiser()
                delay(cfg.intervalle * 1000L)
            }
        }
        if (veilleMatrix?.isActive != true && cfg.matrixConfigure) veilleMatrix = portee.launch { veilleMatrix() }
        if (veilleElement?.isActive != true && cfg.relaisConfigure) veilleElement = portee.launch { veilleElement() }
    }

    fun redemarrer() {
        boucle?.cancel(); veilleMatrix?.cancel(); veilleElement?.cancel()
        Matrix.derniereBonne = null; Relais.derniereBonne = null; SyncPont.viderCache()
        _etat.update { it.copy(jours = cfg.jours) }
        demarrer()
    }

    /** Long-poll /sync : dès qu'un message ou un accusé arrive, on actualise. */
    private suspend fun veilleMatrix() {
        var jeton: String? = null
        var pause = 2L
        while (portee.isActive) {
            try {
                val (suivant, nouveau) = Matrix(cfg).attendreNouveautes(jeton)
                jeton = suivant; pause = 2
                if (nouveau) actualiser()
            } catch (e: Exception) {
                delay(pause * 1000); pause = minOf(pause * 2, 60)
            }
        }
    }

    private suspend fun veilleElement() {
        var version: Int? = null
        var pause = 2L
        while (portee.isActive) {
            try {
                val apres = archive.curseur("element.seq")?.toLongOrNull() ?: 0
                val (nouveau, v) = Relais(cfg).attendre(apres, version)
                val premier = version == null
                version = v; pause = 2
                if (nouveau && !premier) actualiser()
            } catch (e: Exception) {
                delay(pause * 1000); pause = minOf(pause * 2, 60)
            }
        }
    }

    fun actualiserMaintenant() { portee.launch { actualiser() } }

    @Volatile private var relanceDemandee = false

    suspend fun actualiser(relancerSiOccupe: Boolean = false) {
        if (!verrou.tryLock()) { if (relancerSiOccupe) relanceDemandee = true; return }
        _etat.update { it.copy(enCours = true) }
        try {
            if (_etat.value.conversations.isEmpty()) recharger()
            val sources = _etat.value.sources.toMutableMap()

            // Toutes les messageries se synchronisent en même temps (et non plus l'une après l'autre).
            coroutineScope {
                val element = async {
                    if (!cfg.relaisConfigure) return@async EtatSource.NonConfigure
                    try {
                        if (archive.curseur("element.seq") == null) _etat.update { it.copy(importEnCours = "Import de l'historique Element…") }
                        val n = Relais(cfg).synchroniser(archive)
                        runCatching { Relais(cfg).lus() }.getOrNull()?.let { appliquerLectures(it.second) }
                        if (n >= Relais.MAX_PAR_PASSAGE) _etat.update { it.copy(importEnCours = "Import de l'historique Element…") }
                        EtatSource.Ok(0)
                    } catch (e: Exception) { EtatSource.Erreur(e.message ?: e.toString()) }
                }
                val teams = async {
                    if (!cfg.teamsConfigure) return@async EtatSource.NonConfigure
                    try { Teams(cfg).synchroniser(archive); EtatSource.Ok(0) }
                    catch (e: Exception) {
                        EtatSource.Erreur(e.message ?: e.toString())
                    }
                }
                if (cfg.matrixConfigure) {
                    val whatsapp = async {
                        // WhatsApp via la passerelle du serveur (correspondance fournie par le relais).
                        var correspondance: Map<String, String>? = null
                        if (cfg.relaisConfigure) correspondance = runCatching { Relais(cfg).salonsWhatsApp() }.getOrNull()
                        if (!correspondance.isNullOrEmpty()) {
                            memoriserSalonsWhatsApp(correspondance)
                            synchroniserPont(Pont.WHATSAPP, correspondance, sources[Source.WHATSAPP])
                        } else if (archive.aDesConversations(Source.WHATSAPP))
                            EtatSource.Erreur("passerelle WhatsApp injoignable") else EtatSource.NonConfigure
                    }
                    val sms = async { synchroniserPont(Pont.SMS, null, sources[Source.SMS], avecRcs = true) }
                    val telegram = async { synchroniserPont(Pont.TELEGRAM, null, sources[Source.TELEGRAM]) }
                    val linkedin = async { synchroniserPont(Pont.LINKEDIN, null, sources[Source.LINKEDIN]) }
                    sources[Source.SMS] = sms.await()
                    sources[Source.TELEGRAM] = telegram.await()
                    sources[Source.LINKEDIN] = linkedin.await()
                    sources[Source.WHATSAPP] = whatsapp.await()
                    _etat.update { it.copy(sources = sources.toMap()) }
                    recharger()
                } else {
                    for (s in listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM, Source.LINKEDIN)) sources[s] = EtatSource.NonConfigure
                }
                sources[Source.ELEMENT] = element.await()
                sources[Source.TEAMS] = teams.await()
            }

            _etat.update { it.copy(sources = sources.toMap()) }
            recharger()
            _etat.update { it.copy(derniereActualisation = System.currentTimeMillis()) }
            runCatching { nouveauxMessages() }.onFailure { Plantages.noter(it, fatal = false) }
            runCatching { lancerResumes() }.onFailure { Plantages.noter(it, fatal = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Plantages.noter(e, fatal = false)
        } finally {
            _etat.update { it.copy(enCours = false, importEnCours = null) }
            verrou.unlock()
        }
        // Un envoi a eu lieu pendant l'actualisation : on repasse pour récupérer son écho.
        if (relanceDemandee) { relanceDemandee = false; portee.launch { actualiser() } }
    }

    private suspend fun synchroniserPont(pont: Pont, correspondance: Map<String, String>?, avant: EtatSource?, avecRcs: Boolean = false): EtatSource =
        passerelles.withPermit { synchroniserPontSeul(pont, correspondance, avant, avecRcs) }

    private suspend fun synchroniserPontSeul(pont: Pont, correspondance: Map<String, String>?, avant: EtatSource?, avecRcs: Boolean): EtatSource {
        val initial = pont.source.id.lowercase() + ".initial"
        return try {
            if (archive.curseur(initial) == null) _etat.update { it.copy(importEnCours = "Import de l'historique ${pont.source.libelle}…") }
            val s = SyncPont(Matrix(cfg), pont, archive, correspondance).avecMoi(cfg.moiFantome)
            s.synchroniser()
            appliquerLectures(s.lectures)
            runCatching { s.completerHistorique() }
            if (avecRcs) s.rcs?.let { r ->
                archive.definirCurseur("sms.rcs", r.sorted().joinToString("\n"))
                _etat.update { it.copy(rcs = r) }
            }
            if (!archive.aDesConversations(pont.source)) return EtatSource.NonConfigure
            archive.definirCurseur(initial, "fait")
            EtatSource.Ok(0)
        } catch (e: Exception) {
            if (archive.aDesConversations(pont.source) || avant !is EtatSource.NonConfigure) EtatSource.Erreur(e.message ?: e.toString())
            else EtatSource.NonConfigure
        }
    }

    /** Lectures faites sur un autre appareil : la conversation passe en lue (rien n'est envoyé). */
    private fun appliquerLectures(lectures: Map<String, Long>) {
        if (lectures.isEmpty()) return
        val etats = archive.etats()
        for ((cle, d) in lectures) {
            val e = etats[cle] ?: EtatConv()
            if (d > e.lue) archive.enregistrerEtat(cle, e.copy(lue = d))
        }
    }

    fun recharger() {
        val e = _etat.value
        val convs = archive.conversations(e.depuis, 60)
        val stats = archive.statistiques()
        val sources = e.sources.mapValues { (s, st) -> if (st is EtatSource.Ok) EtatSource.Ok(stats[s]?.first ?: 0) else st }
        _etat.update { it.copy(conversations = convs, statistiques = stats, sources = sources,
            etats = archive.etats(), syntheses = archive.syntheses()) }
    }

    private fun majEtat(cle: String, f: (EtatConv) -> EtatConv) {
        val n = f(_etat.value.etats[cle] ?: EtatConv())
        _etat.update { it.copy(etats = it.etats + (cle to n)) }
        // Écriture hors du fil d'affichage : la base peut être occupée par une synchro.
        portee.launch { archive.enregistrerEtat(cle, n) }
    }

    // MARK: Lu / non lu — LOCAL uniquement

    fun marquerLue(c: Conversation) {
        runCatching { NotificationManagerCompat.from(app).cancel(c.cle.hashCode()) }   // le chiffre de l'icône baisse
        if (!_etat.value.estNonLue(c)) return
        majEtat(c.cle) { it.copy(lue = c.derniere, forcee = 0) }
    }
    fun marquerNonLue(c: Conversation) = majEtat(c.cle) { it.copy(forcee = c.derniere) }
    fun toutMarquerLu() = _etat.value.let { e ->
        e.conversations.filter { e.estNonLue(it) }.forEach { c -> majEtat(c.cle) { it.copy(lue = c.derniere, forcee = 0) } }
        runCatching { NotificationManagerCompat.from(app).cancelAll() }
    }
    fun marquerTraitee(c: Conversation) = majEtat(c.cle) { it.copy(traitee = c.derniere, manuel = false) }
    fun basculerATraiter(c: Conversation) = majEtat(c.cle) { if (it.manuel) it.copy(manuel = false) else it.copy(manuel = true, traitee = 0) }
    fun basculerSourdine(c: Conversation) = majEtat(c.cle) { it.copy(sourdine = !it.sourdine) }

    // MARK: IA

    private fun lancerResumes() {
        if (!cfg.ollamaConfigure || fileIA?.isActive == true) return
        fileIA = portee.launch {
            val faites = HashSet<String>()
            while (isActive) {
                val e = _etat.value
                val c = e.conversations.firstOrNull { it.derniere >= e.depuis && e.synthese(it) == null && it.cle !in faites } ?: break
                faites.add(c.cle)
                if (!resumer(c)) break   // Ollama injoignable : on réessaiera à la prochaine actualisation
            }
        }
    }

    /** Attend la fin des résumés en cours (synchro en arrière-plan). */
    suspend fun attendreResumes() { fileIA?.join() }

    /** Résume une conversation ; false si Ollama ne répond pas. */
    suspend fun resumer(c: Conversation): Boolean {
        _etat.update { it.copy(resumesEnCours = it.resumesEnCours + c.cle) }
        try {
            val msgs = withContext(Dispatchers.IO) { archive.messages(c.cle, null, 60) }.ifEmpty { c.messages }
            val s = withContext(Dispatchers.IO) { Ollama(cfg).synthetiser(c, msgs) } ?: return false
            withContext(Dispatchers.IO) { archive.enregistrerSynthese(c.cle, s) }
            _etat.update { it.copy(syntheses = it.syntheses + (c.cle to s)) }
            notifierSiBesoin(c)
            return true
        } finally {
            _etat.update { it.copy(resumesEnCours = it.resumesEnCours - c.cle) }
        }
    }

    fun regenerer(c: Conversation) {
        _etat.update { it.copy(syntheses = it.syntheses - c.cle) }
        portee.launch { archive.enregistrerSynthese(c.cle, null); resumer(c) }
    }

    suspend fun ameliorer(brouillon: String, facon: Ollama.Facon, c: Conversation): String? = withContext(Dispatchers.IO) {
        if (!cfg.ollamaConfigure) null else Ollama(cfg).ameliorer(brouillon, facon, c, archive.messages(c.cle, null, 30))
    }

    // MARK: Envoi — uniquement sur le bouton « Envoyer »

    /** Renvoie null si envoyé, sinon le message d'erreur. */
    suspend fun envoyer(texte: String, c: Conversation, cite: Message?): String? = withContext(Dispatchers.IO) {
        val t = texte.trim()
        if (t.isEmpty()) return@withContext "message vide"
        val idCite = cite?.cle?.substringAfter(':')?.takeIf { it.startsWith("$") }
        try {
            val eid: String = when (c.source) {
                Source.TEAMS -> return@withContext "Teams est en lecture seule : répondez depuis l'appli Teams"
                Source.ELEMENT -> { Relais(cfg).envoyer(c.jid, t, idCite); "" }
                Source.WHATSAPP -> {
                    // Correspondance discussion → salon : en mémoire, sinon redemandée au relais tout de suite
                    val salon = _etat.value.salonsWhatsApp[c.jid]
                        ?: runCatching { Relais(cfg).salonsWhatsApp() }.getOrNull()?.takeIf { it.isNotEmpty() }
                            ?.let { memoriserSalonsWhatsApp(it); _etat.value.salonsWhatsApp[c.jid] }
                        ?: return@withContext if (_etat.value.salonsWhatsApp.isEmpty())
                            "relais injoignable : impossible de trouver la discussion WhatsApp sur le serveur"
                        else "cette discussion WhatsApp n'est pas reliée au serveur (passerelle)"
                    Matrix(cfg).envoyer(salon, t, idCite)
                }
                else -> Matrix(cfg).envoyer(c.jid, t, idCite)
            }
            afficherEnvoye(c, eid, t)
            null
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    /** Salon Matrix d'une conversation (WhatsApp : via la correspondance du relais). */
    private fun salonPourEnvoi(c: Conversation): String? = when (c.source) {
        Source.WHATSAPP -> _etat.value.salonsWhatsApp[c.jid]
            ?: runCatching { Relais(cfg).salonsWhatsApp() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { memoriserSalonsWhatsApp(it); _etat.value.salonsWhatsApp[c.jid] }
        Source.ELEMENT, Source.TEAMS -> null
        else -> c.jid
    }

    /** Peut-on envoyer un vocal dans cette conversation ? (passerelles Matrix seulement) */
    fun vocalPossible(c: Conversation) = cfg.matrixConfigure && c.source != Source.ELEMENT && c.source != Source.TEAMS

    /** Envoie un message vocal enregistré — UNIQUEMENT sur le bouton « Envoyer » de l'aperçu. */
    suspend fun envoyerVocal(f: java.io.File, typeMime: String, dureeMs: Long, c: Conversation): String? = withContext(Dispatchers.IO) {
        try {
            if (!vocalPossible(c)) return@withContext "envoi de vocaux impossible pour ${c.source.libelle}"
            val salon = salonPourEnvoi(c) ?: return@withContext "discussion introuvable sur le serveur"
            val (eid, mxc) = Matrix(cfg).envoyerVocal(salon, f.readBytes(), typeMime, f.name, dureeMs)
            afficherEnvoye(c, eid, "", type = "audio", media = "$mxc#$typeMime", duree = dureeMs / 1000.0)
            null
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    /** Le message envoyé s'affiche tout de suite (même clé que l'écho du serveur : pas de doublon). */
    private fun afficherEnvoye(c: Conversation, eid: String, texte: String, type: String = "texte", media: String? = null, duree: Double? = null) {
        if (eid.isNotEmpty()) runCatching {
            archive.enregistrerMessages(listOf(Message("${c.source.id}:$eid", c.cle, System.currentTimeMillis(), true, "Vous", texte, type, media, duree)))
            recharger()
        }
        val n = _etat.value.conversations.firstOrNull { it.cle == c.cle } ?: c
        majEtat(c.cle) { it.copy(traitee = maxOf(n.derniere, c.derniere), manuel = false, lue = maxOf(n.derniere, c.derniere), forcee = 0) }
        portee.launch { delay(1500); actualiser(relancerSiOccupe = true) }
    }

    /** Garde la correspondance salon Matrix → discussion WhatsApp (aussi dans l'archive, pour envoyer dès l'ouverture). */
    private fun memoriserSalonsWhatsApp(salonVersJid: Map<String, String>) {
        _etat.update { it.copy(salonsWhatsApp = salonVersJid.entries.associate { (salon, jid) -> jid to salon }) }
        runCatching { archive.definirCurseur("whatsapp.salons", org.json.JSONObject(salonVersJid).toString()) }
    }

    // MARK: Réactions — uniquement sur un appui de l'utilisateur

    /** Une réaction peut-elle être transmise au contact ? (message Matrix, hors Element, réglage activé) */
    fun reactionTransmissible(m: Message, c: Conversation): Boolean =
        cfg.envoyerReactions && c.source != Source.ELEMENT && m.cle.substringAfter(':').startsWith("$") && salonDe(c) != null

    private fun salonDe(c: Conversation): String? =
        if (c.source == Source.WHATSAPP) _etat.value.salonsWhatsApp[c.jid] else c.jid

    /** Pose (emoji) ou retire (null) votre réaction. Renvoie null si tout va bien, sinon le message d'erreur. */
    suspend fun reagir(m: Message, c: Conversation, emoji: String?): String? = withContext(Dispatchers.IO) {
        try {
            val avant = archive.maReaction(m.cle)
            if (avant?.idEnvoi != null && avant.salon != null) {
                Matrix(cfg).retirerReaction(avant.salon, avant.idEnvoi)
                archive.oublierReaction(avant.idEnvoi)
            }
            if (emoji == null) { archive.definirMaReaction(m.cle, null); return@withContext null }
            var salon: String? = null; var id: String? = null
            if (reactionTransmissible(m, c)) {
                salon = salonDe(c)
                id = Matrix(cfg).reagir(salon!!, m.cle.substringAfter(':'), emoji).takeIf { it.isNotEmpty() }
            }
            archive.definirMaReaction(m.cle, MaReaction(m.cle, c.cle, emoji, salon, id))
            null
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    // MARK: Médias (lecture seule)

    /** Fichier local d'un média (téléchargé une fois, puis gardé en cache). */
    suspend fun fichierMedia(m: Message): java.io.File = withContext(Dispatchers.IO) {
        val media = m.media ?: throw IllegalStateException("média absent")
        val parts = media.split('#')
        val ref = parts[0]
        val mime = parts.getOrNull(1) ?: "application/octet-stream"
        val dossier = java.io.File(app.cacheDir, if (m.type == "audio") "vocaux" else "medias").apply { mkdirs() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(ref.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        val f = java.io.File(dossier, "$hash.${extension(mime, m.type)}")
        if (f.exists() && f.length() > 0) return@withContext f
        val donnees = when {
            ref.startsWith("mxc://") -> Matrix(cfg).media(ref)
            ref.startsWith("element:") -> Relais(cfg).media(ref.removePrefix("element:"))
            else -> throw IllegalStateException("média introuvable")
        }
        val tmp = java.io.File(dossier, f.name + ".part")
        tmp.writeBytes(donnees)
        tmp.renameTo(f)
        f
    }

    /** Fichier déjà téléchargé d'un média (sans le télécharger), ou null. */
    fun mediaEnCache(m: Message): java.io.File? {
        val parts = m.media?.split('#') ?: return null
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(parts[0].toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        return java.io.File(java.io.File(app.cacheDir, "medias"), "$hash.${extension(parts.getOrNull(1) ?: "", m.type)}").takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * Aperçu d'une vidéo : vignette envoyée par la messagerie, sinon image tirée de la vidéo
     * si elle est déjà sur le téléphone (ou si telecharger = true). null si aucun aperçu possible.
     */
    suspend fun vignetteVideo(m: Message, telecharger: Boolean = false): java.io.File? = withContext(Dispatchers.IO) {
        val parts = m.media?.split('#') ?: return@withContext null
        val dossier = java.io.File(app.cacheDir, "medias").apply { mkdirs() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(parts[0].toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        val f = java.io.File(dossier, "$hash-apercu.jpg")
        if (f.exists() && f.length() > 0) return@withContext f
        val octets: ByteArray? = parts.getOrNull(3)?.takeIf { it.startsWith("mxc://") }?.let { v -> runCatching { Matrix(cfg).media(v) }.getOrNull() }
            ?: run {
                val video = mediaEnCache(m) ?: if (telecharger) runCatching { fichierMedia(m) }.getOrNull() else null
                video?.let { v ->
                    runCatching {
                        val r = android.media.MediaMetadataRetriever()
                        try {
                            r.setDataSource(v.absolutePath)
                            val image = r.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.frameAtTime
                            java.io.ByteArrayOutputStream().also { image?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
                        } finally { r.release() }
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                }
            }
        if (octets == null) return@withContext null
        f.writeBytes(octets); f
    }

    private val photos = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()

    /** Photo de contact d'une conversation (miniature en cache), ou null s'il n'y en a pas. */
    suspend fun photoContact(conv: String): java.io.File? = withContext(Dispatchers.IO) {
        photos[conv]?.let { return@withContext it }
        val mxc = archive.avatar(conv) ?: return@withContext null
        val dossier = java.io.File(app.cacheDir, "avatars").apply { mkdirs() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(mxc.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        val f = java.io.File(dossier, "$hash.img")
        if (!f.exists() || f.length() == 0L) {
            val donnees = runCatching { Matrix(cfg).miniature(mxc) }.getOrNull() ?: return@withContext null
            f.writeBytes(donnees)
        }
        photos[conv] = f
        f
    }

    private fun extension(mime: String, type: String): String {
        val m = mime.lowercase()
        return when {
            "ogg" in m || "opus" in m -> "ogg"
            "amr" in m -> "amr"
            "3gpp" in m -> "3gp"
            "mp4" in m && type == "audio" -> "m4a"
            "aac" in m -> "aac"
            "mpeg" in m && type == "audio" -> "mp3"
            "wav" in m -> "wav"
            "webm" in m -> "webm"
            else -> android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(m)
                ?: when (type) { "audio" -> "mp3"; "image" -> "jpg"; "video" -> "mp4"; else -> "bin" }
        }
    }

    // MARK: Notifications

    // MARK: Organisation (locale) : pro / perso, épingler, archiver

    fun chargerOrganisation() {
        _etat.update { it.copy(pro = Source.entries.filter { s -> cfg.pro(s) }.toSet(), categories = cfg.categories,
            archivees = cfg.archivees, epinglees = cfg.epinglees) }
    }
    fun classer(c: Conversation, categorie: String?) {
        cfg.categories = if (categorie == null) cfg.categories - c.cle else cfg.categories + (c.cle to categorie)
        chargerOrganisation()
    }
    fun basculerEpingle(c: Conversation) {
        val l = cfg.epinglees
        if (c.cle in l) cfg.epinglees = l - c.cle
        else { cfg.epinglees = l + c.cle; cfg.archivees = cfg.archivees - c.cle }
        chargerOrganisation()
    }
    fun basculerArchive(c: Conversation) {
        if (_etat.value.estArchivee(c)) cfg.archivees = cfg.archivees - c.cle
        else { cfg.archivees = cfg.archivees + (c.cle to System.currentTimeMillis()); cfg.epinglees = cfg.epinglees - c.cle }
        chargerOrganisation()
    }

    // MARK: Nouveaux messages : notification et délai de réception

    /** Appli visible (pas de notification alors) et conversation ouverte. */
    @Volatile var auPremierPlan = false
    @Volatile var convOuverte: String? = null
    const val CANAL_MESSAGES = "msg"

    /** Délais de réception mesurés (ms) : date d'arrivée sur le téléphone − date du message sur le serveur. */
    fun delais(s: Source): List<Long> = liste("delais.${s.id}")
    /** Découpage : réseau officiel → serveur (passerelle), et serveur → téléphone. */
    fun delaisPasserelle(s: Source): List<Long> = liste("delais.pont.${s.id}")
    fun delaisTelephone(s: Source): List<Long> = liste("delais.tel.${s.id}")
    private fun liste(k: String) = archive.curseur(k)?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    private fun ajouter(k: String, v: List<Long>) { if (v.isNotEmpty()) archive.definirCurseur(k, (liste(k) + v).takeLast(50).joinToString(",")) }

    private fun nouveauxMessages() {
        val maintenant = System.currentTimeMillis()
        val depuis = archive.curseur("vu.jusqua")?.toLongOrNull()
        archive.definirCurseur("vu.jusqua", maintenant.toString())
        // Messages déjà vus (clés) : un message arrivé en retard, daté d'avant le dernier passage, compte quand même.
        val vus = archive.curseur("vu.cles")?.split("\n")?.toHashSet() ?: hashSetOf()
        val e = _etat.value
        val recents = e.conversations.flatMap { c -> c.messages.filter { !it.deMoi && it.date > maintenant - 6 * 3_600_000L }.map { c to it } }
        archive.definirCurseur("vu.cles", recents.map { it.second.cle }.takeLast(3000).joinToString("\n"))
        if (depuis == null) return   // premier passage : rien à signaler
        val nouveaux = recents.filter { (_, m) -> m.cle !in vus && (m.date > depuis || vus.isNotEmpty()) }
        if (nouveaux.isEmpty()) return
        // Délai : seulement pour des messages arrivés « en direct » (moins de 10 min).
        nouveaux.groupBy { it.first.source }.forEach { (s, l) ->
            val enDirect = l.filter { maintenant - it.second.date in 0..3_600_000 }
            ajouter("delais.${s.id}", enDirect.map { maintenant - it.second.date })
            val decoupes = enDirect.mapNotNull { (_, m) -> ArriveesServeur.prendre(m.cle)?.let { srv -> (srv - m.date) to (maintenant - srv) } }
            ajouter("delais.pont.${s.id}", decoupes.map { it.first.coerceAtLeast(0) })
            ajouter("delais.tel.${s.id}", decoupes.map { it.second.coerceAtLeast(0) })
        }
        if (auPremierPlan) return
        nouveaux.filter { maintenant - it.second.date < 3_600_000 }.groupBy { it.first }.forEach { (c, l) ->
            if (cfg.notif(c.source) != "tous" || e.etat(c).sourdine || c.cle == convOuverte) return@forEach
            notifierMessages(c, l.map { it.second })
        }
    }

    /** Notification d'essai, locale (rien n'est envoyé sur le réseau). */
    fun notificationEssai(s: Source) {
        val c = Conversation("essai", s, "essai", "Messagerie (essai)", false, System.currentTimeMillis(), emptyList())
        notifierMessages(c, listOf(Message("essai:" + System.currentTimeMillis(), "essai", System.currentTimeMillis(), false,
            "Messagerie", "Ceci est une notification d'essai ${s.libelle}.")))
    }

    private fun notifierMessages(c: Conversation, msgs: List<Message>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val canal = "$CANAL_MESSAGES.${c.source.id}"
        if (Build.VERSION.SDK_INT >= 26) app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(canal, "Messages ${c.source.libelle}", NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(false) })
        if (Build.VERSION.SDK_INT >= 26) runCatching { app.getSystemService(NotificationManager::class.java).deleteNotificationChannel("messages.${c.source.id}") }
        val intent = Intent(app, MainActivity::class.java).putExtra("cle", c.cle)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(app, c.cle.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val moi = androidx.core.app.Person.Builder().setName("Vous").build()
        val style = NotificationCompat.MessagingStyle(moi).setConversationTitle(if (c.estGroupe) c.nom else null).setGroupConversation(c.estGroupe)
        msgs.takeLast(6).forEach { m ->
            style.addMessage(m.texteUtile, m.date, androidx.core.app.Person.Builder().setName(if (c.estGroupe) m.auteur else c.nom).build())
        }
        val n = NotificationCompat.Builder(app, canal)
            .setSmallIcon(R.drawable.ic_bulle)
            .setSubText(libelle(c))
            .setStyle(style)
            .setColor(cfg.couleurEffective(c.source).toInt())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // Chiffre sur l'icône (si le lanceur du téléphone l'affiche) : non-lus de la conversation
            .setNumber(_etat.value.conversations.firstOrNull { it.cle == c.cle }?.let { _etat.value.nombreNonLus(it) }?.coerceAtLeast(msgs.size) ?: msgs.size)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setSilent(cfg.enSilence(c.source))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setGroup("messages")
            .build()
        try { NotificationManagerCompat.from(app).notify(c.cle.hashCode(), n) } catch (_: SecurityException) {}
    }

    private fun libelle(c: Conversation) = _etat.value.libelleSource(c)

    private fun notifierSiBesoin(c: Conversation) {
        val e = _etat.value
        val ec = e.etat(c)
        if (!cfg.notifications || cfg.notif(c.source) == "aucun" || ec.sourdine || !e.aTraiter(c) || ec.notifiee == c.derniere) return
        val s = e.synthese(c) ?: return
        majEtat(c.cle) { it.copy(notifiee = c.derniere) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(app, MainActivity::class.java).putExtra("cle", c.cle)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(app, c.cle.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, CANAL)
            .setSmallIcon(R.drawable.ic_bulle)
            .setContentTitle(c.nom)
            .setSubText("${c.source.id} · réponse attendue")
            .setContentText(s.resume)
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.resume))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setNumber(_etat.value.nombreNonLus(c).coerceAtLeast(1))
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setSilent(cfg.enSilence(c.source))
            .build()
        try { NotificationManagerCompat.from(app).notify(c.cle.hashCode(), n) } catch (_: SecurityException) {}
    }
}
