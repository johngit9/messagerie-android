package fr.messagerie.unifiee

import android.content.Context
import org.json.JSONObject
import kotlin.math.ln

/**
 * Anti-spam local : score de risque de 0 à 100 pour les conversations d'expéditeurs inconnus.
 * Indices (liens raccourcis ou douteux, faux colis / banque / amende, urgence), ce que vous avez appris
 * à l'appli avec « Spam » / « Pas spam », et en option l'avis de l'IA (Ollama, sur votre serveur).
 * Tout reste sur le téléphone : rien n'est signalé ni envoyé aux messageries, aucun accusé de lecture.
 */
object Spam {
    const val SEUIL = 60
    class Verdict(val score: Int, val raisons: List<String>, val manuel: Boolean = false) {
        val estSpam get() = score >= SEUIL
        val douteux get() = score in 30 until SEUIL
    }

    private lateinit var p: android.content.SharedPreferences
    fun init(ctx: Context) { p = ctx.getSharedPreferences("antispam", Context.MODE_PRIVATE); charger() }

    /** Verdicts posés par vous : clé de conversation → true (spam) / false (pas spam). */
    private val manuels = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    /** Avis de l'IA par « clé@date du dernier message ». */
    private val ia = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    /** Apprentissage : nombre d'apparitions de chaque mot dans les messages marqués spam / pas spam. */
    private val motsSpam = HashMap<String, Int>(); private val motsOk = HashMap<String, Int>()
    private var docsSpam = 0; private var docsOk = 0

    private fun charger() = synchronized(this) {
        runCatching { JSONObject(p.getString("manuels", "{}")!!).let { j -> j.keys().forEach { manuels[it] = j.getBoolean(it) } } }
        runCatching { JSONObject(p.getString("ia", "{}")!!).let { j -> j.keys().forEach { ia[it] = j.getBoolean(it) } } }
        runCatching { JSONObject(p.getString("motsSpam", "{}")!!).let { j -> j.keys().forEach { motsSpam[it] = j.getInt(it) } } }
        runCatching { JSONObject(p.getString("motsOk", "{}")!!).let { j -> j.keys().forEach { motsOk[it] = j.getInt(it) } } }
        docsSpam = p.getInt("docsSpam", 0); docsOk = p.getInt("docsOk", 0)
    }

    private fun sauver() = synchronized(this) {
        // On garde les 3 000 mots les plus vus de chaque côté
        fun garder(m: Map<String, Int>) = JSONObject(m.entries.sortedByDescending { it.value }.take(3000).associate { it.key to it.value })
        p.edit().putString("manuels", JSONObject(manuels as Map<*, *>).toString())
            .putString("ia", JSONObject(ia.entries.toList().takeLast(500).associate { it.key to it.value }).toString())
            .putString("motsSpam", garder(motsSpam).toString()).putString("motsOk", garder(motsOk).toString())
            .putInt("docsSpam", docsSpam).putInt("docsOk", docsOk).apply()
    }

    fun oublier() = synchronized(this) {
        manuels.clear(); ia.clear(); motsSpam.clear(); motsOk.clear(); docsSpam = 0; docsOk = 0; sauver()
    }
    val nombreAppris get() = docsSpam + docsOk

