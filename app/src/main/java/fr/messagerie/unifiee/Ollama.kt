package fr.messagerie.unifiee

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Résumés et réécritures avec Ollama, sur votre serveur (réseau local / VPN). Rien ne part sur Internet. */
class Ollama(private val cfg: Config) {

    companion object {
        const val INSTRUCTIONS = "Tu aides l'utilisateur, désigné par « Vous » dans les messages, à suivre ses messages. " +
            "On te donne les derniers messages d'une conversation, au format « [heure] Auteur : texte ». " +
            "Résume ce qui s'est dit, indique si « Vous » doit répondre ou agir, et propose une réponse si c'est le cas. " +
            "Dans le résumé, parle de l'utilisateur à la deuxième personne (« vous »). " +
            "Fais attention à qui a écrit chaque message (l'auteur est indiqué avant les deux-points). " +
            "Réponds toujours en français. N'invente rien qui ne soit pas dans les messages."

        fun transcription(messages: List<Message>, maxCaracteres: Int): String {
            val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
            val lignes = ArrayList<String>()
            var total = 0
            for (m in messages.asReversed()) {
                var t = m.texteUtile.replace(Regex("\\s*\\n\\s*"), " ")
                if (t.length > 400) t = t.take(400) + "…"
                val ligne = "[${fmt.format(Date(m.date))}] ${m.auteur} : $t"
                if (total + ligne.length > maxCaracteres && lignes.isNotEmpty()) break
                lignes.add(ligne); total += ligne.length + 1
            }
            return lignes.asReversed().joinToString("\n")
        }
    }

    enum class Facon(val libelle: String, val consigne: String) {
        AMELIORER("Améliorer", "Améliore ce brouillon : plus clair, plus fluide et bien écrit, sans changer le sens ni le ton (tutoiement/vouvoiement)."),
        CORRIGER("Corriger les fautes", "Corrige uniquement l'orthographe, la grammaire et la ponctuation de ce brouillon, sans rien reformuler d'autre."),
        COURT("Plus court", "Raccourcis ce brouillon au strict nécessaire, sans perdre d'information importante."),
        FORMEL("Plus formel", "Réécris ce brouillon sur un ton plus formel et professionnel (vouvoiement)."),
        CHALEUREUX("Plus chaleureux", "Réécris ce brouillon sur un ton plus chaleureux et amical, sans en faire trop."),
    }

