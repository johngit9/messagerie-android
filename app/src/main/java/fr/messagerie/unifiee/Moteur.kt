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
import kotlinx.coroutines.ensureActive
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
    /** Mode vacances : le Pro n'est ni notifié ni compté dans les non-lus (sauf dans la vue Pro elle-même). */
    val vacances: Boolean = false,
    val etats: Map<String, EtatConv> = emptyMap(),
    val syntheses: Map<String, Synthese> = emptyMap(),
    val sources: Map<Source, EtatSource> = Source.entries.associateWith { EtatSource.Inactif },
    val statistiques: Map<Source, Pair<Int, Int>> = emptyMap(),
    val rcs: Set<String> = emptySet(),
    /** Conversation → date jusqu'à laquelle le contact a lu vos messages (si la messagerie le transmet). */
    val lusParContacts: Map<String, Long> = emptyMap(),
    /** Date de la dernière mention de vous (@) par conversation. */
    val mentions: Map<String, Long> = emptyMap(),
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
    /** Score anti-spam des conversations à risque (score ≥ 30), voir [Spam]. */
    val risques: Map<String, Spam.Verdict> = emptyMap(),
) {
    fun estSpam(c: Conversation) = risques[c.cle]?.estSpam == true
    val nombreSpams get() = conversations.count { estSpam(it) }
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
    private fun compte(c: Conversation, pro: Boolean?) = if (vacances && pro != true && estPro(c)) false else true
    fun nombreMessagesNonLus(s: Source?, pro: Boolean? = null) =
        conversations.filter { (s == null || it.source == s) && (pro == null || estPro(it) == pro) && compte(it, pro) && !estArchivee(it) && !estSpam(it) }
            .sumOf { maxOf(nombreNonLus(it), if (estForcee(it)) 1 else 0) }

    val depuis get() = System.currentTimeMillis() - jours * 86_400_000L

    /** « À traiter » : l'IA attend une réponse de vous, vous n'avez pas répondu en dernier, pas déjà traitée. */
    fun aTraiter(c: Conversation): Boolean {
        val e = etat(c)
        if (e.manuel) return true
        if (c.dernierDeMoi || c.derniere < depuis) return false
        // Groupe : « À traiter » seulement si vous y êtes mentionné depuis votre dernier message
        if (c.estGroupe) {
            val mention = mentions[c.cle] ?: return false
            val monDernier = c.messages.lastOrNull { it.deMoi }?.date ?: 0
            return mention > monDernier && mention > e.traitee && e.traitee != c.derniere
        }
        val s = synthese(c) ?: return false
        return s.actionRequise && e.traitee != c.derniere
    }

    val nombreATraiter get() = conversations.count { aTraiter(it) && !estArchivee(it) && !estSpam(it) }
    fun nombreATraiter(s: Source?, pro: Boolean?) =
        conversations.count { (s == null || it.source == s) && (pro == null || estPro(it) == pro) && compte(it, pro) && aTraiter(it) && !estArchivee(it) && !estSpam(it) }
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
        Spam.init(app)
        chargerOrganisation()
        _etat.update { it.copy(jours = cfg.jours, debutLecture = cfg.debutLecture, vacances = cfg.vacances,
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
            // Une fois : les réactions iPhone déjà archivées en texte (« 😂 à "…" ») deviennent des réactions
            if (archive.curseur("tapbacks.v1") == null) runCatching {
                var change = false
                _etat.value.conversations.forEach { c ->
                    val hist = archive.messages(c.cle, null, 1000)
                    if (hist.none { Tapback.lire(it.texte) != null }) return@forEach
                    val (vrais, reactions) = Tapback.separer(hist, emptyList())
                    val convertis = hist.map { it.cle }.toSet() - vrais.map { it.cle }.toSet()
                    if (convertis.isEmpty()) return@forEach
                    archive.supprimerMessages(convertis.toList())
                    archive.enregistrerReactions(reactions)
                    change = true
                }
                archive.definirCurseur("tapbacks.v1", "1")
                if (change) recharger()
            }
        }
        // Non-lus par messagerie dans les raccourcis de l'icône (appui long)
        portee.launch {
            _etat.debounce(1500).collect { e ->
                runCatching { Raccourcis.mettreAJour(app, e) }
                runCatching { Pastille.afficher(app, e.nombreMessagesNonLus(null)) }
                runCatching { nettoyerNotifications(e) }
                runCatching { WidgetNonLus.mettreAJour(app, e) }
            }
        }
    }

    /** Conversations devenues lues (ici, sur le Mac ou dans l'appli officielle) : leurs notifications disparaissent, et le point de l'icône avec. */
    private fun nettoyerNotifications(e: Etat) {
        if (Build.VERSION.SDK_INT < 26) return
        val actives = app.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.notification.channelId?.let { id -> id.startsWith("$CANAL_MESSAGES.") || id.startsWith("msg.") } == true }.map { it.id }.toSet()
        if (actives.isEmpty()) return
        e.conversations.forEach { c -> val id = c.cle.hashCode(); if (id in actives && !e.estNonLue(c)) Bulles.retirerNotification(app, id) }
    }

    /** Démarre l'actualisation périodique et la réception en temps réel (idempotent). */
    fun demarrer() {
        arretDiffere?.cancel()
        if (boucle?.isActive != true) boucle = portee.launch {
            while (isActive) {
                actualiser()
                // En arrière-plan, la réception en temps réel (long-poll) suffit : passage complet au plus tous les 15 min
                delay(if (auPremierPlan || !cfg.economie) cfg.intervalle * 1000L else maxOf(cfg.intervalle, 900) * 1000L)
            }
        }
        if (veilleMatrix?.isActive != true && cfg.matrixConfigure) veilleMatrix = portee.launch { veilleMatrix() }
        if (veilleElement?.isActive != true && cfg.relaisConfigure) veilleElement = portee.launch { veilleElement() }
    }

    private var arretDiffere: Job? = null

    /**
     * Appli quittée : sans « réception en arrière-plan », tout s'arrête après 1 min (la synchro des 15 min
     * de WorkManager prend le relais) ; rien ne tourne en continu et la batterie est épargnée.
     */
    fun auRepos() {
        arretDiffere?.cancel()
        if (cfg.veilleArrierePlan) return
        arretDiffere = portee.launch {
            delay(60_000)
            if (!auPremierPlan && !Pastille.veilleActive) { boucle?.cancel(); veilleMatrix?.cancel(); veilleElement?.cancel() }
        }
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
                val (suivant, nouveau) = Matrix(cfg).attendreNouveautes(jeton, auPremierPlan || !cfg.economie)
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
                    val signal = async { synchroniserPont(Pont.SIGNAL, null, sources[Source.SIGNAL]) }
                    sources[Source.SMS] = sms.await()
                    sources[Source.TELEGRAM] = telegram.await()
                    sources[Source.LINKEDIN] = linkedin.await()
                    sources[Source.SIGNAL] = signal.await()
                    sources[Source.WHATSAPP] = whatsapp.await()
                    _etat.update { it.copy(sources = sources.toMap()) }
                    recharger()
                } else {
                    for (s in listOf(Source.WHATSAPP, Source.SMS, Source.TELEGRAM, Source.LINKEDIN, Source.SIGNAL)) sources[s] = EtatSource.NonConfigure
                }
                sources[Source.ELEMENT] = element.await()
                sources[Source.TEAMS] = teams.await()
            }

            _etat.update { it.copy(sources = sources.toMap()) }
            recharger()
            _etat.update { it.copy(derniereActualisation = System.currentTimeMillis()) }
            // Présence des contacts (lecture seule), au plus toutes les 2 min
            if (auPremierPlan) portee.launch { runCatching { Presences.actualiser(cfg, _etat.value.conversations) { salonDe(it) } } }
            // Lu / traité venant de vos autres applis Messagerie (puis envoi des nôtres)
            runCatching { echangerEtatsPartages(envoyer = true) }
            runCatching { synchroniserVacances() }
            // Fiabilité : messageries déconnectées (alerte + notification), au plus toutes les 5 min
            portee.launch { runCatching { Surveillance.verifier() } }
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
            for ((conv, ts) in s.mentionsMoi) {
                val avant = archive.curseur("mention.$conv")?.toLongOrNull() ?: 0
                if (ts > avant) archive.definirCurseur("mention.$conv", ts.toString())
            }
            for ((conv, ts) in s.lusParContacts) {
                val avant = archive.curseur("luPar.$conv")?.toLongOrNull() ?: 0
                if (ts > avant) archive.definirCurseur("luPar.$conv", ts.toString())
            }
            runCatching { s.completerHistorique() }
            runCatching { s.reparerPiecesJointes() }
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
        val etats = etatsActuels()
        for ((cle, d) in lectures) {
            val e = etats[cle] ?: EtatConv()
            // Seulement plus loin que ce qui est déjà lu, et en gardant le reste de l'état (sourdine, À traiter…)
            if (d > e.lue) majEtat(cle, partager = false) { it.copy(lue = maxOf(it.lue, d)) }
        }
    }

    fun recharger() {
        val e = _etat.value
        val connus = HashSet<String>()
        val convs = archive.conversations(e.depuis, 60).map { c ->
            if (c.source != Source.TEAMS && !c.estGroupe && estUnNumero(c.nom)) numeros[c.cle] = c.nom.trim()
            if (c.source == Source.TEAMS || !estUnNumero(c.nom)) c
            else (nomsCarnet[c.nom] ?: nomContactTelephone(c.nom.filter { it.isDigit() })?.also { nomsCarnet[c.nom] = it })?.let { connus += c.cle; c.copy(nom = it) } ?: c
        }.map { c -> nomPerso(c.cle)?.let { n -> connus += c.cle; c.copy(nom = n) } ?: c }   // nom choisi par vous
        carnetConnus = connus
        val risques = if (!cfg.antiSpam) emptyMap() else convs.mapNotNull { c -> Spam.evaluer(c, c.cle in connus).takeIf { it.score >= 30 }?.let { c.cle to it } }.toMap()
        val stats = archive.statistiques()
        val sources = e.sources.mapValues { (s, st) -> if (st is EtatSource.Ok) EtatSource.Ok(stats[s]?.first ?: 0) else st }
        verifierSpamIA(convs, risques)
        _etat.update { it.copy(conversations = convs, statistiques = stats, sources = sources, risques = risques,
            etats = etatsActuels(), syntheses = archive.syntheses(),
            lusParContacts = archive.curseurs("luPar.").mapNotNull { (k, v) -> v.toLongOrNull()?.let { k to it } }.toMap(),
            mentions = archive.curseurs("mention.").mapNotNull { (k, v) -> v.toLongOrNull()?.let { k to it } }.toMap()) }
    }

    // MARK: Anti-spam (local)

    /** Conversations dont l'expéditeur est dans le carnet d'adresses (nom retrouvé à partir du numéro). */
    @Volatile private var carnetConnus: Set<String> = emptySet()
    private var iaSpamEnCours = false

    fun estSpam(c: Conversation) = cfg.antiSpam && Spam.evaluer(c, c.cle in carnetConnus).estSpam

    /** Cas douteux : avis de l'IA (Ollama de votre serveur), quelques conversations à la fois, puis nouveau calcul. */
    private fun verifierSpamIA(convs: List<Conversation>, risques: Map<String, Spam.Verdict>) {
        if (!cfg.spamIA || !cfg.ollamaConfigure || iaSpamEnCours) return
        val aVoir = convs.filter { c -> risques[c.cle]?.let { Spam.iaAVerifier(c, it) } == true }.take(3)
        if (aVoir.isEmpty()) return
        iaSpamEnCours = true
        portee.launch {
            try { aVoir.forEach { c -> Ollama(cfg).estArnaque(c)?.let { Spam.noterIA(c, it) } } }
            finally { iaSpamEnCours = false }
            recharger()
        }
    }

    /** « Spam » / « Pas spam » (local : rien n'est signalé à la messagerie). */
    fun marquerSpam(c: Conversation, spam: Boolean) {
        Spam.marquer(c, spam)
        if (spam) runCatching { NotificationManagerCompat.from(app).cancel(c.cle.hashCode()) }
        rafraichirSpam()
    }

    /** Recalcule les scores (hors du fil d'affichage). */
    fun rafraichirSpam() { portee.launch { recharger() } }

    // MARK: Transcriptions des vocaux (gardées sur le téléphone)

    private val transcriptions by lazy { java.util.concurrent.ConcurrentHashMap(runCatching { archive.transcriptions() }.getOrDefault(emptyMap())) }
    val versionTranscriptions = MutableStateFlow(0)
    fun transcriptionConnue(m: Message): String? = transcriptions[m.cle]
    fun noterTranscription(m: Message, texte: String) {
        transcriptions[m.cle] = texte
        portee.launch { runCatching { archive.enregistrerTranscription(m.cle, texte) } }
        versionTranscriptions.value++
    }

    /** États modifiés pas encore écrits en base : une actualisation ne doit pas les effacer (sinon « lu » redevient « non lu »). */
    private val etatsEnAttente = java.util.concurrent.ConcurrentHashMap<String, EtatConv>()

    private fun majEtat(cle: String, partager: Boolean = true, f: (EtatConv) -> EtatConv) {
        val avant = _etat.value.etats[cle] ?: EtatConv()
        val n = f(avant)
        if (partager && (n.lue > avant.lue || n.traitee > avant.traitee)) partagerPlusTard()
        etatsEnAttente[cle] = n
        _etat.update { it.copy(etats = it.etats + (cle to n)) }
        // Écriture hors du fil d'affichage : la base peut être occupée par une synchro.
        portee.launch {
            archive.enregistrerEtat(cle, n)
            etatsEnAttente.remove(cle, n)
        }
    }

    // MARK: Lu et traité partagés entre vos applis Messagerie (Mac, Android)
    // Données privées de VOTRE compte Matrix : ni les contacts ni les passerelles ne les voient (aucun accusé de lecture).

    private const val TYPE_PARTAGE = "fr.messagerie.etats"
    private var envoiPartage: Job? = null

    /** Salon Matrix servant de clé commune aux deux applis. */
    private fun clePartagee(c: Conversation): String? = if (c.source == Source.TEAMS) null else salonDe(c)

    private fun partagerPlusTard() {
        if (!cfg.matrixConfigure) return
        envoiPartage?.cancel()
        envoiPartage = portee.launch { delay(8_000); runCatching { echangerEtatsPartages(envoyer = true) } }
    }

    /**
     * Lit les états partagés (lu / traité, par salon) et les applique s'ils sont plus récents ;
     * avec envoyer, y ajoute aussi les vôtres (on garde toujours la date la plus récente).
     */
    private suspend fun echangerEtatsPartages(envoyer: Boolean) = withContext(Dispatchers.IO) {
        if (!cfg.matrixConfigure) return@withContext
        val m = Matrix(cfg)
        val distant = runCatching { m.lireDonneesCompte(TYPE_PARTAGE) }.getOrNull()
        val salons = distant?.optJSONObject("salons") ?: org.json.JSONObject()
        val e = _etat.value
        // 1. Appliquer ce qui vient des autres applis
        for (c in e.conversations) {
            val k = clePartagee(c) ?: continue
            val d = salons.optJSONObject(k) ?: continue
            val lu = d.optLong("lu"); val traite = d.optLong("traite")
            val local = e.etat(c)
            if (lu > local.lue || traite > local.traitee) {
                val etaitNonLue = e.estNonLue(c)
                majEtat(c.cle, partager = false) { it.copy(lue = maxOf(it.lue, lu), traitee = maxOf(it.traitee, traite)) }
                // Lu sur le Mac : aussi dans Google Messages (même option)
                if (etaitNonLue && lu >= finDe(c)) luDansGoogleMessages(c)
            }
        }
        if (!envoyer) return@withContext
        // 2. Ajouter les nôtres (60 derniers jours, pour rester petit)
        val limite = System.currentTimeMillis() - 60 * 86_400_000L
        var change = false
        val e2 = _etat.value
        for (c in e2.conversations) {
            if (c.derniere < limite) continue
            val k = clePartagee(c) ?: continue
            val local = e2.etat(c)
            val d = salons.optJSONObject(k) ?: org.json.JSONObject()
            val lu = maxOf(d.optLong("lu"), local.lue); val traite = maxOf(d.optLong("traite"), local.traitee)
            if (lu != d.optLong("lu") || traite != d.optLong("traite")) {
                salons.put(k, org.json.JSONObject().put("lu", lu).put("traite", traite)); change = true
            }
        }
        // Ménage : entrées anciennes retirées
        salons.keys().asSequence().toList().forEach { k ->
            val d = salons.optJSONObject(k)
            if (d == null || maxOf(d.optLong("lu"), d.optLong("traite")) < limite) { salons.remove(k); change = true }
        }
        if (change) m.ecrireDonneesCompte(TYPE_PARTAGE, org.json.JSONObject().put("v", 1).put("salons", salons))
    }

    /** États de la base, complétés par ceux en cours d'écriture. */
    private fun etatsActuels(): Map<String, EtatConv> = archive.etats() + etatsEnAttente

    // MARK: Lu / non lu — LOCAL uniquement

    fun marquerLue(c: Conversation) {
        Bulles.retirerNotification(app, c.cle.hashCode())   // le chiffre de l'icône baisse (une bulle ouverte reste)
        if (!_etat.value.estNonLue(c)) return
        majEtat(c.cle) { it.copy(lue = maxOf(it.lue, finDe(c)), forcee = 0) }
        luDansElement(c)
        luDansGoogleMessages(c)
    }

    /**
     * Lu reporté dans l'appli officielle :
     * - WhatsApp, toujours : la passerelle du serveur le transmet en « lu par moi » (read-self), jamais visible des contacts ;
     * - SMS, sur option (Réglages > Confidentialité) : Google Messages.
     * JAMAIS pour Telegram, Signal ni LinkedIn (l'accusé y serait visible).
     */
    private fun luDansGoogleMessages(c: Conversation) {
        val permis = when (c.source) { Source.WHATSAPP -> true; Source.SMS -> cfg.lusSmsGoogle; else -> false }
        if (!permis || !cfg.matrixConfigure) return
        val salon = salonDe(c) ?: return
        val eid = c.messages.lastOrNull { it.cle.substringAfter(':').startsWith("$") }?.cle?.substringAfter(':') ?: return
        portee.launch(Dispatchers.IO) { runCatching { Matrix(cfg).marquerLuPasserelle(salon, eid) } }
    }

    /** Date du dernier message (parfois plus récente que la date enregistrée de la conversation). */
    private fun finDe(c: Conversation) = maxOf(c.derniere, c.messages.maxOfOrNull { it.date } ?: 0)

    /** Element : le message lu dans Messagerie apparaît lu dans Element (accusé PRIVÉ, invisible des contacts). */
    private fun luDansElement(c: Conversation) {
        if (c.source != Source.ELEMENT || !cfg.relaisConfigure) return
        val eid = c.messages.lastOrNull()?.cle?.substringAfter(':')?.takeIf { it.startsWith("$") } ?: return
        portee.launch(Dispatchers.IO) { runCatching { Relais(cfg).luPrive(c.jid, eid) } }
    }
    fun marquerNonLue(c: Conversation) = majEtat(c.cle) { it.copy(forcee = c.derniere) }
    fun toutMarquerLu() = _etat.value.let { e ->
        e.conversations.filter { e.estNonLue(it) }.forEach { c -> majEtat(c.cle) { it.copy(lue = maxOf(it.lue, finDe(c)), forcee = 0) }; luDansElement(c); luDansGoogleMessages(c) }
        // Notifications des conversations retirées une à une : celles en bulle restent
        e.conversations.forEach { Bulles.retirerNotification(app, it.cle.hashCode()) }
    }
    fun marquerTraitee(c: Conversation) = majEtat(c.cle) { it.copy(traitee = c.derniere, manuel = false) }
    /** Met (ou retire) plusieurs conversations dans « À traiter » d'un coup. */
    /** Statut choisi par vous : réponse attendue, pas de réponse (jusqu'au prochain message), ou décision automatique (IA / mention). */
    enum class Statut { ATTENDUE, AUCUNE, AUTO }
    fun statutChoisi(c: Conversation): Statut {
        val e = _etat.value.etat(c)
        return when { e.manuel -> Statut.ATTENDUE; e.traitee != 0L && e.traitee >= c.derniere -> Statut.AUCUNE; else -> Statut.AUTO }
    }
    fun choisirStatut(c: Conversation, st: Statut) = majEtat(c.cle) {
        when (st) {
            Statut.ATTENDUE -> it.copy(manuel = true, traitee = 0)
            Statut.AUCUNE -> it.copy(manuel = false, traitee = c.derniere)
            Statut.AUTO -> it.copy(manuel = false, traitee = 0)
        }
    }

    /** Met dans « À traiter », ou l'en retire (y compris si c'est l'IA ou une mention qui l'y avait mise). */
    fun mettreATraiter(c: Conversation, oui: Boolean) = majEtat(c.cle) { if (oui) it.copy(manuel = true, traitee = 0) else it.copy(manuel = false, traitee = c.derniere) }
    fun basculerATraiter(c: Conversation) = mettreATraiter(c, !_etat.value.aTraiter(c))
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

    /** Quelques réponses possibles proposées par l'IA (insérées dans le champ, jamais envoyées seules). */
    suspend fun brouillonsIA(c: Conversation): List<String>? = withContext(Dispatchers.IO) {
        if (!cfg.ollamaConfigure) null else Ollama(cfg).brouillons(c, archive.messages(c.cle, null, 30))
    }

    // MARK: Envoi — uniquement sur le bouton « Envoyer »

    /** Renvoie null si envoyé, sinon le message d'erreur. */
    suspend fun envoyer(texte: String, c: Conversation, cite: Message?, mentions: Map<String, String> = emptyMap()): String? = withContext(Dispatchers.IO) {
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
                    Matrix(cfg).envoyer(salon, t, idCite, mentions)
                }
                else -> Matrix(cfg).envoyer(c.jid, t, idCite, mentions)
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

    /** Peut-on envoyer une image dans cette conversation ? (passerelles Matrix seulement) */
    fun imagePossible(c: Conversation) = vocalPossible(c)

    /** Envoie une image collée — UNIQUEMENT sur le bouton « Envoyer » de l'aperçu. */
    suspend fun envoyerImage(octets: ByteArray, typeMime: String, c: Conversation): String? = withContext(Dispatchers.IO) {
        try {
            if (!imagePossible(c)) return@withContext "envoi d'images impossible pour ${c.source.libelle}"
            val salon = salonPourEnvoi(c) ?: return@withContext "discussion introuvable sur le serveur"
            val ext = when (typeMime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
            val nom = "image-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.FRANCE).format(java.util.Date()) + "." + ext
            val bornes = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
            val m = Matrix(cfg)
            val mxc = m.televerser(octets, typeMime, nom)
            val info = org.json.JSONObject().put("mimetype", typeMime).put("size", octets.size)
            if (bornes.outWidth > 0) info.put("w", bornes.outWidth).put("h", bornes.outHeight)
            val eid = m.envoyerContenu(salon, org.json.JSONObject().put("msgtype", "m.image").put("body", nom).put("url", mxc).put("info", info))
            afficherEnvoye(c, eid, "[photo]", type = "image", media = "$mxc#$typeMime#$nom")
            null
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    /**
     * Envoie un fichier (vidéo, document, fiche contact .vcf…) — UNIQUEMENT sur le bouton « Envoyer » de l'aperçu.
     * Les images passent par [envoyerImage].
     */
    suspend fun envoyerFichier(octets: ByteArray, typeMime: String, nom: String, c: Conversation): String? = withContext(Dispatchers.IO) {
        try {
            if (!imagePossible(c)) return@withContext "envoi de fichiers impossible pour ${c.source.libelle}"
            val salon = salonPourEnvoi(c) ?: return@withContext "discussion introuvable sur le serveur"
            val m = Matrix(cfg)
            val mxc = m.televerser(octets, typeMime, nom)
            val (msgtype, type, apercu) = when {
                typeMime.startsWith("video/") -> Triple("m.video", "video", "[vidéo]")
                typeMime.startsWith("audio/") -> Triple("m.audio", "fichier", nom)
                else -> Triple("m.file", "fichier", nom)
            }
            val info = org.json.JSONObject().put("mimetype", typeMime).put("size", octets.size)
            val eid = m.envoyerContenu(salon, org.json.JSONObject().put("msgtype", msgtype).put("body", nom).put("filename", nom).put("url", mxc).put("info", info))
            afficherEnvoye(c, eid, apercu, type = type, media = "$mxc#$typeMime#$nom")
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
        cfg.envoyerReactions && c.source != Source.TEAMS && m.cle.substringAfter(':').startsWith("$") &&
            (if (c.source == Source.ELEMENT) cfg.relaisConfigure else salonDe(c) != null)

    private fun salonDe(c: Conversation): String? =
        if (c.source == Source.WHATSAPP) _etat.value.salonsWhatsApp[c.jid] else c.jid

    /**
     * Récupère les réactions déjà posées dans une conversation (contacts et vous, depuis les applis officielles).
     * Lecture seule : quelques pages des événements m.reaction du salon. Renvoie true si quelque chose a changé.
     */
    suspend fun recupererReactions(c: Conversation): Boolean = withContext(Dispatchers.IO) {
        if (!cfg.matrixConfigure || c.source == Source.ELEMENT || c.source == Source.TEAMS) return@withContext false
        val salon = salonDe(c) ?: return@withContext false
        runCatching {
            val m = Matrix(cfg); m.connecter()
            val noms = runCatching { m.membres(salon) }.getOrNull().orEmpty()
            val moi = setOfNotNull(cfg.matrixUtilisateur, cfg.moiFantome.ifEmpty { null }, SyncPont.moiDetecte(Pont.pour(c.source)))
            val filtre = Http.enc("""{"types":["m.reaction"]}""")
            val lot = ArrayList<Reaction>(); val retirees = ArrayList<String>()
            var depuis: String? = null
            for (page in 0 until 4) {
                var chemin = "/_matrix/client/v3/rooms/${Http.enc(salon)}/messages?dir=b&limit=500&filter=$filtre"
                if (depuis != null) chemin += "&from=${Http.enc(depuis)}"
                val rep = m.get(chemin, 20_000)
                val evs = rep.optJSONArray("chunk")?.objets() ?: break
                for (e in evs) {
                    val eid = e.optString("event_id").takeIf { it.isNotEmpty() } ?: continue
                    val rel = e.optJSONObject("content")?.optJSONObject("m.relates_to")
                    // Réaction retirée (événement effacé) : on l'oublie
                    if (rel == null) { retirees += "${c.source.id}:$eid"; continue }
                    val cible = rel.optString("event_id"); val cle = rel.optString("key")
                    if (rel.optString("rel_type") != "m.annotation" || cible.isEmpty() || cle.isEmpty()) continue
                    val exp = e.optString("sender")
                    val deMoi = exp in moi
                    lot += Reaction("${c.source.id}:$eid", "${c.source.id}:$cible", c.cle, cle,
                        if (deMoi) "Vous" else noms[exp]?.replace(" (WA)", "")?.trim() ?: "Quelqu'un", deMoi)
                }
                depuis = rep.optString("end").takeIf { it.isNotEmpty() && evs.isNotEmpty() } ?: break
            }
            val avant = archive.reactions(c.cle)
            archive.enregistrerReactions(lot)
            retirees.forEach { archive.oublierReactionCle(it) }
            archive.reactions(c.cle) != avant
        }.getOrDefault(false)
    }

    /** Pose (emoji) ou retire (null) votre réaction. Renvoie null si tout va bien, sinon le message d'erreur. */
    suspend fun reagir(m: Message, c: Conversation, emoji: String?): String? = withContext(Dispatchers.IO) {
        try {
            val avant = archive.maReaction(m.cle)
            if (avant?.idEnvoi != null && avant.salon != null) {
                if (c.source == Source.ELEMENT) Relais(cfg).retirerReaction(avant.salon, avant.idEnvoi)
                else Matrix(cfg).retirerReaction(avant.salon, avant.idEnvoi)
                archive.oublierReaction(avant.idEnvoi)
            }
            if (emoji == null) { archive.definirMaReaction(m.cle, null); return@withContext null }
            var salon: String? = null; var id: String? = null
            if (reactionTransmissible(m, c)) {
                salon = salonDe(c)
                id = (if (c.source == Source.ELEMENT) Relais(cfg).reagir(salon!!, m.cle.substringAfter(':'), emoji)
                    else Matrix(cfg).reagir(salon!!, m.cle.substringAfter(':'), emoji)).takeIf { it.isNotEmpty() }
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

    /**
     * Aperçu d'une pièce jointe : vignette fournie par la messagerie, sinon première page
     * d'un PDF déjà sur le téléphone. null si aucun aperçu possible (on affiche alors le type).
     */
    suspend fun apercuFichier(m: Message): java.io.File? = withContext(Dispatchers.IO) {
        val parts = m.media?.split('#') ?: return@withContext null
        val dossier = java.io.File(app.cacheDir, "medias").apply { mkdirs() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(parts[0].toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        val f = java.io.File(dossier, "$hash-apercu.jpg")
        if (f.exists() && f.length() > 0) return@withContext f
        parts.getOrNull(3)?.takeIf { it.startsWith("mxc://") }?.let { v ->
            runCatching { Matrix(cfg).miniature(v, 320) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { f.writeBytes(it); return@withContext f }
        }
        val mime = parts.getOrNull(1).orEmpty()
        val nom = parts.getOrNull(2).orEmpty()
        if (mime != "application/pdf" && !nom.endsWith(".pdf", true)) return@withContext null
        val pdf = mediaEnCache(m) ?: return@withContext null
        runCatching {
            android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { r ->
                    r.openPage(0).use { page ->
                        val l = 480; val h = (l.toFloat() * page.height / page.width).toInt().coerceIn(120, 900)
                        val bmp = android.graphics.Bitmap.createBitmap(l, h, android.graphics.Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }
                    }
                }
            }
            f
        }.getOrNull()
    }

    // MARK: Groupes — lecture des membres ; quitter seulement après confirmation

    /** Membre d'un groupe : nom (si connu) et numéro (si la messagerie en donne un). */
    data class Membre(val nom: String?, val numero: String?, val moi: Boolean = false, val id: String = "") {
        /** Texte affiché après @ dans une mention. */
        val libelle: String get() = nom ?: numero?.let { "+$it" } ?: id
    }

    /**
     * Membres d'un groupe, hors robots des passerelles. Le nom vient, dans l'ordre : des contacts
     * du téléphone (si autorisé, lecture locale), d'une discussion à deux déjà connue, puis de la passerelle.
     * null si la messagerie ne le permet pas.
     */
    suspend fun membresGroupe(c: Conversation): List<Membre>? = withContext(Dispatchers.IO) {
        if (!cfg.matrixConfigure || c.source == Source.ELEMENT || c.source == Source.TEAMS) return@withContext null
        val salon = salonDe(c) ?: return@withContext null
        val brut = runCatching { Matrix(cfg).membres(salon) }.getOrNull() ?: return@withContext null
        val convs = _etat.value.conversations.filter { it.source == c.source && !it.estGroupe }
        val parNumero = HashMap<String, String>()
        convs.forEach { cv ->
            val n = cv.jid.substringBefore('@').substringBefore(':').filter { it.isDigit() }
            if (n.length >= 6 && !estUnNumero(cv.nom)) parNumero[n] = cv.nom.replace(" (WA)", "")
        }
        brut.filterKeys { !it.contains("bot:") }.map { (id, affiche) ->
            val moi = id == cfg.matrixUtilisateur || id == cfg.moiFantome
            val propre = affiche.replace(" (WA)", "").trim()
            val numero = Regex("""_\+?(\d{6,})[:_]""").find(id)?.groupValues?.get(1)
                ?: propre.takeIf { estUnNumero(it) }?.filter { it.isDigit() }
            val nom = when {
                moi -> "Vous"
                else -> numero?.let { nomContactTelephone(it) } ?: numero?.let { parNumero[it] } ?: propre.takeIf { !estUnNumero(it) && !it.startsWith("@") }
            }
            Membre(nom, numero, moi, id)
        }.distinctBy { it.numero ?: it.nom }
            .sortedWith(compareBy<Membre> { !it.moi }.thenBy { it.nom == null }.thenBy { (it.nom ?: it.numero ?: "").lowercase() })
    }

    // MARK: Fiche contact unifiée (même personne sur plusieurs messageries ; tout reste sur le téléphone)

    private fun sansAccents(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Repères d'une personne : nom simplifié (mots triés) et 9 derniers chiffres du numéro ; ou le lien choisi à la main. */
    private fun reperes(c: Conversation, liens: Map<String, String>): Set<String> {
        liens[c.cle]?.let { return setOf(it) }
        val r = HashSet<String>()
        val nom = c.nom.trim()
        if (estUnNumero(nom)) r += "tel:" + nom.filter { it.isDigit() }.takeLast(9)
        else {
            val mots = sansAccents(nom).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.sorted()
            val cle = mots.joinToString(" ")
            if (cle.length >= 4 && (mots.size >= 2 || cle.length >= 5)) r += "nom:$cle"
        }
        val chiffres = c.jid.substringBefore('@')
        if (c.source == Source.WHATSAPP && chiffres.length >= 8 && chiffres.all { it.isDigit() }) r += "tel:" + chiffres.takeLast(9)
        return r
    }

    /** Autres conversations à deux avec la même personne, sur d'autres messageries ou comptes (les plus récentes d'abord). */
    fun autresComptes(c: Conversation, e: Etat = _etat.value): List<Conversation> {
        if (c.estGroupe) return emptyList()
        val liens = cfg.liensContacts
        val miens = reperes(c, liens)
        if (miens.isEmpty()) return emptyList()
        return e.conversations.filter { it.cle != c.cle && !it.estGroupe && reperes(it, liens).any { r -> r in miens } }
            .sortedByDescending { it.derniere }
    }

    /** Relie deux conversations à la même personne (choix manuel). */
    fun lierContacts(a: Conversation, b: Conversation) {
        val liens = cfg.liensContacts.toMutableMap()
        val personne = liens[b.cle] ?: liens[a.cle] ?: ("lien:" + b.cle)
        // Les comptes déjà rapprochés automatiquement suivent
        (listOf(a, b) + autresComptes(a) + autresComptes(b)).forEach { liens[it.cle] = personne }
        cfg.liensContacts = liens
    }

    /** Sépare une conversation de la fiche (ce n'est pas la même personne). */
    fun separerContact(c: Conversation) {
        cfg.liensContacts = cfg.liensContacts + (c.cle to "seul:" + c.cle)
    }

    /** Numéro → nom du carnet d'adresses du téléphone ("" = inconnu), pour ne pas relire les contacts à chaque fois. */
    private val nomsCarnet = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Numéro d'origine des conversations nommées par un numéro (avant le nom du carnet ou le vôtre). */
    private val numeros = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Numéro de téléphone d'un contact, s'il est connu : numéro de la conversation (SMS), identifiant WhatsApp,
     * même personne sur une autre messagerie, ou fiche du carnet portant ce nom. Lecture locale uniquement.
     */
    fun numeroTelephone(c: Conversation): String? {
        if (c.estGroupe) return null
        fun direct(x: Conversation): String? = numeros[x.cle]
            ?: x.nom.trim().takeIf { estUnNumero(it) }
            ?: x.jid.substringBefore('@').substringBefore(':').takeIf { x.source == Source.WHATSAPP && it.length >= 8 && it.all(Char::isDigit) }?.let { "+$it" }
        direct(c)?.let { return it }
        runCatching { autresComptes(c) }.getOrNull()?.firstNotNullOfOrNull { direct(it) }?.let { return it }
        if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            app.contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${android.provider.ContactsContract.Contacts.DISPLAY_NAME} = ?", arrayOf(c.nom.trim()), null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        }.getOrNull()
    }

    private fun estUnNumero(t: String) = t.count { it.isDigit() } >= 6 && t.none { it.isLetter() }

    /** Nom d'un numéro dans les contacts du téléphone (lecture locale, rien n'est envoyé). */
    private fun nomContactTelephone(numero: String): String? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode("+$numero"))
            app.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Suit qui écrit dans la conversation tant que l'écran est ouvert (réception seulement ;
     * l'appli n'envoie jamais son propre indicateur). Appelle maj avec les noms (liste vide = personne).
     */
    suspend fun suivreSaisie(c: Conversation, maj: (List<String>) -> Unit) = withContext(Dispatchers.IO) {
        // Element : on demande au relais toutes les 3 s (il s'arrête si le relais ne connaît pas cette route)
        if (c.source == Source.ELEMENT) {
            if (!cfg.relaisConfigure) return@withContext
            val r = Relais(cfg)
            var avant: List<String>? = null
            var echecs = 0
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val l = runCatching { r.saisie(c.jid) }.getOrNull()
                if (l == null) { if (++echecs >= 3) { maj(emptyList()); return@withContext } }
                else { echecs = 0; if (l != avant) { maj(l); avant = l } }
                delay(3_000)
            }
        }
        if (!cfg.matrixConfigure || c.source == Source.TEAMS) return@withContext
        val salon = salonDe(c) ?: return@withContext
        val m = Matrix(cfg)
        val noms = runCatching { m.membres(salon) }.getOrDefault(emptyMap())
        val moi = setOf(cfg.matrixUtilisateur, cfg.moiFantome)
        var jeton: String? = null
        var echecs = 0
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            try {
                val (suivant, ids) = m.attendreSaisie(salon, jeton)
                if (jeton != null && ids != null) {
                    maj(ids.filter { it !in moi && !it.contains("bot:") }
                        .map { id -> (noms[id] ?: id.substringAfter('@').substringBefore(':')).replace(" (WA)", "") })
                }
                jeton = suivant; echecs = 0
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) { if (++echecs > 5) return@withContext; delay(5_000L * echecs) }
        }
    }

    /**
     * Applique votre statut de présence (choisi dans les Réglages) au serveur Matrix et au relais Element.
     * Renvoie la liste des endroits où il a été appliqué et ceux en échec.
     */
    suspend fun definirPresence(etat: String, matrix: Boolean = true, element: Boolean = true): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val ok = ArrayList<String>(); val ko = ArrayList<String>()
        if (matrix) {
            cfg.presence = etat
            if (cfg.matrixConfigure) runCatching { Matrix(cfg).definirPresence(etat) }
                .onSuccess { ok += "WhatsApp et Telegram" }.onFailure { ko += "serveur Matrix" }
        }
        if (element) {
            cfg.presenceElement = etat
            if (cfg.relaisConfigure) runCatching { Relais(cfg).presence(etat) }
                .onSuccess { ok += "Element" }.onFailure { ko += "Element" }
        }
        _statut.value = cfg.presence to cfg.presenceElement
        ok to ko
    }

    /** Statuts montrés aux autres : (compte Matrix = WhatsApp, Telegram ; Element). */
    private val _statut by lazy { MutableStateFlow(cfg.presence to cfg.presenceElement) }
    val statut: StateFlow<Pair<String, String>> get() = _statut

    /** Supprime des messages DANS MESSAGERIE seulement (rien n'est supprimé chez les contacts ni sur les messageries). */
    fun supprimerMessages(cles: List<String>) {
        portee.launch(Dispatchers.IO) { runCatching { archive.effacerMessages(cles); recharger() } }
    }

    /** Peut-on supprimer ce message pour tout le monde ? (le vôtre, déjà sur le serveur, hors Teams) */
    fun supprimablePourTous(m: Message, c: Conversation) =
        m.deMoi && c.source != Source.TEAMS && c.source != Source.SMS && m.cle.substringAfter(':').startsWith("$") &&
            (if (c.source == Source.ELEMENT) cfg.relaisConfigure else cfg.matrixConfigure)

    /**
     * Supprime VOS messages pour tout le monde (WhatsApp, Telegram… via la passerelle ; Element via le relais),
     * puis de Messagerie. UNIQUEMENT après confirmation. Renvoie (supprimés partout, erreur éventuelle).
     */
    suspend fun supprimerPourTous(messages: List<Message>, c: Conversation): Pair<Int, String?> = withContext(Dispatchers.IO) {
        var n = 0; var err: String? = null
        val salon = if (c.source == Source.ELEMENT) c.jid else salonPourEnvoi(c)
        val faits = ArrayList<String>()
        for (m in messages.filter { supprimablePourTous(it, c) }) {
            val eid = m.cle.substringAfter(':')
            try {
                if (salon == null) throw Exception("discussion introuvable sur le serveur")
                if (c.source == Source.ELEMENT) Relais(cfg).supprimer(salon, eid) else Matrix(cfg).supprimerPourTous(salon, eid)
                faits += m.cle; n++
            } catch (e: Exception) {
                err = if (e is ErreurHttp && e.code == 404 && c.source == Source.ELEMENT) "le relais Element ne sait pas encore supprimer" else (e.message ?: e.toString())
            }
        }
        runCatching { archive.effacerMessages(faits); recharger() }
        n to err
    }

    /** Supprime une conversation DANS MESSAGERIE seulement ; elle revient si un nouveau message arrive. */
    fun supprimerConversation(c: Conversation) {
        val jusqua = maxOf(c.derniere, System.currentTimeMillis())
        portee.launch(Dispatchers.IO) { runCatching { archive.effacerConversation(c.cle, jusqua); recharger() } }
    }

    fun peutQuitter(c: Conversation) = c.estGroupe && cfg.matrixConfigure && c.source != Source.ELEMENT && c.source != Source.TEAMS

    /** Quitte le groupe — UNIQUEMENT après la confirmation de l'utilisateur. Renvoie null si c'est fait. */
    suspend fun quitterGroupe(c: Conversation): String? = withContext(Dispatchers.IO) {
        try {
            if (!peutQuitter(c)) return@withContext "impossible de quitter ce groupe depuis Messagerie"
            val salon = salonPourEnvoi(c) ?: return@withContext "groupe introuvable sur le serveur"
            Matrix(cfg).quitter(salon)
            if (!_etat.value.estArchivee(c)) withContext(Dispatchers.Main) { basculerArchive(c) }
            null
        } catch (e: Exception) { e.message ?: e.toString() }
    }

    // MARK: Transfert — uniquement après confirmation de l'utilisateur

    /** Peut-on transférer vers cette conversation ? */
    fun destinationPossible(c: Conversation) = c.source != Source.TEAMS

    /**
     * Transfère les messages choisis vers une conversation (texte renvoyé, pièce jointe reprise du serveur).
     * Renvoie (nombre transférés, erreur éventuelle).
     */
    suspend fun transferer(messages: List<Message>, vers: Conversation): Pair<Int, String?> = withContext(Dispatchers.IO) {
        var faits = 0
        var erreur: String? = null
        for (m in messages.sortedBy { it.date }) {
            val ref = m.media?.split('#')
            val mxc = ref?.getOrNull(0)?.takeIf { it.startsWith("mxc://") }
            if (m.type in setOf("image", "video", "fichier", "audio") && mxc != null) {
                val salon = if (vers.source == Source.ELEMENT) null else salonPourEnvoi(vers)
                if (salon == null) { erreur = "pièce jointe non transférable vers ${vers.source.libelle}"; continue }
                val mime = ref.getOrNull(1) ?: "application/octet-stream"
                val nom = ref.getOrNull(2)?.ifBlank { null } ?: when (m.type) { "image" -> "photo"; "video" -> "video"; "audio" -> "vocal"; else -> "fichier" }
                val info = org.json.JSONObject().put("mimetype", mime)
                m.duree?.let { info.put("duration", (it * 1000).toLong()) }
                val contenu = org.json.JSONObject()
                    .put("msgtype", when (m.type) { "image" -> "m.image"; "video" -> "m.video"; "audio" -> "m.audio"; else -> "m.file" })
                    .put("body", nom).put("url", mxc).put("info", info)
                try {
                    val eid = Matrix(cfg).envoyerContenu(salon, contenu)
                    afficherEnvoye(vers, eid, m.texte, m.type, m.media, m.duree)
                    faits++
                } catch (e: Exception) { erreur = e.message ?: e.toString() }
            } else if (m.texte.isNotBlank() && m.type != "audio") {
                val err = envoyer(m.texte, vers, null)
                if (err == null) faits++ else erreur = err
            } else erreur = "certains messages ne peuvent pas être transférés"
        }
        faits to erreur
    }

    // MARK: État des passerelles (lecture seule)

    /** État d'une passerelle : ok (true / false / null si inconnu), dernier message du robot et sa date. */
    data class EtatPasserelle(val ok: Boolean?, val message: String?, val date: Long?)

    /**
     * Lit le dernier message du robot de la passerelle (salon de gestion, trouvé par m.direct).
     * Rien n'est envoyé au robot. null si la messagerie ne passe pas par une passerelle Matrix.
     */
    suspend fun etatPasserelle(s: Source): EtatPasserelle? = withContext(Dispatchers.IO) {
        if (!cfg.matrixConfigure || s == Source.ELEMENT || s == Source.TEAMS) return@withContext null
        val nom = Pont.pour(s).prefixe.trim('@', '_')
        runCatching {
            val m = Matrix(cfg); m.connecter()
            val direct = m.get("/_matrix/client/v3/user/${Http.enc(cfg.matrixUtilisateur)}/account_data/m.direct")
            val robot = direct.keys().asSequence().firstOrNull { k ->
                val local = k.substringAfter('@').substringBefore(':').lowercase()
                local.startsWith(nom) && local.endsWith("bot")
            } ?: return@runCatching EtatPasserelle(null, null, null)
            val salons = direct.optJSONArray(robot)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            var dernier: Pair<String, Long>? = null
            for (salon in salons.take(3)) {
                val r = runCatching { m.get("/_matrix/client/v3/rooms/${Http.enc(salon)}/messages?dir=b&limit=30") }.getOrNull() ?: continue
                r.optJSONArray("chunk")?.objets()?.firstOrNull { it.optString("sender") == robot && it.optString("type") == "m.room.message" }?.let { ev ->
                    val ts = ev.ts("origin_server_ts") ?: 0
                    val corps = ev.optJSONObject("content")?.optString("body").orEmpty()
                    if (dernier == null || ts > dernier!!.second) dernier = corps to ts
                }
            }
            val (texte, date) = dernier ?: return@runCatching EtatPasserelle(null, null, null)
            val t = texte.lowercase()
            val probleme = listOf("logged out", "not logged in", "disconnect", "déconnect", "error", "erreur", "failed", "échec",
                "expired", "expiré", "re-login", "relogin", "log in again", "bad credential", "banned", "unauthorized", "not connected")
            val bien = listOf("connected", "connecté", "logged in", "successfully", "reconnected", "pong")
            val ok = when {
                probleme.any { t.contains(it) } && !t.contains("reconnected") -> false
                bien.any { t.contains(it) } -> true
                else -> null
            }
            EtatPasserelle(ok, texte.lines().firstOrNull { it.isNotBlank() }?.take(200), date)
        }.getOrNull()
    }

    private val photos = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()

    // MARK: Contacts modifiés (nom et photo choisis par vous, gardés sur ce téléphone seulement)
    private val prefsContacts by lazy { app.getSharedPreferences("contacts", android.content.Context.MODE_PRIVATE) }
    private val _versionPhotos = MutableStateFlow(0)
    /** Change à chaque photo modifiée : les avatars se rechargent. */
    val versionPhotos: StateFlow<Int> get() = _versionPhotos

    fun nomPerso(cle: String): String? = prefsContacts.getString("nom.$cle", null)?.takeIf { it.isNotBlank() }

    private fun fichierPhotoPerso(cle: String): java.io.File {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(cle.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        return java.io.File(java.io.File(app.filesDir, "photos-perso"), "$hash.jpg")
    }

    fun photoPerso(cle: String): java.io.File? = fichierPhotoPerso(cle).takeIf { it.exists() && it.length() > 0 }

    /**
     * Modifie le nom et/ou la photo d'un contact DANS MESSAGERIE (rien n'est changé sur les messageries ni chez le contact).
     * nom vide = nom d'origine ; photo = image choisie dans la galerie ; retirerPhoto = photo d'origine.
     */
    suspend fun modifierContact(cle: String, nom: String?, photo: android.net.Uri?, retirerPhoto: Boolean) = withContext(Dispatchers.IO) {
        prefsContacts.edit().apply { if (nom.isNullOrBlank()) remove("nom.$cle") else putString("nom.$cle", nom.trim()) }.apply()
        val f = fichierPhotoPerso(cle)
        if (retirerPhoto) f.delete()
        if (photo != null) runCatching {
            val source = app.contentResolver.openInputStream(photo)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return@runCatching
            // Carré central, 512 px au plus
            val cote = minOf(source.width, source.height)
            val carre = android.graphics.Bitmap.createBitmap(source, (source.width - cote) / 2, (source.height - cote) / 2, cote, cote)
            val finale = if (cote > 512) android.graphics.Bitmap.createScaledBitmap(carre, 512, 512, true) else carre
            f.parentFile?.mkdirs()
            f.outputStream().use { finale.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        }
        photos.remove(cle)
        _versionPhotos.value++
        recharger()
    }

    /** Photo de contact déjà téléchargée (sans réseau), pour les notifications et les bulles. */
    fun photoEnCache(conv: String): java.io.File? {
        photoPerso(conv)?.let { return it }
        photos[conv]?.let { return it }
        val mxc = archive.avatar(conv) ?: return null
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(mxc.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        return java.io.File(java.io.File(app.cacheDir, "avatars"), "$hash.img").takeIf { it.exists() && it.length() > 0 }
    }

    /** Photo du contact dans le carnet d'adresses du téléphone (numéro tiré de la conversation), lecture locale. */
    fun photoCarnet(c: Conversation): android.graphics.Bitmap? {
        if (c.estGroupe || androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val numero = c.jid.substringBefore('@').filter { it.isDigit() }.takeIf { it.length >= 6 }
            ?: nomsCarnet.entries.firstOrNull { it.value == c.nom }?.key?.filter { it.isDigit() }?.takeIf { it.length >= 6 }
            ?: c.nom.filter { it.isDigit() }.takeIf { it.length >= 6 && c.nom.none { ch -> ch.isLetter() } }
            ?: return null
        return runCatching {
            val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode("+$numero"))
            val photo = app.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0) else null
            } ?: return null
            app.contentResolver.openInputStream(android.net.Uri.parse(photo))?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    /** Photo de contact d'une conversation (miniature en cache), ou null s'il n'y en a pas. */
    suspend fun photoContact(conv: String): java.io.File? = withContext(Dispatchers.IO) {
        photoPerso(conv)?.let { return@withContext it }
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
    // « msg2 » : les canaux « msg » d'avant refusaient les bulles (réglage figé à la création du canal)
    const val CANAL_MESSAGES = "msg2"

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
        // Vos messages envoyés depuis une autre appli (téléphone, ordinateur) : la conversation est lue jusque-là, ici aussi
        e.conversations.forEach { c ->
            val monDernier = c.messages.lastOrNull { it.deMoi }?.date ?: return@forEach
            if (monDernier > e.etat(c).lue) majEtat(c.cle, partager = false) { it.copy(lue = maxOf(it.lue, monDernier)) }
        }
        if (depuis == null) return   // premier passage : rien à signaler
        // Pas de « nouveau message » pour ce qui précède votre propre réponse (déjà lu ailleurs)
        val nouveaux = recents.filter { (c, m) -> m.cle !in vus && (m.date > depuis || vus.isNotEmpty()) && m.date > e.luJusqua(c) }
        if (nouveaux.isEmpty()) return
        // Délai : seulement pour des messages arrivés « en direct » (moins de 10 min).
        nouveaux.groupBy { it.first.source }.forEach { (s, l) ->
            val enDirect = l.filter { maintenant - it.second.date in 0..3_600_000 }
            ajouter("delais.${s.id}", enDirect.map { maintenant - it.second.date })
            val decoupes = enDirect.mapNotNull { (_, m) -> ArriveesServeur.prendre(m.cle)?.let { srv -> (srv - m.date) to (maintenant - srv) } }
            ajouter("delais.pont.${s.id}", decoupes.map { it.first.coerceAtLeast(0) })
            ajouter("delais.tel.${s.id}", decoupes.map { it.second.coerceAtLeast(0) })
        }
        // Appel entrant (moins d'une minute) : on ouvre l'appli officielle pour décrocher
        nouveaux.filter { it.second.type == "appel" && !it.second.texte.startsWith("📵") && maintenant - it.second.date in 0..60_000 }
            .distinctBy { it.first.cle }.forEach { (c, _) ->
                val e2 = _etat.value
                if (cfg.appelsOfficiels && !(cfg.vacances && e2.estPro(c)) && !estSpam(c) && !e2.etat(c).sourdine) ouvrirPourAppel(c)
            }
        if (auPremierPlan) return
        nouveaux.filter { maintenant - it.second.date < 3_600_000 }.groupBy { it.first }.forEach { (c, l) ->
            if (cfg.notif(c.source) != "tous" || e.etat(c).sourdine || c.cle == convOuverte) return@forEach
            if (cfg.vacances && e.estPro(c)) return@forEach   // mode vacances : rien pour le Pro
            if (estSpam(c)) return@forEach                     // spam : jamais de notification
            notifierMessages(c, l.map { it.second })
        }
    }

    /** Paquet Android de l'appli officielle d'une messagerie (null : SMS, gérés par le téléphone). */
    fun appliOfficielle(s: Source): String? = when (s) {
        Source.WHATSAPP -> "com.whatsapp"; Source.TELEGRAM -> "org.telegram.messenger"; Source.SIGNAL -> "org.thoughtcrime.securesms"
        Source.LINKEDIN -> "com.linkedin.android"; Source.ELEMENT -> "im.vector.app"; Source.TEAMS -> "com.microsoft.teams"; Source.SMS -> null
    }

    /**
     * Appel reçu : Messagerie ne gère pas les appels, on ouvre l'appli officielle pour décrocher.
     * Appli au premier plan : ouverte tout de suite. Sinon Android interdit d'ouvrir une appli depuis l'arrière-plan :
     * notification d'appel plein écran (écran verrouillé) qui ouvre l'appli officielle, avec le bouton « Ouvrir … ».
     */
    private fun ouvrirPourAppel(c: Conversation) {
        val paquet = appliOfficielle(c.source) ?: return
        val lancer = app.packageManager.getLaunchIntentForPackage(paquet)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        if (auPremierPlan && runCatching { app.startActivity(lancer) }.isSuccess) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val canal = "appels"
        if (Build.VERSION.SDK_INT >= 26) app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(canal, "Appels reçus", NotificationManager.IMPORTANCE_HIGH).apply { setShowBadge(false) })
        val pi = PendingIntent.getActivity(app, ("appel" + c.cle).hashCode(), lancer, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nom = c.source.libelle.substringBefore(" /")
        val n = NotificationCompat.Builder(app, canal)
            .setSmallIcon(R.drawable.ic_bulle)
            .setContentTitle(c.nom)
            .setContentText("Appel $nom : touchez pour répondre dans $nom")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(cfg.couleurEffective(c.source).toInt())
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .addAction(0, "Ouvrir $nom", pi)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .build()
        try { NotificationManagerCompat.from(app).notify(("appel" + c.cle).hashCode(), n) } catch (_: SecurityException) {}
    }

    /** Notification d'essai, locale (rien n'est envoyé sur le réseau). */
    fun notificationEssai(s: Source) {
        val c = Conversation("essai", s, "essai", "Messagerie (essai)", false, System.currentTimeMillis(), emptyList())
        notifierMessages(c, listOf(Message("essai:" + System.currentTimeMillis(), "essai", System.currentTimeMillis(), false,
            "Messagerie", "Ceci est une notification d'essai ${s.libelle}.")))
    }

    /** Canal Android d'une messagerie (son, vibration, pop-up : réglables dans Android). Créé au besoin. */
    fun canalMessages(s: Source): String {
        val canal = "$CANAL_MESSAGES.${s.id}"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = app.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(canal) == null) {
                // Reprend le son, la vibration et l'importance choisis sur l'ancien canal, puis le supprime
                val ancien = nm.getNotificationChannel("msg.${s.id}")
                nm.createNotificationChannel(
                    NotificationChannel(canal, "Messages ${s.libelle}", ancien?.importance?.takeIf { it != NotificationManager.IMPORTANCE_NONE }
                        ?: NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(false); if (Build.VERSION.SDK_INT >= 29) setAllowBubbles(true)
                        if (ancien != null) {
                            setSound(ancien.sound, ancien.audioAttributes)
                            enableVibration(ancien.shouldVibrate()); ancien.vibrationPattern?.let { vibrationPattern = it }
                            lockscreenVisibility = ancien.lockscreenVisibility
                        }
                    })
                if (ancien != null) runCatching { nm.deleteNotificationChannel("msg.${s.id}") }
            }
        }
        return canal
    }

    /** Mode vacances : active ou coupe les notifications Pro ; à l'activation, retire celles déjà affichées. */
    private val _vacances by lazy { MutableStateFlow(cfg.vacances) }
    /** Mode vacances actif (pour le bandeau de l'accueil). */
    val vacances: StateFlow<Boolean> get() = _vacances

    private const val TYPE_VACANCES = "fr.messagerie.vacances"

    /**
     * Mode vacances partagé entre vos applis Messagerie (Android, Mac) : donnée privée du compte Matrix
     * fr.messagerie.vacances = {"v":1,"actif":bool,"date":ms}. Le changement le plus récent l'emporte.
     */
    private suspend fun synchroniserVacances(envoyer: Boolean = false) = withContext(Dispatchers.IO) {
        if (!cfg.matrixConfigure) return@withContext
        val m = Matrix(cfg)
        val d = runCatching { m.lireDonneesCompte(TYPE_VACANCES) }.getOrNull()
        val dateDistante = d?.optLong("date") ?: 0L
        when {
            !envoyer && d != null && dateDistante > cfg.vacancesDate -> {
                // Changé sur un autre appareil
                cfg.vacancesDate = dateDistante
                if (d.optBoolean("actif") != cfg.vacances) definirVacances(d.optBoolean("actif"), partager = false)
            }
            envoyer || cfg.vacancesDate > dateDistante ->
                m.ecrireDonneesCompte(TYPE_VACANCES, org.json.JSONObject().put("v", 1).put("actif", cfg.vacances).put("date", cfg.vacancesDate))
        }
    }

    fun definirVacances(actif: Boolean, partager: Boolean = true) {
        if (partager) {
            cfg.vacancesDate = System.currentTimeMillis()
            portee.launch { runCatching { synchroniserVacances(envoyer = true) } }
        }
        cfg.vacances = actif
        _vacances.value = actif
        _etat.update { it.copy(vacances = actif) }
        if (!actif) return
        val e = _etat.value
        e.conversations.filter { e.estPro(it) }.forEach { runCatching { NotificationManagerCompat.from(app).cancel(it.cle.hashCode()) } }
    }

    /** Participant d'un groupe pour la notification, avec sa photo (téléchargée au besoin) ou son initiale en couleur. */
    private fun membre(c: Conversation, nom: String): androidx.core.app.Person {
        val b = androidx.core.app.Person.Builder().setName(nom).setKey(c.cle + ":" + nom)
        val f = if (!cfg.notifPhoto) null else runCatching { kotlinx.coroutines.runBlocking { photoContact("membre:" + c.cle + ":" + nom) } }.getOrNull()
        val image = f?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
        if (image != null) b.setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(image))
        return b.build()
    }

    private fun notifierMessages(c: Conversation, msgs: List<Message>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val canal = canalMessages(c.source)
        if (Build.VERSION.SDK_INT >= 26) runCatching { app.getSystemService(NotificationManager::class.java).deleteNotificationChannel("messages.${c.source.id}") }
        val intent = Intent(app, MainActivity::class.java).putExtra("cle", c.cle)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(app, c.cle.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val moi = androidx.core.app.Person.Builder().setName("Vous").build()
        val style = NotificationCompat.MessagingStyle(moi).setConversationTitle(if (c.estGroupe) c.nom else null).setGroupConversation(c.estGroupe)
        // Le contact (ou le groupe) avec sa photo : notification « conversation » et bulle
        val contact = androidx.core.app.Person.Builder().setName(c.nom.ifEmpty { "Contact" }).setKey(c.cle)
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(Bulles.image(app, c))).build()
        // Tous les messages reçus non lus de la conversation (pas seulement ceux qui viennent d'arriver),
        // sinon la nouvelle notification remplace l'ancienne et les messages précédents disparaissent
        val nonLus = run {
            val e = _etat.value
            val actuelle = e.conversations.firstOrNull { it.cle == c.cle } ?: c
            val depuis = e.luJusqua(actuelle)
            (actuelle.messages + msgs).filter { !it.deMoi && it.date > depuis }.distinctBy { it.cle }.sortedBy { it.date }
        }
        nonLus.takeLast(8).forEach { m ->
            style.addMessage(if (cfg.notifApercu == 1) "Nouveau message" else m.texteUtile, m.date, if (c.estGroupe) membre(c, m.auteur) else contact)
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
            .also { if (c.cle != "essai") {
                if (cfg.notifBoutons) ActionsNotification.ajouter(app, it, c)
                if (Build.VERSION.SDK_INT >= 30) Bulles.preparer(app, it, c, contact)
            } }
            .also { b -> ecranVerrouille(b, canal, msgs.size) }
            .build()
        try { NotificationManagerCompat.from(app).notify(c.cle.hashCode(), n) } catch (_: SecurityException) {}
    }

    private fun libelle(c: Conversation) = _etat.value.libelleSource(c)

    private fun notifierSiBesoin(c: Conversation) {
        val e = _etat.value
        val ec = e.etat(c)
        if (!cfg.notifications || cfg.notif(c.source) == "aucun" || ec.sourdine || !e.aTraiter(c) || ec.notifiee == c.derniere) return
        if (cfg.vacances && e.estPro(c)) return   // mode vacances : rien pour le Pro
        if (estSpam(c)) return
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
            .also { if (cfg.notifBoutons) ActionsNotification.ajouter(app, it, c) }
            .also { b -> ecranVerrouille(b, CANAL, 1) }
            .build()
        try { NotificationManagerCompat.from(app).notify(c.cle.hashCode(), n) } catch (_: SecurityException) {}
    }

    /** Écran verrouillé : contenu masqué (« n nouveaux messages ») ou rien du tout, selon les Réglages. */
    private fun ecranVerrouille(b: NotificationCompat.Builder, canal: String, n: Int) {
        when (cfg.notifVerrouille) {
            1 -> b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
                NotificationCompat.Builder(app, canal).setSmallIcon(R.drawable.ic_bulle).setContentTitle("Messagerie")
                    .setContentText(if (n > 1) "$n nouveaux messages" else "Nouveau message").build())
            2 -> b.setVisibility(NotificationCompat.VISIBILITY_SECRET)
            else -> b.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
    }

    /** Réponse depuis la notification non partie : la notification le dit (le texte n'est pas perdu de vue). */
    fun echecReponse(c: Conversation, erreur: String) {
        val intent = Intent(app, MainActivity::class.java).putExtra("cle", c.cle)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(app, c.cle.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, canalMessages(c.source))
            .setSmallIcon(R.drawable.ic_bulle)
            .setContentTitle(c.nom)
            .setContentText("Réponse non envoyée : $erreur")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        try { NotificationManagerCompat.from(app).notify(c.cle.hashCode(), n) } catch (_: SecurityException) {}
    }
}
