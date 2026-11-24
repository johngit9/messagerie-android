package fr.messagerie.unifiee.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.messagerie.unifiee.Conversation
import fr.messagerie.unifiee.Lecteur
import fr.messagerie.unifiee.MaReaction
import fr.messagerie.unifiee.Message
import fr.messagerie.unifiee.Moteur
import fr.messagerie.unifiee.Reaction
import fr.messagerie.unifiee.Source
import fr.messagerie.unifiee.Vote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vues de la liste (pastilles en haut). */
enum class Vue(val libelle: String) { Toutes("Toutes"), NonLus("Non lues"), ATraiter("À traiter"), Archivees("Archivées"), Spam("Spam") }
/** Discussions à deux ou groupes. */
enum class Genre(val libelle: String) { Tous("Tous"), Discussions("Discussions"), Groupes("Groupes") }
/** Vues Pro / Perso. */
enum class Espace(val libelle: String, val pro: Boolean?) { Tout("Tout", null), Perso("Perso", false), Pro("Pro", true) }

/** Texte sans majuscules ni accents, pour comparer les noms. */
internal fun pourRecherche(t: String) = java.text.Normalizer.normalize(t.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

private val SEPARATEURS = Regex("[^\\p{L}\\p{N}]+")

/**
 * Correspondance d'un nom avec la saisie (déjà passée par [pourRecherche]) : 0 = le nom commence par la saisie,
 * 1 = chaque mot tapé commence un mot du nom (« ron jan »), 2 = la saisie est contenue (3 lettres ou plus), null sinon.
 */
internal fun rangNom(nom: String, q: String): Int? {
    if (q.isBlank()) return null
    val n = pourRecherche(nom)
    if (n.startsWith(q)) return 0
    val mots = n.split(SEPARATEURS).filter { it.isNotEmpty() }
    val tapes = q.split(SEPARATEURS).filter { it.isNotEmpty() }
    if (tapes.isNotEmpty() && tapes.all { t -> mots.any { it.startsWith(t) } }) return 1
    if (q.trim().length >= 3 && q.trim() in n) return 2
    return null
}

/** Proposition d'auto-complétion : un contact, un groupe, ou un membre d'un groupe (groupe = nom du groupe). */
data class Suggestion(val conv: Conversation, val nom: String, val groupe: String? = null)

/** Liste des conversations : filtres et recherche calculés hors du fil d'affichage. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ListeVM : ViewModel() {
    val vue = MutableStateFlow(Vue.Toutes)
    val source = MutableStateFlow<Source?>(null)
    val genre = MutableStateFlow(Genre.Tous)
    val espace = MutableStateFlow(Espace.Tout)
    val recherche = MutableStateFlow("")
    private data class Filtres(val vue: Vue, val source: Source?, val genre: Genre, val espace: Espace)
    private val filtres = combine(vue, source, genre, espace) { v, s, g, es -> Filtres(v, s, g, es) }
    /** Conversation ouverte en dernier : reste visible dans « Non lus » au retour. */
    var derniereOuverte: String? = null

    private val trouvees: StateFlow<Set<String>> = recherche
        .debounce(200)
        .mapLatest { q -> if (q.isBlank()) emptySet() else withContext(Dispatchers.IO) { Moteur.archive.rechercher(q.trim()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Auteurs des messages reçus par conversation : chargés une fois au début d'une recherche. */
    private val auteurs: StateFlow<Map<String, List<String>>> = recherche
        .map { it.isNotBlank() }.distinctUntilChanged()
        .mapLatest { actif -> if (actif) withContext(Dispatchers.IO) { Moteur.archive.auteursParConversation() } else emptyMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Auto-complétion : dans toutes les messageries, sans tenir compte des filtres ; membres des groupes compris. */
    val suggestions: StateFlow<List<Suggestion>> =
        combine(Moteur.etat, recherche, auteurs) { e, brut, aut ->
            val q = pourRecherche(brut.trim())
            if (q.isEmpty()) return@combine emptyList()
            // (suggestion, rang, genre : 0 contact, 1 membre, 2 groupe)
            val trouves = ArrayList<Triple<Suggestion, Int, Int>>()
            val convs = e.conversations.filter { !e.estSpam(it) }
            convs.forEach { c -> rangNom(c.nom, q)?.let { trouves += Triple(Suggestion(c, c.nom), it, if (c.estGroupe) 2 else 0) } }
            val dejaContacts = trouves.filter { it.third == 0 }.map { pourRecherche(it.first.nom) }.toSet()
            val membresVus = HashSet<String>()
            convs.filter { it.estGroupe }.sortedByDescending { it.derniere }.forEach { g ->
                aut[g.cle].orEmpty().forEach { a ->
                    val cleNom = pourRecherche(a)
                    if (cleNom in dejaContacts || !membresVus.add(cleNom)) return@forEach
                    rangNom(a, q)?.let { trouves += Triple(Suggestion(g, a, g.nom), it, 1) }
                }
            }
            trouves.sortedWith(compareBy<Triple<Suggestion, Int, Int>> { it.second }.thenBy { it.third }.thenByDescending { it.first.conv.derniere })
                .map { it.first }.take(10)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibles: StateFlow<List<Conversation>> =
        combine(Moteur.etat, filtres, recherche, trouvees) { e, f, q, t ->
            var l = e.conversations.asSequence()
            if (f.source != null) l = l.filter { it.source == f.source }
            l = when (f.genre) { Genre.Tous -> l; Genre.Discussions -> l.filter { !it.estGroupe }; Genre.Groupes -> l.filter { it.estGroupe } }
            l = when (f.espace) { Espace.Tout -> l; Espace.Perso -> l.filter { !e.estPro(it) }; Espace.Pro -> l.filter { e.estPro(it) } }
            // Mode vacances : le Pro n'apparaît pas dans Non lues / À traiter (sauf en vue Pro)
            val horsVacances: (Conversation) -> Boolean = { c -> !(e.vacances && f.espace != Espace.Pro && e.estPro(c)) }
            // Pendant une recherche, « Non lues » et « À traiter » ne cachent rien : on cherche dans toutes les conversations
            val vueEffective = if (q.isNotBlank() && (f.vue == Vue.NonLus || f.vue == Vue.ATraiter)) Vue.Toutes else f.vue
            l = when (vueEffective) {
                Vue.ATraiter -> l.filter { e.aTraiter(it) && !e.estArchivee(it) && !e.estSpam(it) && horsVacances(it) }
                Vue.NonLus -> l.filter { (e.estNonLue(it) || it.cle == derniereOuverte) && !e.estArchivee(it) && !e.estSpam(it) && horsVacances(it) }
                Vue.Toutes -> l.filter { !e.estArchivee(it) && !e.estSpam(it) }
                Vue.Archivees -> l.filter { e.estArchivee(it) && !e.estSpam(it) }
                Vue.Spam -> l.filter { e.estSpam(it) }
            }
            val r = q.trim()
            val rn = pourRecherche(r)
            if (r.isNotEmpty()) l = l.filter { c -> rangNom(c.nom, rn) != null || c.cle in t || e.synthese(c)?.resume?.contains(r, true) == true }
            // Épinglées en tête (dans leur ordre), puis le reste par date.
            val (ep, reste) = l.toList().partition { e.estEpinglee(it) }
            ep.sortedBy { e.epinglees.indexOf(it.cle) } + reste
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Si la vue filtrée ne trouve rien : résultats dans toute la messagerie (autres messageries, espaces, archives). */
    val ailleurs: StateFlow<List<Conversation>> =
        combine(Moteur.etat, recherche, trouvees, visibles) { e, q, t, vis ->
            val r = q.trim()
            if (r.isEmpty() || vis.isNotEmpty()) return@combine emptyList()
            val rn = pourRecherche(r)
            e.conversations.filter { c -> !e.estSpam(c) && (rangNom(c.nom, rn) != null || c.cle in t || e.synthese(c)?.resume?.contains(r, true) == true) }
                .sortedByDescending { it.derniere }.take(50)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

/** Une conversation : historique, réactions et actions. */
class ConversationVM(private val cle: String) : ViewModel() {
    private val _historique = MutableStateFlow<List<Message>>(emptyList())
    val historique = _historique.asStateFlow()
    private val _total = MutableStateFlow(0)
    val total = _total.asStateFlow()
    private val _chargement = MutableStateFlow(false)
    val chargement = _chargement.asStateFlow()
    private val _reactions = MutableStateFlow<Map<String, List<Reaction>>>(emptyMap())
    val reactions = _reactions.asStateFlow()
    private val _mesReactions = MutableStateFlow<Map<String, MaReaction>>(emptyMap())
    val mesReactions = _mesReactions.asStateFlow()
    private val _votes = MutableStateFlow<Map<String, List<Vote>>>(emptyMap())
    /** Votes des sondages : clé du sondage → dernier vote de chaque personne. */
    val votes = _votes.asStateFlow()
    private val _favoris = MutableStateFlow<Set<String>>(emptySet())
    val favoris = _favoris.asStateFlow()

    init {
        // Réactions arrivées sans nouveau message (posées depuis l'appli officielle) : relues à chaque actualisation
        viewModelScope.launch {
            Moteur.etat.map { it.derniereActualisation }.distinctUntilChanged().collect { withContext(Dispatchers.IO) { chargerReactions() } }
        }
        // À l'ouverture : récupère aussi les anciennes réactions du salon (lecture seule)
        viewModelScope.launch {
            val c = Moteur.etat.value.conversations.firstOrNull { it.cle == cle } ?: return@launch
            if (Moteur.recupererReactions(c)) withContext(Dispatchers.IO) { chargerReactions() }
        }
    }

    /** Relit l'archive (à l'ouverture et à chaque nouveau message). */
    fun actualiser(initial: List<Message>) {
        if (_historique.value.isEmpty()) _historique.value = initial
        viewModelScope.launch(Dispatchers.IO) {
            val n = maxOf(200, _historique.value.size)
            _historique.value = Moteur.archive.messages(cle, null, n)
            _total.value = Moteur.archive.nombreMessages(cle)
            chargerReactions()
        }
    }

    fun plusAnciens() {
        if (_chargement.value) return
        _chargement.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val plus = Moteur.archive.messages(cle, _historique.value.firstOrNull()?.date, 200)
            _historique.value = plus + _historique.value
            _chargement.value = false
        }
    }

    // --- Recherche dans la discussion
    private val _trouves = MutableStateFlow<List<Message>>(emptyList())
    val trouves = _trouves.asStateFlow()
    private var tacheRecherche: kotlinx.coroutines.Job? = null

    fun chercher(q: String) {
        tacheRecherche?.cancel()
        if (q.isBlank()) { _trouves.value = emptyList(); return }
        tacheRecherche = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(250)
            _trouves.value = Moteur.archive.chercherDans(cle, q.trim())
        }
    }

    /** Charge l'historique jusqu'à ce message (s'il n'est pas déjà affiché), puis appelle pret. */
    fun allerA(m: Message, pret: () -> Unit) {
        if (_historique.value.any { it.cle == m.cle }) { pret(); return }
        viewModelScope.launch(Dispatchers.IO) {
            val n = Moteur.archive.nombreDepuis(cle, m.date) + 30
            _historique.value = Moteur.archive.messages(cle, null, maxOf(n, _historique.value.size))
            withContext(Dispatchers.Main) { pret() }
        }
    }

    private fun chargerReactions() {
        _reactions.value = Moteur.archive.reactions(cle)
        _mesReactions.value = Moteur.archive.mesReactions(cle)
        _votes.value = Moteur.archive.votes(cle)
        _favoris.value = Moteur.archive.clesFavoris(cle)
    }

    /** Ajoute ou retire un message des favoris (sur ce téléphone seulement). */
    fun basculerFavori(m: Message) {
        val oui = m.cle !in _favoris.value
        _favoris.value = if (oui) _favoris.value + m.cle else _favoris.value - m.cle
        viewModelScope.launch(Dispatchers.IO) { Moteur.archive.definirFavori(m, oui) }
    }

    /** Vote à un sondage (seulement sur votre appui). */
    fun voter(m: Message, c: Conversation, choix: List<String>, afficher: (String) -> Unit) {
        viewModelScope.launch {
            val err = Moteur.voter(m, c, choix)
            withContext(Dispatchers.IO) { chargerReactions() }
            afficher(if (err == null) "Vote envoyé" else "Échec : $err")
        }
    }

    /** Pose ou retire votre réaction (seulement sur votre appui). Renvoie le texte à afficher. */
    fun reagir(m: Message, c: Conversation, emoji: String?, afficher: (String) -> Unit) {
        viewModelScope.launch {
            val err = Moteur.reagir(m, c, emoji)
            withContext(Dispatchers.IO) { chargerReactions() }
            afficher(when {
                err != null -> "Réaction non transmise : $err"
                emoji == null -> "Réaction retirée"
                Moteur.reactionTransmissible(m, c) -> "Réaction $emoji envoyée"
                else -> "Réaction $emoji gardée sur ce téléphone"
            })
        }
    }

    override fun onCleared() { Lecteur.arreter() }
}
