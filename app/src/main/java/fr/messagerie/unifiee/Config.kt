package fr.messagerie.unifiee

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Réglages et identifiants de connexion, saisis dans l'appli (écran Réglages).
 * Rien n'est codé en dur : aucune adresse ni aucun jeton n'est livré avec l'appli.
 * Stockés dans les préférences privées de l'appli (non sauvegardées : allowBackup=false).
 */
class Config(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("config", Context.MODE_PRIVATE)

    private fun s(k: String) = p.getString(k, "")!!.trim()
    private fun set(k: String, v: String) = p.edit().putString(k, v.trim()).apply()

    // Serveur Matrix (Synapse) et passerelles mautrix
    var matrixUrls: String get() = s("matrix.urls"); set(v) = set("matrix.urls", v)   // une ou plusieurs adresses, séparées par des virgules
    var matrixUtilisateur: String get() = s("matrix.user"); set(v) = set("matrix.user", v)
    var matrixJeton: String get() = s("matrix.jeton"); set(v) = set("matrix.jeton", v)
    var moiFantome: String get() = s("matrix.moi"); set(v) = set("matrix.moi", v)

    // Relais Element
    var relaisUrls: String get() = s("relais.urls"); set(v) = set("relais.urls", v)
    var relaisJeton: String get() = s("relais.jeton"); set(v) = set("relais.jeton", v)

    // Service Teams (lecture seule), même jeton que le relais
    var teamsUrls: String get() = s("teams.urls"); set(v) = set("teams.urls", v)
    var afficherTeams: Boolean get() = p.getBoolean("teams", true); set(v) = p.edit().putBoolean("teams", v).apply()
    /** Adresses du service Teams : celles saisies, sinon les hôtes du relais sur le port 8789. */
    val teamsAdresses: List<String>
        get() = liste(teamsUrls).ifEmpty {
            liste(relaisUrls).mapNotNull { u -> runCatching { val x = java.net.URI(u); "${x.scheme}://${x.host}:8789" }.getOrNull() }.distinct()
        }

    // Ollama
    var ollamaUrls: String get() = s("ollama.urls"); set(v) = set("ollama.urls", v)
    var ollamaModele: String get() = s("ollama.modele").ifEmpty { "qwen3:8b" }; set(v) = set("ollama.modele", v)

    // Préférences
    var jours: Int get() = p.getInt("jours", 3); set(v) = p.edit().putInt("jours", v).apply()
    var intervalle: Int get() = p.getInt("intervalle", 60); set(v) = p.edit().putInt("intervalle", v).apply()
    var notifications: Boolean get() = p.getBoolean("notifications", true); set(v) = p.edit().putBoolean("notifications", v).apply()
    /** Transmettre vos émoticônes aux contacts (sinon elles restent sur ce téléphone). */
    var envoyerReactions: Boolean get() = p.getBoolean("reactions", true); set(v) = p.edit().putBoolean("reactions", v).apply()
    var veilleArrierePlan: Boolean get() = p.getBoolean("veille", false); set(v) = p.edit().putBoolean("veille", v).apply()

    // Affichage : échelle de toute l'interface et de la taille du texte (en %)
    var zoom: Int get() = p.getInt("zoom", 100); set(v) = p.edit().putInt("zoom", v).apply()
    var police: Int get() = p.getInt("police", 0); set(v) = p.edit().putInt("police", v).apply()
    var tailleTexte: Int get() = p.getInt("tailleTexte", 100); set(v) = p.edit().putInt("tailleTexte", v).apply()
    var synchroArrierePlan: Boolean get() = p.getBoolean("synchroFond", true); set(v) = p.edit().putBoolean("synchroFond", v).apply()
    var notifTousMessages: Boolean get() = p.getBoolean("notifTous", true); set(v) = p.edit().putBoolean("notifTous", v).apply()

    /** Notifications par messagerie : "tous" (chaque message), "attendue" (réponse attendue), "aucun". */
    fun notif(s: Source): String = p.getString("notif.${s.id}", "tous")!!
    fun definirNotif(s: Source, v: String) = p.edit().putString("notif.${s.id}", v).apply()

    /** Messagerie rangée en « Pro » par défaut (Element et LinkedIn). */
    /** Anti-spam local (activé par défaut) et avis de l'IA pour les cas douteux. */
    var antiSpam: Boolean get() = p.getBoolean("antiSpam", true); set(v) = p.edit().putBoolean("antiSpam", v).apply()
    var spamIA: Boolean get() = p.getBoolean("spamIA", false); set(v) = p.edit().putBoolean("spamIA", v).apply()
    /** Appel reçu sur une messagerie : ouvrir l'appli officielle pour décrocher (Messagerie ne gère pas les appels). */
    var appelsOfficiels: Boolean get() = p.getBoolean("appelsOfficiels", true); set(v) = p.edit().putBoolean("appelsOfficiels", v).apply()
    /** Mode vacances : aucune notification pour les messageries et conversations rangées en Pro. */
    var vacances: Boolean get() = p.getBoolean("vacances", false); set(v) = p.edit().putBoolean("vacances", v).apply()
    /** Date du dernier changement du mode vacances (ici ou sur un autre appareil), pour la synchro. */
    var vacancesDate: Long get() = p.getLong("vacancesDate", 0L); set(v) = p.edit().putLong("vacancesDate", v).apply()
    fun pro(s: Source): Boolean = p.getBoolean("pro.${s.id}", s == Source.ELEMENT || s == Source.LINKEDIN || s == Source.TEAMS)
    fun definirPro(s: Source, v: Boolean) = p.edit().putBoolean("pro.${s.id}", v).apply()

    private fun carte(k: String): Map<String, String> = try {
        val o = JSONObject(p.getString(k, "{}")!!); o.keys().asSequence().associateWith { o.optString(it) }
    } catch (_: Exception) { emptyMap() }
    private fun ecrireCarte(k: String, m: Map<String, String>) = p.edit().putString(k, JSONObject(m).toString()).apply()

    /** Classement manuel d'une conversation : "pro" ou "perso". */
    var categories: Map<String, String> get() = carte("categories"); set(v) = ecrireCarte("categories", v)
    /** Conversations archivées → date d'archivage. */
    var archivees: Map<String, Long> get() = carte("archivees").mapValues { it.value.toLongOrNull() ?: 0 }; set(v) = ecrireCarte("archivees", v.mapValues { it.value.toString() })
    /** Réponses rapides (modèles modifiables), insérées dans le champ : jamais envoyées sans votre appui. */
    var reponsesRapides: List<String>
        get() = try {
            p.getString("reponsesRapides", null)?.let { t -> org.json.JSONArray(t).let { a -> (0 until a.length()).map { a.getString(it) } } }
                ?: listOf("Merci, je regarde et je reviens vers toi.", "D'accord, ça marche !", "Je te rappelle dès que possible.",
                    "Je suis en réunion, je te réponds plus tard.", "Bien reçu, merci.")
        } catch (_: Exception) { emptyList() }
        set(v) = p.edit().putString("reponsesRapides", org.json.JSONArray(v).toString()).apply()
    /** Fiche contact : conversation → personne, choisi à la main (sinon, rapprochement par nom ou numéro). */
    var liensContacts: Map<String, String> get() = carte("liensContacts"); set(v) = ecrireCarte("liensContacts", v)
    /** Conversations épinglées, dans l'ordre. */

    var epinglees: List<String>
        get() = try { org.json.JSONArray(p.getString("epinglees", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (_: Exception) { emptyList() }
        set(v) = p.edit().putString("epinglees", org.json.JSONArray(v).toString()).apply()
    /** Thème : 0 = comme le téléphone, 1 = clair, 2 = sombre. */
    var theme: Int get() = p.getInt("theme", 0); set(v) = p.edit().putInt("theme", v).apply()
    /** Couleur principale de l'appli (0 = bleu d'origine). */
    var accent: Long get() = p.getLong("accent", 0L); set(v) = p.edit().putLong("accent", v).apply()
    /** Couleur choisie pour une messagerie (0 = couleur d'origine). */
    fun couleur(s: Source): Long = p.getLong("couleur.${s.id}", 0L)
    fun definirCouleur(s: Source, v: Long) = p.edit().putLong("couleur.${s.id}", v).apply()
    fun couleurEffective(s: Source): Long = couleur(s).takeIf { it != 0L } ?: s.couleur
    /** Heures de silence des notifications (-1 = désactivé), de debut à fin, en heures pleines. */
    var silenceDebut: Int get() = p.getInt("silenceDebut", -1); set(v) = p.edit().putInt("silenceDebut", v).apply()
    var silenceFin: Int get() = p.getInt("silenceFin", 7); set(v) = p.edit().putInt("silenceFin", v).apply()
    /** Messageries concernées par les heures de silence (toutes par défaut). */
    fun silencePour(s: Source): Boolean = p.getBoolean("silence.${s.id}", true)
    fun definirSilencePour(s: Source, v: Boolean) = p.edit().putBoolean("silence.${s.id}", v).apply()
    fun enSilence(s: Source): Boolean = silencePour(s) && enSilence()
    /** Présentation de la liste : 0 cartes, 1 liste compacte, 2 bulles (aérée). */
    /** Serveur Jitsi Meet pour les appels vidéo (meet.jit.si par défaut, ou votre propre serveur). */
    var jitsi: String get() = s("jitsi").ifEmpty { "https://meet.jit.si" }.trimEnd('/'); set(v) = set("jitsi", v)
    /** Votre statut de présence choisi : offline (par défaut, discret), unavailable (absent) ou online. */
    var presence: String get() = s("presence").ifEmpty { "offline" }; set(v) = set("presence", v)
    /** Statut montré sur Element (relais), indépendant du compte Matrix ; reprend l'ancien statut commun s'il n'a jamais été choisi. */
    var presenceElement: String get() = s("presenceElement").ifEmpty { presence }; set(v) = set("presenceElement", v)
    var design: Int get() = p.getInt("design", 0); set(v) = p.edit().putInt("design", v).apply()
    /** Haut de la liste : 0 classique, 1 Bandeau, 2 Épuré. */
    /** Barre des messageries en bas de la liste. */
    var barreBas: Boolean get() = p.getBoolean("barreBas", true); set(v) = p.edit().putBoolean("barreBas", v).apply()
    var haut: Int get() = p.getInt("haut", 1); set(v) = p.edit().putInt("haut", v).apply()
    /** Couleur du bandeau de l'écran d'accueil (0 = bleu nuit d'origine). */
    var couleurBandeau: Long get() = p.getLong("couleurBandeau", 0L); set(v) = p.edit().putLong("couleurBandeau", v).apply()
    /** SMS lus dans Messagerie aussi lus dans Google Messages (désactivé par défaut, choix de l'utilisateur). */
    var lusSmsGoogle: Boolean get() = p.getBoolean("lusSmsGoogle", false); set(v) = p.edit().putBoolean("lusSmsGoogle", v).apply()
    /** Temps réel économe : en arrière-plan, attente plus longue et accusés ignorés (les messages arrivent toujours tout de suite). */
    var economie: Boolean get() = p.getBoolean("economie", true); set(v) = p.edit().putBoolean("economie", v).apply()
    /** Une seule fois (v1.85) : réception en temps réel activée, à la demande de l'utilisateur. */
    var tempsReelActive: Boolean get() = p.getBoolean("tempsReelActive", false); set(v) = p.edit().putBoolean("tempsReelActive", v).apply()
    var contactsDemandes: Boolean get() = p.getBoolean("contactsDemandes", false); set(v) = p.edit().putBoolean("contactsDemandes", v).apply()
    // Style des notifications (comme les applis officielles)
    var notifBulles: Boolean get() = p.getBoolean("notifBulles", true); set(v) = p.edit().putBoolean("notifBulles", v).apply()
    var notifBoutons: Boolean get() = p.getBoolean("notifBoutons", true); set(v) = p.edit().putBoolean("notifBoutons", v).apply()
    var notifPhoto: Boolean get() = p.getBoolean("notifPhoto", true); set(v) = p.edit().putBoolean("notifPhoto", v).apply()
    /** Contenu affiché : 0 le message, 1 « Nouveau message » seulement. */
    var notifApercu: Int get() = p.getInt("notifApercu", 0); set(v) = p.edit().putInt("notifApercu", v).apply()
    /** Écran verrouillé : 0 tout afficher, 1 masquer le contenu, 2 ne rien afficher. */
    var notifVerrouille: Int get() = p.getInt("notifVerrouille", 0); set(v) = p.edit().putInt("notifVerrouille", v).apply()
    fun enSilence(heure: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): Boolean {
        val d = silenceDebut; val f = silenceFin
        if (d < 0 || d == f) return false
        return if (d < f) heure in d until f else heure >= d || heure < f
    }
    var couleursDynamiques: Boolean get() = p.getBoolean("dynamique", false); set(v) = p.edit().putBoolean("dynamique", v).apply()
    var verifierMaj: Boolean get() = p.getBoolean("verifierMaj", true); set(v) = p.edit().putBoolean("verifierMaj", v).apply()

    // Historique des fichiers importés (gardé dans les données privées de l'appli)
    data class Import(val nom: String, val date: Long, val faits: String, val contenu: String)

    val imports: List<Import>
        get() = try {
            val a = org.json.JSONArray(p.getString("imports", "[]"))
            (0 until a.length()).map { i -> a.getJSONObject(i).let {
                Import(it.optString("nom"), it.optLong("date"), it.optString("faits"), it.optString("contenu")) } }
        } catch (_: Exception) { emptyList() }

    fun memoriserImport(nom: String, faits: List<String>, contenu: String) {
        val l = listOf(Import(nom, System.currentTimeMillis(), faits.joinToString(", "), contenu)) +
            imports.filter { it.contenu != contenu }
        enregistrerImports(l.take(10))
    }

    fun oublierImport(i: Import) = enregistrerImports(imports.filter { it.date != i.date })

    private fun enregistrerImports(l: List<Import>) {
        val a = org.json.JSONArray()
        l.forEach { a.put(JSONObject().put("nom", it.nom).put("date", it.date).put("faits", it.faits).put("contenu", it.contenu)) }
        p.edit().putString("imports", a.toString()).apply()
    }

    /** Les messages plus anciens comptent comme lus (fixé au premier lancement : maintenant − 24 h). */
    val debutLecture: Long
        get() {
            if (!p.contains("debutLecture")) p.edit().putLong("debutLecture", System.currentTimeMillis() - 86_400_000).apply()
            return p.getLong("debutLecture", 0)
        }

    val matrixConfigure get() = matrixUrls.isNotEmpty() && matrixJeton.isNotEmpty() && matrixUtilisateur.isNotEmpty()
    val relaisConfigure get() = relaisUrls.isNotEmpty() && relaisJeton.isNotEmpty()
    val ollamaConfigure get() = ollamaUrls.isNotEmpty()
    val teamsConfigure get() = afficherTeams && relaisJeton.isNotEmpty() && teamsAdresses.isNotEmpty()
    val configure get() = matrixConfigure || relaisConfigure

    /** Exporte tous les réglages dans le format du fichier de configuration (voir messagerie-config.exemple.json). */
    fun exporterJson(): String = JSONObject()
        .put("matrix", JSONObject().put("adresses", matrixUrls).put("utilisateur", matrixUtilisateur).put("jeton", matrixJeton).put("moi", moiFantome))
        .put("relais", JSONObject().put("adresses", relaisUrls).put("jeton", relaisJeton))
        .put("teams", JSONObject().put("adresses", teamsUrls))
        .put("ollama", JSONObject().put("adresses", ollamaUrls).put("modele", ollamaModele))
        .put("jours", jours).put("intervalle", intervalle).put("notifications", notifications).put("veille", veilleArrierePlan).put("envoyerReactions", envoyerReactions)
        .put("zoom", zoom).put("tailleTexte", tailleTexte).put("police", police)
        .toString(2)

    /**
     * Importe un fichier de configuration. Accepte :
     *  - le format de l'appli (messagerie-config.exemple.json) ;
     *  - le client.json de l'app Mac (homeserver_url…, user_id, access_token, moi_fantome) ;
     *  - le element.json / relais.json du relais (url, url_ip, url_tailscale, jeton).
     * Seules les valeurs présentes dans le fichier remplacent les réglages actuels.
     * Renvoie la liste de ce qui a été importé.
     */
    fun importerJson(texte: String): List<String> {
        val j = JSONObject(texte)
        val faits = mutableListOf<String>()
        fun JSONObject.txt(k: String): String? = if (has(k) && !isNull(k)) optString(k).trim().takeIf { it.isNotEmpty() } else null
        fun adresses(o: JSONObject, vararg cles: String): String? {
            val l = cles.flatMap { k ->
                if (!o.has(k) || o.isNull(k)) emptyList()
                else o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: liste(o.optString(k))
            }.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }.distinct()
            return l.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }

        j.optJSONObject("matrix")?.let { m ->
            adresses(m, "adresses")?.let { matrixUrls = it }
            m.txt("utilisateur")?.let { matrixUtilisateur = it }
            m.txt("jeton")?.let { matrixJeton = it }
            m.txt("moi")?.let { moiFantome = it }
            faits += "serveur Matrix"
        }
        j.optJSONObject("relais")?.let { r ->
            adresses(r, "adresses")?.let { relaisUrls = it }
            r.txt("jeton")?.let { relaisJeton = it }
            faits += "relais Element"
        }
        j.optJSONObject("teams")?.let { t ->
            adresses(t, "adresses")?.let { teamsUrls = it }
            faits += "Teams"
        }
        j.optJSONObject("ollama")?.let { o ->
            adresses(o, "adresses")?.let { ollamaUrls = it }
            o.txt("modele")?.let { ollamaModele = it }
            faits += "Ollama"
        }
        // client.json de l'app Mac
        if (j.has("access_token") || j.has("homeserver_url")) {
            adresses(j, "homeserver_url_tailscale", "homeserver_url_lan", "homeserver_url", "homeserver_url_ip")?.let { matrixUrls = it }
            j.txt("user_id")?.let { matrixUtilisateur = it }
            j.txt("access_token")?.let { matrixJeton = it }
            j.txt("moi_fantome")?.let { moiFantome = it }
            faits += "serveur Matrix"
        }
        // element.json / relais.json du relais
        if (j.has("jeton") && (j.has("url") || j.has("url_tailscale"))) {
            adresses(j, "url_tailscale", "url", "url_ip")?.let { relaisUrls = it }
            j.txt("jeton")?.let { relaisJeton = it }
            faits += "relais Element"
        }
        if (j.has("jours")) { jours = j.optInt("jours", jours); faits += "préférences" }
        if (j.has("intervalle")) intervalle = j.optInt("intervalle", intervalle)
        if (j.has("notifications")) notifications = j.optBoolean("notifications", notifications)
        if (j.has("envoyerReactions")) envoyerReactions = j.optBoolean("envoyerReactions", envoyerReactions)
        if (j.has("zoom")) zoom = j.optInt("zoom", zoom)
        if (j.has("tailleTexte")) tailleTexte = j.optInt("tailleTexte", tailleTexte)
        if (j.has("police")) police = j.optInt("police", police)
        if (j.has("veille")) veilleArrierePlan = j.optBoolean("veille", veilleArrierePlan)
        return faits.distinct()
    }

    companion object {
        fun liste(s: String) = s.split(',', ' ', '\n').map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
    }
}
