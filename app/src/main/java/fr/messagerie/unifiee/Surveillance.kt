package fr.messagerie.unifiee

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Fiabilité : repère les messageries déconnectées (passerelle du serveur en panne, compte déconnecté…)
 * et prévient par une alerte dans la liste et une notification. Lecture seule : rien n'est envoyé.
 */
object Surveillance {
    data class Panne(val source: Source, val detail: String, val depuis: Long?)

    /** Reconnexion demandée depuis la notification (ouvre la fenêtre de reconnexion WhatsApp). */
    val reconnexionDemandee = MutableStateFlow(false)

    private val _pannes = MutableStateFlow<List<Panne>>(emptyList())
    val pannes: StateFlow<List<Panne>> = _pannes
    /** État de chaque passerelle donné par le serveur (null si le serveur ne le fournit pas). */
    private val _serveur = MutableStateFlow<Map<Source, Relais.EtatServeur>?>(null)
    val serveur: StateFlow<Map<Source, Relais.EtatServeur>?> = _serveur
    private val _sauvegarde = MutableStateFlow<Long?>(null)
    /** Date de la dernière sauvegarde automatique de la configuration du serveur. */
    val derniereSauvegarde: StateFlow<Long?> = _sauvegarde

    private const val CANAL = "pannes"
    private val verrou = Mutex()
    @Volatile private var derniere = 0L

    /** Vérifie au plus toutes les 5 minutes (sauf si force). */
    suspend fun verifier(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!verrou.tryLock()) return@withContext
        try {
            if (!force && System.currentTimeMillis() - derniere < 5 * 60_000) return@withContext
            derniere = System.currentTimeMillis()
            val cfg = Moteur.cfg
            val e = Moteur.etat.value
            // Messageries utilisées : déjà des conversations, ou synchro en cours
            val actives = Source.entries.filter { s ->
                s != Source.TEAMS && (e.conversations.any { it.source == s } || e.sources[s] is EtatSource.Ok || e.sources[s] is EtatSource.Erreur)
            }.toSet()
            val res = ArrayList<Panne>()
            val viaServeur = if (cfg.relaisConfigure) runCatching { Relais(cfg).passerelles() }.getOrNull() else null
            if (viaServeur != null) {
                _serveur.value = viaServeur.first
                _sauvegarde.value = viaServeur.second
                viaServeur.first.forEach { (s, p) ->
                    if (s in actives && p.ok == false) res += Panne(s, p.detail.ifBlank { "déconnectée" }, p.depuis)
                }
            } else {
                _serveur.value = null
                // Secours : dernier message du robot de chaque passerelle (récent seulement)
                for (s in actives) {
                    if (s == Source.ELEMENT) continue
                    val p = Moteur.etatPasserelle(s) ?: continue
                    if (p.ok == false && (p.date ?: 0) > System.currentTimeMillis() - 3 * 86_400_000L)
                        res += Panne(s, p.message ?: "déconnectée", p.date)
                }
            }
            _pannes.value = res.sortedBy { it.source.ordinal }
            notifier(res)
        } finally { verrou.unlock() }
    }

    /** Une notification par messagerie qui vient de se déconnecter (pas de répétition tant qu'elle reste en panne). */
    private fun notifier(pannes: List<Panne>) {
        val deja = Moteur.archive.curseur("pannes.notifiees")?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        val maintenant = pannes.map { it.source.id }.toSet()
        Moteur.archive.definirCurseur("pannes.notifiees", maintenant.joinToString(","))
        val nouvelles = pannes.filter { it.source.id !in deja }
        if (nouvelles.isEmpty()) return
        val app = Moteur.app
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) app.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Messagerie déconnectée", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) })
        val pi = PendingIntent.getActivity(app, CANAL.hashCode(),
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        for (p in nouvelles) {
            // WhatsApp : toucher la notification lance directement la reconnexion par code
            val piP = if (p.source == Source.WHATSAPP && Moteur.cfg.relaisConfigure) PendingIntent.getActivity(app, "reco.whatsapp".hashCode(),
                Intent(app, MainActivity::class.java).putExtra("reconnecter", Source.WHATSAPP.name)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) else pi
            val n = NotificationCompat.Builder(app, CANAL)
                .setSmallIcon(R.drawable.ic_bulle)
                .setContentTitle("${p.source.libelle} est déconnecté")
                .setContentText(p.detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(p.detail + "\nVous ne recevez plus les messages ${p.source.libelle} tant que ce n'est pas réparé."))
                .setColor(Moteur.cfg.couleurEffective(p.source).toInt())
                .setContentIntent(piP)
                .apply { if (piP !== pi) addAction(0, "Reconnecter", piP) }
                .setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(app).notify("panne.${p.source.id}".hashCode(), n) } catch (_: SecurityException) {}
        }
    }
}
