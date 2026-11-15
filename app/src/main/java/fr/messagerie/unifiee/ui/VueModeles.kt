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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vues de la liste (pastilles en haut). */
enum class Vue(val libelle: String) { ATraiter("À traiter"), NonLus("Non lus"), Toutes("Toutes"), Groupes("Groupes") }

/** Liste des conversations : filtres et recherche calculés hors du fil d'affichage. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ListeVM : ViewModel() {
    val vue = MutableStateFlow(Vue.ATraiter)
    val source = MutableStateFlow<Source?>(null)
    val recherche = MutableStateFlow("")
    /** Conversation ouverte en dernier : reste visible dans « Non lus » au retour. */
    var derniereOuverte: String? = null

    private val trouvees: StateFlow<Set<String>> = recherche
        .debounce(200)
        .mapLatest { q -> if (q.isBlank()) emptySet() else withContext(Dispatchers.IO) { Moteur.archive.rechercher(q.trim()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val visibles: StateFlow<List<Conversation>> =
        combine(Moteur.etat, vue, source, recherche, trouvees) { e, v, s, q, t ->
            var l = e.conversations.asSequence()
            if (s != null) l = l.filter { it.source == s }
            l = when (v) {
                Vue.ATraiter -> l.filter { e.aTraiter(it) }
                Vue.NonLus -> l.filter { e.estNonLue(it) || it.cle == derniereOuverte }
                Vue.Toutes -> l
                Vue.Groupes -> l.filter { it.estGroupe }
            }
            val r = q.trim()
            if (r.isNotEmpty()) l = l.filter { c -> c.nom.contains(r, true) || c.cle in t || e.synthese(c)?.resume?.contains(r, true) == true }
            l.toList()
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
