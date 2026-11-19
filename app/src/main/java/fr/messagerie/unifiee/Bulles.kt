package fr.messagerie.unifiee

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Bulle de conversation qui s'ouvre par-dessus les autres applis (Android 11 et plus). */
class BulleActivity : MainActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        Bulles.journal("fenêtre de la bulle ouverte")
        super.onCreate(savedInstanceState)
    }
    override fun onDestroy() {
        Bulles.journal("fenêtre de la bulle fermée" + if (isFinishing) " (fin)" else " (par Android)")
        super.onDestroy()
    }
}

/**
 * Notifications « conversation » avec bulle : un raccourci par conversation (photo du contact ou initiale),
 * puis la bulle qui ouvre la conversation dans une petite fenêtre. Rien n'est envoyé sur le réseau.
 */
object Bulles {
    private const val T = 216   // taille des icônes adaptatives (le contenu utile tient dans le cercle central)

    fun idRaccourci(c: Conversation) = "conv:" + c.cle

    /** Journal de l'essai de bulle (affiché dans Réglages > Notifications), pour comprendre une bulle qui disparaît. */
    private val evenements = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var debutEssai = 0L
    @Volatile var dernierEssai: String? = null
    fun journal(t: String) { if (debutEssai > 0) evenements.add("%.1f s : %s".format((System.currentTimeMillis() - debutEssai) / 1000.0, t)) }

    /** État de la bulle d'essai maintenant, ajouté au journal ; renvoie tout le journal. */
    fun diagnostic(ctx: Context): String {
        val cle = dernierEssai ?: return ""
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        val sbn = runCatching { nm.activeNotifications.firstOrNull { it.id == cle.hashCode() } }.getOrNull()
        val enBulle = sbn != null && android.os.Build.VERSION.SDK_INT >= 29 && sbn.notification.flags and android.app.Notification.FLAG_BUBBLE != 0
        val raccourci = runCatching {
            ShortcutManagerCompat.getShortcuts(ctx, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED).any { it.id == "conv:$cle" }
        }.getOrDefault(false)
        val canal = sbn?.notification?.channelId?.let { id -> if (android.os.Build.VERSION.SDK_INT >= 29) nm.getNotificationChannel(id)?.canBubble() else null }
        journal("notification " + (if (sbn != null) "présente" else "retirée") + ", " + (if (enBulle) "en bulle" else "pas en bulle") +
            ", raccourci " + (if (raccourci) "présent" else "absent") + (canal?.let { ", canal " + if (it) "autorise les bulles" else "sans bulles" } ?: ""))
        return evenements.joinToString("\n")
    }

    /** Raccourcis des dernières conversations notifiées (au plus 3), à garder publiés pour leurs bulles. */
    private val derniers = LinkedHashMap<String, ShortcutInfoCompat>()
    fun recents(): List<ShortcutInfoCompat> = synchronized(derniers) { derniers.values.toList() }

