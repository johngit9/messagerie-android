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
        Moteur.init(this)
        SynchroArrierePlan.programmer(this)
    }
}

/**
 * Service au premier plan (optionnel, dans les Réglages) : garde la réception en temps réel
 * quand l'appli est fermée, pour être notifié des réponses attendues.
 */
class ServiceVeille : Service() {
    companion object {
        private const val CANAL = "veille"
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
        nm.createNotificationChannel(NotificationChannel(CANAL, "Réception en arrière-plan", NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_bulle)
            .setContentTitle("Messagerie à l'écoute")
            .setContentText("Réception des nouveaux messages")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, type) else startForeground(1, n)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        Moteur.demarrer()
        return START_STICKY
    }
}
