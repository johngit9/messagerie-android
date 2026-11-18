package fr.messagerie.unifiee

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Chiffre des non-lus sur l'icône de l'appli.
 * Android standard n'affiche qu'un point ; plusieurs lanceurs (OPPO / OnePlus / realme, Samsung, Huawei, Sony…)
 * acceptent un nombre par leur propre interface, comme le fait WhatsApp. Chaque essai est sans effet ailleurs.
 */
object Pastille {
    private var dernier = -1
    private const val CANAL = "compteur"
    private const val CANAL_SANS_POINT = "veille.discret"
    const val ID_COMPTEUR = 999
    /** Le service « à l'écoute » tourne : sa notification permanente porte alors le chiffre. */
    @Volatile var veilleActive = false

    /**
     * Notification discrète (sans son, repliée) qui porte le total des non-lus : c'est elle que le lanceur
     * additionne pour afficher le chiffre, comme pour WhatsApp. Les notifications de messages ne comptent pas,
     * sinon le chiffre serait doublé.
     */
    fun notification(ctx: Context, n: Int): android.app.Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Nombre de messages non lus", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(true) })
        // Tout est lu : la notification permanente passe sur un canal sans point sur l'icône
        nm.createNotificationChannel(NotificationChannel(CANAL_SANS_POINT, "Réception en arrière-plan", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val texte = when (n) { 0 -> "Tout est lu"; 1 -> "1 message non lu"; else -> "$n messages non lus" }
        return NotificationCompat.Builder(ctx, if (n > 0) CANAL else CANAL_SANS_POINT)
            .setSmallIcon(R.drawable.ic_bulle)
            .setContentTitle(if (veilleActive) "Messagerie à l'écoute" else texte)
            .setContentText(if (veilleActive) texte else "Toucher pour ouvrir Messagerie")
            .setContentIntent(pi)
            .setNumber(n)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setOngoing(veilleActive)
            .setOnlyAlertOnce(true).setSilent(true)
            .setShowWhen(false)
            .build()
    }

    /** Remet le chiffre à jour, même si le total n'a pas changé (par exemple au démarrage du service). */
    fun rafraichir(ctx: Context) { val n = dernier.coerceAtLeast(0); dernier = -1; afficher(ctx, n) }

    fun afficher(ctx: Context, n: Int) {
        if (n == dernier) return
        dernier = n
        runCatching {
            val nmc = NotificationManagerCompat.from(ctx)
            when {
                veilleActive -> nmc.notify(1, notification(ctx, n))
                n > 0 -> nmc.notify(ID_COMPTEUR, notification(ctx, n))
                else -> nmc.cancel(ID_COMPTEUR)
            }
            if (veilleActive) nmc.cancel(ID_COMPTEUR)
        }
        val classe = MainActivity::class.java.name
        // OPPO, OnePlus (ColorOS / OxygenOS récents), realme
        runCatching {
            ctx.contentResolver.call(Uri.parse("content://com.android.badge/badge"), "setAppBadgeCount", null,
                Bundle().apply { putInt("app_badge_count", n) })
        }
        // Huawei / Honor
        runCatching {
            ctx.contentResolver.call(Uri.parse("content://com.huawei.android.launcher.settings/badge/"), "change_badge", null,
                Bundle().apply { putString("package", ctx.packageName); putString("class", classe); putInt("badgenumber", n) })
        }
        // Samsung (anciens), LG, Sony, HTC, ASUS : diffusion classique
        runCatching {
            ctx.sendBroadcast(Intent("android.intent.action.BADGE_COUNT_UPDATE")
                .putExtra("badge_count", n).putExtra("badge_count_package_name", ctx.packageName)
                .putExtra("badge_count_class_name", classe))
        }
        // OPPO (anciens)
        runCatching {
            ctx.sendBroadcast(Intent("com.oppo.unsettledevent").putExtra("pakeageName", ctx.packageName)
                .putExtra("number", n).putExtra("upgradeNumber", n))
        }
    }
}
