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
    /** Filtre demandé par un raccourci ou une notification (lu par l'écran Liste). */
    val filtreDemande = MutableStateFlow<FiltreDemande?>(null)
    /** Appui sur une notification ou un widget « non lus » : résolu à l'ouverture de l'appli (voir MainActivity). */
    val nonLusDemandes = MutableStateFlow<NonLusDemandes?>(null)

    /** Messagerie (null = toutes) et vue « Non lues » ou « Toutes » à afficher dans la liste. */
    data class FiltreDemande(val source: Source?, val nonLus: Boolean)
    /** Non-lus d'une messagerie (null = toutes) ; l'horodatage distingue deux appuis de suite. */
    data class NonLusDemandes(val source: Source?, val quand: Long = System.currentTimeMillis())

    /**
     * Intention « ouvrir les non-lus » : la conversation elle-même s'il n'y en a qu'une, sinon la liste
     * filtrée sur « Non lues » (et sur la messagerie). Calculé au moment de l'appui, pas à l'affichage de la notification.
     */
    fun intentionNonLus(ctx: Context, source: Source?): Intent =
        Intent(ctx, MainActivity::class.java).setAction("nonlus." + (source?.name ?: "tout"))
            .putExtra("nonlus", source?.name ?: "")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    private var dernier: String? = null

    /** Republie tout (raccourcis de messagerie et de conversation) : Android les retire quand l'entrée d'icône change. */
    fun republier(ctx: Context) { dernier = null; runCatching { mettreAJour(ctx, Moteur.etat.value) } }

    fun mettreAJour(ctx: Context, e: Etat) {
        val presentes = Source.entries.filter { s -> e.conversations.any { it.source == s } }
        val comptes = presentes.associateWith { e.nombreMessagesNonLus(it) }
        val signature = comptes.entries.joinToString { "${it.key.id}=${it.value}" }
        if (signature == dernier) return   // rien de changé : on n'appelle pas le système
        dernier = signature
        // Place laissée aux raccourcis de conversation : sans eux, Android retire la bulle aussitôt
        val max = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(ctx) - 3).coerceIn(1, 3)
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
        // Pas de setDynamicShortcuts : il effacerait les raccourcis de conversation, indispensables aux bulles
        runCatching {
            val gardes = liste.map { it.id }.toSet()
            val anciens = ShortcutManagerCompat.getDynamicShortcuts(ctx).map { it.id }.filter { it.startsWith("source.") && it !in gardes }
            if (anciens.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(ctx, anciens)
            liste.forEach { ShortcutManagerCompat.pushDynamicShortcut(ctx, it) }
            // Les conversations en bulle repassent en dernier : ce sont les plus récentes, jamais évincées
            Bulles.recents().forEach { ShortcutManagerCompat.pushDynamicShortcut(ctx, it) }
        }
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