    /** Photo du contact déjà en cache (pas de téléchargement ici), sinon initiale sur la couleur de la messagerie. */
    fun image(ctx: Context, c: Conversation): Bitmap {
        val photo = if (!Moteur.cfg.notifPhoto) null else Moteur.photoEnCache(c.cle)?.let { f -> runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() }
        val b = Bitmap.createBitmap(T, T, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        if (photo != null) {
            val cote = minOf(photo.width, photo.height)
            val src = Rect((photo.width - cote) / 2, (photo.height - cote) / 2, (photo.width + cote) / 2, (photo.height + cote) / 2)
            cv.drawBitmap(photo, src, Rect(0, 0, T, T), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            cv.drawColor(Moteur.cfg.couleurEffective(c.source).toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 80f }
            val initiale = c.nom.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            cv.drawText(initiale, T / 2f, T / 2f - (p.descent() + p.ascent()) / 2, p)
        }
        return b
    }

    /** Publie le raccourci de la conversation et branche la bulle sur la notification. */
    fun preparer(ctx: Context, b: NotificationCompat.Builder, c: Conversation, contact: Person, essai: Boolean = false): Boolean {
        val icone = IconCompat.createWithAdaptiveBitmap(image(ctx, c))
        val id = idRaccourci(c)
        val ouvrir = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("cle", c.cle)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val raccourci = ShortcutInfoCompat.Builder(ctx, id)
            .setShortLabel(c.nom.take(24).ifEmpty { "Conversation" })
            .setLongLabel(c.nom.ifEmpty { "Conversation" })
            .setIcon(icone)
            .setIntent(ouvrir)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(id))
            .setPerson(contact)
            .build()
        if (runCatching { ShortcutManagerCompat.pushDynamicShortcut(ctx, raccourci) }.isFailure) return false
        synchronized(derniers) { derniers.remove(id); derniers[id] = raccourci; while (derniers.size > 3) derniers.remove(derniers.keys.first()) }
        b.setShortcutId(id).setLocusId(LocusIdCompat(id))
        if (!Moteur.cfg.notifBulles && !essai) return true
        val intention = Intent(ctx, BulleActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("cle", c.cle)
        val pi = PendingIntent.getActivity(ctx, ("bulle" + c.cle).hashCode(), intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        b.setBubbleMetadata(NotificationCompat.BubbleMetadata.Builder(pi, icone).setDesiredHeight(640)
            .setAutoExpandBubble(essai).build())
        return true
    }

    /**
     * Retire la notification d'une conversation, sauf si elle est affichée en bulle :
     * retirer la notification ferait disparaître la bulle aussitôt.
     */
    fun retirerNotification(ctx: Context, id: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val active = runCatching { ctx.getSystemService(android.app.NotificationManager::class.java).activeNotifications }.getOrNull()
                ?.firstOrNull { it.id == id }
            if (active != null && active.notification.flags and android.app.Notification.FLAG_BUBBLE != 0) return
        }
        runCatching { androidx.core.app.NotificationManagerCompat.from(ctx).cancel(id) }
    }

    /** Ce qu'Android autorise pour les bulles de l'appli (texte pour les Réglages). */
    fun etat(ctx: Context): String {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 31) return when (nm.bubblePreference) {
            android.app.NotificationManager.BUBBLE_PREFERENCE_ALL -> "Android : bulles autorisées pour toutes les conversations."
            android.app.NotificationManager.BUBBLE_PREFERENCE_SELECTED -> "Android : bulles seulement pour les conversations choisies (icône bulle en bas d'une notification)."
            else -> "Android : bulles désactivées pour Messagerie. Touchez « Autoriser les bulles » et choisissez « Toutes les conversations »."
        }
        @Suppress("DEPRECATION")
        return if (nm.areBubblesAllowed()) "Android : bulles autorisées." else "Android : bulles désactivées pour Messagerie."
    }

    /** Bulle d'essai locale (rien n'est envoyé) sur la conversation la plus récente ; null si tout va bien, sinon la raison. */
    fun essayer(ctx: Context): String? {
        if (android.os.Build.VERSION.SDK_INT < 30) return "Il faut Android 11 ou plus."
        val c = Moteur.etat.value.conversations.filter { it.source != Source.TEAMS }.maxByOrNull { it.derniere }
            ?: return "Aucune conversation pour l'essai."
        val contact = Person.Builder().setName(c.nom.ifEmpty { "Contact" }).setKey(c.cle).setIcon(IconCompat.createWithBitmap(image(ctx, c))).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Vous").build())
            .addMessage("Essai de bulle : touchez-la pour ouvrir la conversation.", System.currentTimeMillis(), contact)
        val b = NotificationCompat.Builder(ctx, Moteur.canalMessages(c.source)).setSmallIcon(R.drawable.ic_bulle)
            .setStyle(style).setCategory(NotificationCompat.CATEGORY_MESSAGE).setOnlyAlertOnce(true)
        evenements.clear(); debutEssai = System.currentTimeMillis(); dernierEssai = c.cle
        journal("essai sur « ${c.nom} »")
        if (!preparer(ctx, b, c, contact, essai = true)) return "Android a refusé le raccourci de la conversation."
        return try { androidx.core.app.NotificationManagerCompat.from(ctx).notify(c.cle.hashCode(), b.build()); null }
        catch (_: SecurityException) { "Les notifications de Messagerie sont désactivées." }
    }
}
