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

    val visibles: StateFlow<List<Conversation>> =
        combine(Moteur.etat, filtres, recherche, trouvees) { e, f, q, t ->
            var l = e.conversations.asSequence()
            if (f.source != null) l = l.filter { it.source == f.source }
            l = when (f.genre) { Genre.Tous -> l; Genre.Discussions -> l.filter { !it.estGroupe }; Genre.Groupes -> l.filter { it.estGroupe } }
            l = when (f.espace) { Espace.Tout -> l; Espace.Perso -> l.filter { !e.estPro(it) }; Espace.Pro -> l.filter { e.estPro(it) } }
            l = when (f.vue) {
                Vue.ATraiter -> l.filter { e.aTraiter(it) && !e.estArchivee(it) && !e.estSpam(it) }
                Vue.NonLus -> l.filter { (e.estNonLue(it) || it.cle == derniereOuverte) && !e.estArchivee(it) && !e.estSpam(it) }
                Vue.Toutes -> l.filter { !e.estArchivee(it) && !e.estSpam(it) }
                Vue.Archivees -> l.filter { e.estArchivee(it) && !e.estSpam(it) }
                Vue.Spam -> l.filter { e.estSpam(it) }
            }
            val r = q.trim()
            if (r.isNotEmpty()) l = l.filter { c -> c.nom.contains(r, true) || c.cle in t || e.synthese(c)?.resume?.contains(r, true) == true }
            // Épinglées en tête (dans leur ordre), puis le reste par date.
            val (ep, reste) = l.toList().partition { e.estEpinglee(it) }
            ep.sortedBy { e.epinglees.indexOf(it.cle) } + reste
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
