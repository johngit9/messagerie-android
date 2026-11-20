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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews

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
    /** Non-lus par messagerie (les plus nombreux d'abord), pour la notification permanente. */
    private fun parSource(): List<Pair<Source, Int>> = runCatching {
        val e = Moteur.etat.value
        Source.entries.map { it to e.nombreMessagesNonLus(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
    }.getOrDefault(emptyList())

    /** Bande de pastilles : fond à la couleur de la messagerie, logo blanc et nombre. Lisible sur fond clair ou sombre. */
    private fun bande(ctx: Context, liste: List<Pair<Source, Int>>): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val h = (26 * d).toInt(); val ecart = 6 * d; val logo = 15 * d; val marge = 8 * d
        val texte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = 13 * d; typeface = Typeface.DEFAULT_BOLD
        }
        val largeurs = liste.map { (_, n) -> marge + logo + 5 * d + texte.measureText(compte(n)) + marge }
        val w = (largeurs.sum() + ecart * (liste.size - 1).coerceAtLeast(0)).toInt().coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val fond = Paint(Paint.ANTI_ALIAS_FLAG)
        val blanc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        var x = 0f
        liste.forEachIndexed { i, (s, n) ->
            fond.color = Moteur.cfg.couleurEffective(s).toInt()
            c.drawRoundRect(RectF(x, 0f, x + largeurs[i], h.toFloat()), h / 2f, h / 2f, fond)
            val lx = x + marge; val ly = (h - logo) / 2
            val chemin = fr.messagerie.unifiee.ui.cheminLogo(s)?.let { runCatching { androidx.core.graphics.PathParser.createPathFromPathData(it) }.getOrNull() }
            if (chemin != null) {
                chemin.transform(Matrix().apply { setScale(logo / 24f, logo / 24f); postTranslate(lx, ly) })
                c.drawPath(chemin, blanc)
            } else {
                val p = Paint(texte).apply { textAlign = Paint.Align.CENTER }
                c.drawText(s.id.take(1), lx + logo / 2, h / 2f - (p.descent() + p.ascent()) / 2, p)
            }
            c.drawText(compte(n), lx + logo + 5 * d, h / 2f - (texte.descent() + texte.ascent()) / 2, texte)
            x += largeurs[i] + ecart
        }
        return b
    }

    private fun compte(n: Int) = if (n > 999) "999+" else n.toString()

    fun notification(ctx: Context, n: Int): android.app.Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Nombre de messages non lus", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(true) })
        // Tout est lu : la notification permanente passe sur un canal sans point sur l'icône
        nm.createNotificationChannel(NotificationChannel(CANAL_SANS_POINT, "Réception en arrière-plan", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val texte = when (n) { 0 -> "Tout est lu"; 1 -> "1 message non lu"; else -> "$n messages non lus" }
        val liste = if (n > 0) parSource() else emptyList()
        val resume = liste.joinToString(" · ") { "${it.first.libelle} ${it.second}" }
        val titre = texte
        val vue = if (liste.isEmpty()) null else runCatching {
            RemoteViews(ctx.packageName, R.layout.notif_compteurs).apply {
                setTextViewText(R.id.notif_titre, titre)
                setImageViewBitmap(R.id.notif_bande, bande(ctx, liste))
                setContentDescription(R.id.notif_bande, resume)
            }
        }.getOrNull()
        return NotificationCompat.Builder(ctx, if (n > 0) CANAL else CANAL_SANS_POINT)
            .apply { if (vue != null) setCustomContentView(vue).setStyle(NotificationCompat.DecoratedCustomViewStyle()) }
            .setSmallIcon(R.drawable.ic_bulle)
            .setContentTitle(if (veilleActive) "Messagerie à l'écoute" else texte)
            .setContentText(if (resume.isNotEmpty()) resume else if (veilleActive) texte else "Toucher pour ouvrir Messagerie")
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

    private var derniereRepartition = ""

    fun afficher(ctx: Context, n: Int) {
        val repartition = parSource().joinToString { "${it.first.id}=${it.second}" }
        if (n == dernier && repartition == derniereRepartition) return
        dernier = n; derniereRepartition = repartition
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