    private fun chat(systeme: String, prompt: String, schema: JSONObject): JSONObject? {
        val corps = JSONObject()
            .put("model", cfg.ollamaModele).put("stream", false).put("think", false)
            .put("format", schema)
            .put("options", JSONObject().put("temperature", 0.2))
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systeme))
                .put(JSONObject().put("role", "user").put("content", prompt)))
            .toString()
        for (a in Config.liste(cfg.ollamaUrls)) {
            try {
                val r = Http.requete("$a/api/chat", "POST", null, corps, 120_000)
                if (r.code != 200) continue
                val contenu = JSONObject(r.texte).optJSONObject("message")?.optString("content") ?: continue
                return JSONObject(contenu)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun schema(vararg champs: Pair<String, String>) = JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().apply { champs.forEach { (n, t) -> put(n, JSONObject().put("type", t)) } })
        .put("required", JSONArray(champs.map { it.first }))

    private fun decrire(c: Conversation) = if (c.estGroupe) "groupe « ${c.nom} »" else "discussion privée avec ${c.nom}"

    /** Anti-spam : l'IA juge si les messages d'un expéditeur inconnu sont une arnaque ; null si Ollama est injoignable. */
    fun estArnaque(c: Conversation): Boolean? {
        val r = chat("Tu détectes les messages indésirables reçus par SMS ou messagerie : spam, hameçonnage, fausses livraisons de colis, " +
            "fausses banques, amendes, CPF, remboursements, faux « nouveau numéro » d'un proche. Les vrais messages (livreur réel, rendez-vous, " +
            "proche, professionnel) ne sont pas du spam. Réponds en JSON.",
            "Expéditeur : ${c.nom} (${c.source.libelle}), à qui l'utilisateur n'a jamais écrit.\nMessages reçus :\n" +
                transcription(c.messages.filter { !it.deMoi }.takeLast(5), 1500),
            schema("spam" to "boolean", "raison" to "string")) ?: return null
        return r.optBoolean("spam")
    }

    /** Résumé d'une conversation : null si Ollama est injoignable. */
    fun synthetiser(c: Conversation, messages: List<Message>): Synthese? {
        val prompt = "Conversation ${c.source.id} (${decrire(c)}) :\n\n" + transcription(messages, 8000) +
            "\n\nRéponds en JSON : resume (1 à 3 phrases), actionRequise (booléen), reponse (si une réponse est attendue : " +
            "le texte exact du message que « Vous » enverrait, à la première personne, court, au ton de la conversation ; sinon chaîne vide)."
        val j = chat(INSTRUCTIONS, prompt, schema("resume" to "string", "actionRequise" to "boolean", "reponse" to "string")) ?: return null
        val action = j.optBoolean("actionRequise")
        return Synthese(j.optString("resume"), action, if (action) j.optString("reponse") else "", c.derniere)
    }

    /** Réécrit le brouillon de réponse selon la consigne choisie. */
    fun ameliorer(brouillon: String, facon: Facon, c: Conversation, messages: List<Message>): String? {
        val systeme = "Tu réécris les messages que l'utilisateur s'apprête à envoyer. Garde la même langue que le brouillon, " +
            "la même personne (« je ») et le même destinataire. N'ajoute aucune information absente du brouillon. Rends uniquement le texte final."
        val prompt = "${facon.consigne}\n\nContexte (derniers messages de la conversation ${c.source.id} avec ${c.nom}) :\n" +
            transcription(messages, 1500) + "\n\nBROUILLON :\n$brouillon"
        return chat(systeme, prompt, schema("texte" to "string"))?.optString("texte")?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Trois réponses courtes et différentes que « Vous » pourrait envoyer (oui / non / plus tard…). */
    fun brouillons(c: Conversation, messages: List<Message>): List<String>? {
        val prompt = "Conversation ${c.source.id} (${decrire(c)}) :\n\n" + transcription(messages, 4000) +
            "\n\nPropose 3 réponses que « Vous » pourrait envoyer maintenant, courtes (une ou deux phrases), à la première personne, " +
            "au ton de la conversation (tutoiement ou vouvoiement comme dans les messages), et différentes entre elles " +
            "(par exemple : accepter, refuser ou décaler, demander une précision). Réponds en JSON : reponses (liste de textes)."
        val schema = JSONObject().put("type", "object")
            .put("properties", JSONObject().put("reponses", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
            .put("required", JSONArray().put("reponses"))
        val j = chat(INSTRUCTIONS, prompt, schema) ?: return null
        val a = j.optJSONArray("reponses") ?: return null
        return (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }.distinct().take(4)
    }

    /** Vérifie qu'Ollama répond et que le modèle est installé. */
    fun tester(): String {
        val adresses = Config.liste(cfg.ollamaUrls)
        if (adresses.isEmpty()) return "Aucune adresse Ollama saisie."
        for (a in adresses) {
            try {
                val r = Http.requete("$a/api/tags", delaiMs = 5_000)
                if (r.code != 200) continue
                val noms = JSONObject(r.texte).optJSONArray("models")?.objets()?.map { it.optString("name") } ?: emptyList()
                return if (cfg.ollamaModele in noms) "✅ Connecté · modèle ${cfg.ollamaModele} disponible"
                else "⚠️ Ollama répond mais le modèle ${cfg.ollamaModele} n'est pas installé. Modèles : ${noms.joinToString(", ")}"
            } catch (_: Exception) {}
        }
        return "❌ Ollama injoignable"
    }
}