    private val RACCOURCIS = setOf("bit.ly", "tinyurl.com", "t.co", "cutt.ly", "is.gd", "rb.gy", "shorturl.at", "ow.ly", "s.id", "tiny.cc",
        "urlz.fr", "goo.gl", "t.ly", "lnkd.in", "shorturl.com", "rebrand.ly", "v.gd", "qrco.de", "bit.do", "x.gd")
    private val EXTENSIONS_DOUTEUSES = setOf("xyz", "top", "click", "live", "online", "site", "icu", "buzz", "info", "shop", "store", "vip",
        "monster", "cyou", "rest", "sbs", "cfd", "lol", "work", "support", "help", "app", "link", "page")
    /** Marques souvent imitées → domaines officiels. */
    private val MARQUES = mapOf(
        "laposte" to listOf("laposte.fr", "laposte.net"), "colissimo" to listOf("colissimo.fr", "laposte.fr"),
        "chronopost" to listOf("chronopost.fr"), "dpd" to listOf("dpd.fr", "dpd.com"), "dhl" to listOf("dhl.com", "dhl.fr"),
        "ups" to listOf("ups.com"), "mondialrelay" to listOf("mondialrelay.fr"), "relais" to listOf("mondialrelay.fr"),
        "ameli" to listOf("ameli.fr"), "impots" to listOf("impots.gouv.fr"), "antai" to listOf("antai.gouv.fr"),
        "caf" to listOf("caf.fr"), "cpf" to listOf("moncompteformation.gouv.fr"), "netflix" to listOf("netflix.com"),
        "paypal" to listOf("paypal.com", "paypal.fr"), "amazon" to listOf("amazon.fr", "amazon.com"), "vinted" to listOf("vinted.fr"),
        "credit" to emptyList(), "banque" to emptyList(), "sg" to listOf("societegenerale.fr"), "bnp" to listOf("mabanque.bnpparibas"),
        "lcl" to listOf("lcl.fr"), "ca-" to listOf("credit-agricole.fr"), "orange" to listOf("orange.fr"), "sfr" to listOf("sfr.fr"),
        "free" to listOf("free.fr"), "bouygues" to listOf("bouyguestelecom.fr"), "edf" to listOf("edf.fr"), "engie" to listOf("engie.fr"),
        "crit" to listOf("critair.fr", "certificat-air.gouv.fr"), "ants" to listOf("ants.gouv.fr"),
    )
    private val THEMES = listOf(
        Regex("(?i)\\b(colis|livraison|livreur|frais de (port|livraison|douane)|reprogramm\\w*|adresse incompl[eè]te)") to "colis ou livraison",
        Regex("(?i)\\b(amende|antai|contravention|infraction|majoration)") to "amende",
        Regex("(?i)\\b(carte vitale|ameli|remboursement|trop[- ]per[çc]u|cpf|compte formation)") to "remboursement ou aide",
        Regex("(?i)\\b(compte (bloqu|suspendu|d[ée]sactiv)\\w*|activit[ée] (inhabituelle|suspecte)|opposition|virement|conseiller bancaire|banque)") to "compte bancaire",
        Regex("(?i)\\b(gagn[ée]|cadeau|f[ée]licitations|tirage au sort|lot|r[ée]compense|offert)") to "gain ou cadeau",
        Regex("(?i)\\b(code de (v[ée]rification|confirmation)|mot de passe|identifiant|mettre? [àa] jour vos (informations|coordonn[ée]es))") to "demande d'identifiants",
        Regex("(?i)\\b(crypto|bitcoin|investissement|rendement|trading|placement)") to "investissement",
        Regex("(?i)\\b(maman|papa)\\b.{0,40}\\b(nouveau num[ée]ro|t[ée]l[ée]phone cass[ée])") to "« nouveau numéro » d'un proche",
    )
    private val URGENCE = Regex("(?i)\\b(urgent|imm[ée]diatement|dernier (rappel|d[ée]lai|avis)|avant (minuit|ce soir|24 ?h|48 ?h)|sous (24|48) ?h|aujourd'hui seulement|expire|sinon)")
    private val LIEN = Regex("(?i)(?:https?://)?((?:[a-z0-9-]+\\.)+[a-z]{2,})(?:/[^\\s]*)?")

