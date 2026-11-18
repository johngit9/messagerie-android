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
        val photo = Moteur.photoEnCache(c.cle)?.let { f -> runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() }
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
    fun preparer(ctx: Context, b: NotificationCompat.Builder, c: Conversation, contact: Person) {
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
        if (runCatching { ShortcutManagerCompat.pushDynamicShortcut(ctx, raccourci) }.isFailure) return
        b.setShortcutId(id).setLocusId(LocusIdCompat(id))
        val intention = Intent(ctx, BulleActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("cle", c.cle)
        val pi = PendingIntent.getActivity(ctx, ("bulle" + c.cle).hashCode(), intention,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        b.setBubbleMetadata(NotificationCompat.BubbleMetadata.Builder(pi, icone).setDesiredHeight(640).build())
    }
}
