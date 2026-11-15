package fr.messagerie.unifiee

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Chiffre des non-lus sur l'icône de l'appli.
 * Android standard n'affiche qu'un point ; plusieurs lanceurs (OPPO / OnePlus / realme, Samsung, Huawei, Sony…)
 * acceptent un nombre par leur propre interface, comme le fait WhatsApp. Chaque essai est sans effet ailleurs.
 */
object Pastille {
    private var dernier = -1

    fun afficher(ctx: Context, n: Int) {
        if (n == dernier) return
        dernier = n
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
