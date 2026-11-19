package fr.messagerie.unifiee

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Fond de l'icône de l'appli : une entrée d'icône (activity-alias du manifeste) par fond, une seule activée.
 * Le lanceur peut mettre quelques secondes à afficher la nouvelle icône.
 */
object IconeAppli {
    class Fond(val id: String, val nom: String, val haut: Long, val bas: Long)

    val fonds = listOf(
        Fond("Indigo", "Indigo", 0xFF5C74F2, 0xFF2B3BAE),
        Fond("Nuit", "Nuit", 0xFF2A3045, 0xFF07080D),
        Fond("Blanc", "Blanc", 0xFFFFFFFF, 0xFFDCE2F0),
        Fond("Aurore", "Aurore", 0xFF8A63FF, 0xFFE0509A),
        Fond("Ocean", "Océan", 0xFF22C3D6, 0xFF1257B8),
        Fond("Corail", "Corail", 0xFFFF9A6B, 0xFFE5484D),
    )

    private fun composant(ctx: Context, id: String) = ComponentName(ctx, "fr.messagerie.unifiee.Icone$id")

    private fun active(ctx: Context, f: Fond): Boolean =
        when (ctx.packageManager.getComponentEnabledSetting(composant(ctx, f.id))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> f.id == "Indigo"   // valeur du manifeste
            else -> false
        }

    fun actuel(ctx: Context): String = fonds.firstOrNull { active(ctx, it) }?.id ?: "Indigo"

    /** Active la nouvelle icône d'abord (jamais zéro icône), puis désactive les autres. */
    fun choisir(ctx: Context, id: String) {
        val pm = ctx.packageManager
        runCatching {
            pm.setComponentEnabledSetting(composant(ctx, id), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            fonds.filter { it.id != id }.forEach {
                pm.setComponentEnabledSetting(composant(ctx, it.id), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
        // Les raccourcis (appui long, bulles) suivent l'ancienne entrée et disparaissent avec elle : on les republie
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ Raccourcis.republier(ctx.applicationContext) }, 1500)
    }
}
