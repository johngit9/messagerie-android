package fr.messagerie.unifiee

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Non-lus par messagerie sur l'icône de l'appli : Android n'affiche qu'un seul chiffre sur l'icône,
 * donc chaque messagerie devient un raccourci (appui long sur l'icône) « WhatsApp · 3 non lus ».
 */
object Raccourcis {
    /** Messagerie demandée par un raccourci (lue par l'écran Liste). */
    val sourceDemandee = MutableStateFlow<Source?>(null)
    private var dernier: String? = null

    fun mettreAJour(ctx: Context, e: Etat) {
        val presentes = Source.entries.filter { s -> e.conversations.any { it.source == s } }
        val comptes = presentes.associateWith { e.nombreMessagesNonLus(it) }
        val signature = comptes.entries.joinToString { "${it.key.id}=${it.value}" }
        if (signature == dernier) return   // rien de changé : on n'appelle pas le système
        dernier = signature
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx).coerceIn(1, 5)
        val liste = presentes.sortedByDescending { comptes[it] ?: 0 }.take(max).mapIndexed { rang, s ->
            val n = comptes[s] ?: 0
            ShortcutInfoCompat.Builder(ctx, "source.${s.name}")
                .setShortLabel(if (n > 0) "${s.id} · $n" else s.id)
                .setLongLabel(when (n) { 0 -> "${s.libelle} : tout est lu"; 1 -> "${s.libelle} : 1 non lu"; else -> "${s.libelle} : $n non lus" })
                .setIcon(IconCompat.createWithAdaptiveBitmap(pastille(s, n)))
                .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .putExtra("source", s.name).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                .setRank(rang)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(ctx, liste) }
    }

    /** Icône ronde à la couleur de la messagerie, avec le nombre de non-lus (ou l'initiale). */
    private fun pastille(s: Source, n: Int): Bitmap {
        val t = 216   // icône adaptative : le contenu utile tient dans le cercle central
        val b = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(s.couleur.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val texte = if (n > 0) (if (n > 99) "99+" else n.toString()) else s.id.take(1)
        p.textSize = if (texte.length >= 3) 54f else 72f
        c.drawText(texte, t / 2f, t / 2f - (p.descent() + p.ascent()) / 2, p)
        return b
    }
}
