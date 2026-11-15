package fr.messagerie.unifiee

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Widget d'écran d'accueil : total des non-lus et chiffre de chaque messagerie (mis à jour à chaque lecture). */
class WidgetNonLus : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gestionnaire: AppWidgetManager, ids: IntArray) {
        runCatching { Moteur.init(ctx) }
        mettreAJour(ctx, Moteur.etat.value, force = true)
    }

    companion object {
        private val pastilles = intArrayOf(R.id.w0, R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5)
        private var dernier: String? = null

        fun mettreAJour(ctx: Context, e: Etat, force: Boolean = false) {
            val gm = AppWidgetManager.getInstance(ctx)
            val ids = gm.getAppWidgetIds(ComponentName(ctx, WidgetNonLus::class.java))
            if (ids.isEmpty()) return
            val total = e.nombreMessagesNonLus(null)
            val parSource = Source.entries.map { it to e.nombreMessagesNonLus(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
            val signature = "$total|" + parSource.joinToString { "${it.first.id}=${it.second}" } + "|${Moteur.cfg.accent}"
            if (!force && signature == dernier) return
            dernier = signature
            val v = RemoteViews(ctx.packageName, R.layout.widget_nonlus)
            v.setTextViewText(R.id.widget_total, if (total > 999) "999+" else total.toString())
            v.setTextViewText(R.id.widget_libelle, when (total) { 0 -> "Tout est lu"; 1 -> "message non lu"; else -> "messages non lus" })
            Moteur.cfg.accent.takeIf { it != 0L }?.let { v.setTextColor(R.id.widget_total, it.toInt()) }
            v.setOnClickPendingIntent(R.id.widget_racine, PendingIntent.getActivity(ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
            pastilles.forEachIndexed { i, id ->
                val p = parSource.getOrNull(i)
                if (p == null) { v.setViewVisibility(id, View.GONE); return@forEachIndexed }
                val (s, n) = p
                v.setViewVisibility(id, View.VISIBLE)
                v.setTextViewText(id, "${s.id} $n")
                v.setTextColor(id, Moteur.cfg.couleurEffective(s).toInt())
                // Toucher une messagerie ouvre la liste filtrée sur elle
                v.setOnClickPendingIntent(id, PendingIntent.getActivity(ctx, 100 + i,
                    Intent(ctx, MainActivity::class.java).setAction("widget.${s.name}").putExtra("source", s.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            gm.updateAppWidget(ids, v)
        }
    }
}