    private fun mots(t: String) = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}.-]{2,}").findAll(t.lowercase()).map { it.value.trimEnd('.') }.toSet()

    private fun cleIA(c: Conversation) = c.cle + "@" + c.derniere

    /** Score de risque d'une conversation. [connuCarnet] : l'expéditeur est dans le carnet d'adresses. */
    fun evaluer(c: Conversation, connuCarnet: Boolean): Verdict {
        manuels[c.cle]?.let { return if (it) Verdict(100, listOf("Marqué comme spam par vous"), true) else Verdict(0, emptyList(), true) }
        // Groupes, Teams, et toute conversation où vous avez déjà écrit : pas de spam
        if (c.estGroupe || c.source == Source.TEAMS || c.messages.any { it.deMoi }) return Verdict(0, emptyList())
        val recus = c.messages.filter { !it.deMoi }.takeLast(5)
        if (recus.isEmpty()) return Verdict(0, emptyList())
        val texte = recus.joinToString("\n") { it.texteUtile }
        var score = 0
        val raisons = ArrayList<String>()
        if (connuCarnet) score -= 40
        else { score += 15; raisons += "expéditeur inconnu" }

        val domaines = LIEN.findAll(texte).map { it.groupValues[1].lowercase().removePrefix("www.") }
            .filter { d -> d.substringAfterLast('.').length >= 2 && !d.substringAfterLast('.').all(Char::isDigit) && d.count { it == '.' } >= 1 }
            .filter { d -> Regex("[a-z]").containsMatchIn(d.substringAfterLast('.')) }.toList()
        if (domaines.isNotEmpty()) { score += 15; raisons += "contient un lien" }
        domaines.firstOrNull { it in RACCOURCIS }?.let { score += 20; raisons += "lien raccourci ($it) qui cache la vraie adresse" }
        domaines.firstOrNull { it.substringAfterLast('.') in EXTENSIONS_DOUTEUSES }?.let { score += 15; raisons += "adresse en .${it.substringAfterLast('.')}, souvent utilisée par les arnaques" }
        // Marque imitée : le nom apparaît dans l'adresse, mais ce n'est pas le site officiel
        for (d in domaines) {
            val imite = MARQUES.entries.firstOrNull { (marque, officiels) ->
                d.contains(marque) && officiels.none { o -> d == o || d.endsWith(".$o") }
            }
            if (imite != null) { score += 35; raisons += "« $d » imite ${imite.key.trimEnd('-')} sans être son site officiel"; break }
        }
        val themes = THEMES.filter { it.first.containsMatchIn(texte) }.map { it.second }
        if (themes.isNotEmpty()) { score += minOf(30, 15 * themes.size); raisons += "sujet typique des arnaques : " + themes.joinToString(", ") }
        if (URGENCE.containsMatchIn(texte)) { score += 15; raisons += "pousse à agir vite" }
        if (domaines.isNotEmpty() && themes.isNotEmpty()) score += 10   // lien + sujet sensible : combinaison classique

        // Ce que vous avez appris à l'appli
        if (docsSpam > 0 && docsOk > 0) synchronized(this) {
            val ts = motsSpam.values.sum().toDouble(); val to = motsOk.values.sum().toDouble()
            var lo = ln(docsSpam.toDouble() / docsOk)
            mots(texte).forEach { w -> lo += ln(((motsSpam[w] ?: 0) + 1) / (ts + 1000)) - ln(((motsOk[w] ?: 0) + 1) / (to + 1000)) }
            val apport = (lo * 4).toInt().coerceIn(-35, 35)
            if (apport >= 10) raisons += "ressemble aux messages que vous avez marqués comme spam"
            score += apport
        }
        // Avis de l'IA (cas douteux seulement)
        ia[cleIA(c)]?.let { if (it) { score += 30; raisons += "l'IA y voit une arnaque probable" } else score -= 25 }
        return Verdict(score.coerceIn(0, 100), raisons)
    }

    /** « Spam » / « Pas spam » : verdict pour cette conversation, et l'appli apprend de ses messages. */
    fun marquer(c: Conversation, spam: Boolean) {
        val avant = manuels[c.cle]
        manuels[c.cle] = spam
        synchronized(this) {
            val ws = mots(c.messages.filter { !it.deMoi }.takeLast(5).joinToString("\n") { it.texteUtile })
            // Changement d'avis : on retire d'abord ce qui avait été appris
            if (avant != null && avant != spam) {
                val m = if (avant) motsSpam else motsOk
                ws.forEach { w -> m[w]?.let { n -> if (n <= 1) m.remove(w) else m[w] = n - 1 } }
                if (avant) docsSpam = maxOf(0, docsSpam - 1) else docsOk = maxOf(0, docsOk - 1)
            }
            if (avant != spam) {
                val m = if (spam) motsSpam else motsOk
                ws.forEach { w -> m[w] = (m[w] ?: 0) + 1 }
                if (spam) docsSpam++ else docsOk++
            }
        }
        sauver()
    }

    fun iaAVerifier(c: Conversation, v: Verdict) = !v.manuel && v.douteux && !ia.containsKey(cleIA(c))
    fun noterIA(c: Conversation, spam: Boolean) { ia[cleIA(c)] = spam; sauver() }
}
