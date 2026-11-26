package fr.messagerie.unifiee

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Plantages.installer(this)
        Moteur.init(this)
        SynchroArrierePlan.programmer(this)
        Surveillance.ecouterReseau(this)
    }
}

/**
 * Service au premier plan (optionnel, dans les Réglages) : garde la réception en temps réel
 * quand l'appli est fermée, pour être notifié des réponses attendues.
 */
class ServiceVeille : Service() {
    companion object {
        private const val CANAL = "veille.discret"
        fun mettreAJour(context: Context) {
            val i = Intent(context, ServiceVeille::class.java)
            if (Moteur.cfg.veilleArrierePlan && Moteur.cfg.configure) {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            } else context.stopService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm.deleteNotificationChannel("veille") }   // ancien canal, qui mettait un point sur l'icône
        // Pas de point sur l'icône pour la notification permanente : il masquerait le chiffre des non-lus.
        nm.createNotificationChannel(NotificationChannel(CANAL, "Réception en arrière-plan", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        // La notification permanente porte aussi le chiffre des non-lus (pastille de l'icône).
        Pastille.veilleActive = true
        val n: Notification = Pastille.notification(this, Moteur.etat.value.nombreMessagesNonLus(null))
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, type) else startForeground(1, n)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        Moteur.demarrer()
        Pastille.rafraichir(this)
        return START_STICKY
    }

    override fun onDestroy() {
        Pastille.veilleActive = false
        Pastille.rafraichir(this)
        super.onDestroy()
    }
}
