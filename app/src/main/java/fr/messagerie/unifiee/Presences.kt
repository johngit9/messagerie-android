package fr.messagerie.unifiee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Statut de présence de vos contacts (en ligne, absent…), en LECTURE seule, là où les messageries le transmettent.
 * Conversations à deux seulement ; rafraîchi au plus toutes les 2 minutes.
 */
object Presences {
    private val _etat = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Clé de conversation → "online" ou "unavailable" (les contacts hors ligne ou inconnus sont absents). */
    val etat: StateFlow<Map<String, String>> = _etat
    private var derniere = 0L
    /** Salon Matrix → identifiant du contact (fantôme de la passerelle). */
    private val contacts = ConcurrentHashMap<String, String>()

    suspend fun actualiser(cfg: Config, convs: List<Conversation>, salonDe: (Conversation) -> String?) = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - derniere < 120_000) return@withContext
        derniere = System.currentTimeMillis()
        val res = HashMap<String, String>()
        val aDeux = convs.filter { !it.estGroupe && it.source != Source.TEAMS }.sortedByDescending { it.derniere }.take(40)
        // Element : le relais donne la présence de l'autre membre de chaque salon à deux
        if (cfg.relaisConfigure && aDeux.any { it.source == Source.ELEMENT }) runCatching { Relais(cfg).presences() }.getOrNull()?.let { p ->
            aDeux.filter { it.source == Source.ELEMENT }.forEach { c -> p[c.jid]?.takeIf { it != "offline" }?.let { res[c.cle] = it } }
        }
        if (cfg.matrixConfigure) {
            val m = Matrix(cfg)
            runCatching { m.connecter() }.onFailure { _etat.value = res; return@withContext }
            val moi = setOf(cfg.matrixUtilisateur, cfg.moiFantome)
            for (c in aDeux.filter { it.source != Source.ELEMENT }) {
                val salon = salonDe(c) ?: continue
                val id = contacts[salon] ?: runCatching {
                    m.membres(salon).keys.firstOrNull { it !in moi && !it.contains("bot:") && it.startsWith(Pont.pour(c.source).prefixe) }
                }.getOrNull()?.also { contacts[salon] = it } ?: continue
                runCatching { m.get("/_matrix/client/v3/presence/${Http.enc(id)}/status", 5_000) }.getOrNull()?.let { j ->
                    val p = j.optString("presence")
                    if (j.optBoolean("currently_active") || p == "online") res[c.cle] = "online"
                    else if (p == "unavailable") res[c.cle] = "unavailable"
                }
            }
        }
        _etat.value = res
    }
}
