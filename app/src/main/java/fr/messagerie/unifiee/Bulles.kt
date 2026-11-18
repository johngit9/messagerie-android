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
class BulleActivity : MainActivity()

/**
 * Notifications « conversation » avec bulle : un raccourci par conversation (photo du contact ou initiale),
 * puis la bulle qui ouvre la conversation dans une petite fenêtre. Rien n'est envoyé sur le réseau.
 */
object Bulles {
    private const val T = 216   // taille des icônes adaptatives (le contenu utile tient dans le cercle central)

    fun idRaccourci(c: Conversation) = "conv:" + c.cle

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
        b.setShortcutId(id).setLocusId(LocusIdCompat(id))
        if (!Moteur.cfg.notifBulles && !essai) return true
        val intention = Intent(ctx, BulleActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("cle", c.cle)
        val pi = PendingIntent.getActivity(ctx, ("bulle" + c.cle).hashCode(), intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        b.setBubbleMetadata(NotificationCompat.BubbleMetadata.Builder(pi, icone).setDesiredHeight(640)
            .setAutoExpandBubble(essai).setSuppressNotification(essai).build())
        return true
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
            .setStyle(style).setCategory(NotificationCompat.CATEGORY_MESSAGE).setSilent(true)
        if (!preparer(ctx, b, c, contact, essai = true)) return "Android a refusé le raccourci de la conversation."
        return try { androidx.core.app.NotificationManagerCompat.from(ctx).notify(c.cle.hashCode(), b.build()); null }
        catch (_: SecurityException) { "Les notifications de Messagerie sont désactivées." }
    }
}
