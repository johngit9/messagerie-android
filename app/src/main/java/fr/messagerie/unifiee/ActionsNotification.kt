package fr.messagerie.unifiee

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Boutons des notifications de messages, comme WhatsApp : Répondre, Marquer comme lu, Silence.
 * Lu = local (accusé privé pour Element, comme dans l'app) ; Répondre n'envoie que le texte tapé par l'utilisateur.
 */
class ActionsNotification : BroadcastReceiver() {
    companion object {
        private const val REPONDRE = "fr.messagerie.unifiee.REPONDRE"
        private const val LU = "fr.messagerie.unifiee.LU"
        private const val SILENCE = "fr.messagerie.unifiee.SILENCE"
        private const val CLE_TEXTE = "texte"
        private val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private fun intention(ctx: Context, action: String, c: Conversation, mutable: Boolean = false): PendingIntent {
            val i = Intent(ctx, ActionsNotification::class.java).setAction(action).putExtra("cle", c.cle)
            val drapeaux = PendingIntent.FLAG_UPDATE_CURRENT or (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
            return PendingIntent.getBroadcast(ctx, (action + c.cle).hashCode(), i, drapeaux)
        }

        /** Ajoute les trois boutons (Répondre absent pour Teams, en lecture seule). */
        fun ajouter(ctx: Context, b: NotificationCompat.Builder, c: Conversation) {
            if (c.source != Source.TEAMS) {
                val saisie = RemoteInput.Builder(CLE_TEXTE).setLabel("Répondre à ${c.nom}").build()
                b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_bulle, "Répondre", intention(ctx, REPONDRE, c, mutable = true))
                    .addRemoteInput(saisie).setAllowGeneratedReplies(false)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).setShowsUserInterface(false).build())
            }
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_bulle, "Marquer comme lu", intention(ctx, LU, c))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build())
            b.addAction(NotificationCompat.Action.Builder(R.drawable.ic_bulle, "Silence", intention(ctx, SILENCE, c))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE).setShowsUserInterface(false).build())
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val cle = intent.getStringExtra("cle") ?: return
        val texte = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CLE_TEXTE)?.toString()
        val fin = goAsync()
        portee.launch {
            try {
                // Appli relancée par le bouton : on attend que les conversations soient chargées
                var c: Conversation? = null
                repeat(40) { if (c == null) { c = Moteur.etat.value.conversations.firstOrNull { it.cle == cle }; if (c == null) delay(250) } }
                val conv = c
                val nm = NotificationManagerCompat.from(ctx)
                if (conv == null) { nm.cancel(cle.hashCode()); return@launch }
                when (intent.action) {
                    LU -> Moteur.marquerLue(conv)
                    SILENCE -> { if (!Moteur.etat.value.etat(conv).sourdine) Moteur.basculerSourdine(conv); nm.cancel(cle.hashCode()) }
                    REPONDRE -> {
                        if (texte.isNullOrBlank()) { nm.cancel(cle.hashCode()); return@launch }
                        val erreur = Moteur.envoyer(texte, conv, null)
                        if (erreur == null) Moteur.marquerLue(conv) else Moteur.echecReponse(conv, erreur)
                    }
                }
            } finally { fin.finish() }
        }
    }
}
