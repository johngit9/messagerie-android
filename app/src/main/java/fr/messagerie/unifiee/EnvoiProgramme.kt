package fr.messagerie.unifiee

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Message programmé : vous l'avez écrit et avez choisi l'heure ; l'appli l'envoie elle-même à cette heure
 * (WorkManager, réseau requis, même appli fermée). Annulable jusqu'au départ.
 */
class EnvoiProgramme(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Moteur.init(applicationContext)
        val id = inputData.getString("id") ?: return Result.success()
        val p = Moteur.archive.programmes().firstOrNull { it.id == id } ?: return Result.success()   // annulé entre-temps
        val c = Moteur.etat.value.conversations.firstOrNull { it.cle == p.conv } ?: Moteur.archive.conversation(p.conv)
        if (c == null) { Moteur.archive.retirerProgramme(id); return Result.success() }
        val cite = p.cite?.let { Moteur.archive.messageParCle(it) }
        val err = Moteur.envoyer(p.texte, c, cite)
        if (err != null && runAttemptCount < 3) return Result.retry()
        Moteur.archive.retirerProgramme(id)
        Moteur.programmesChanges()
        if (err != null) prevenir(c, err)
        return Result.success()
    }

    private fun prevenir(c: Conversation, err: String) {
        runCatching {
            val n = NotificationCompat.Builder(applicationContext, Moteur.CANAL)
                .setSmallIcon(R.drawable.ic_bulle)
                .setContentTitle("Message programmé non envoyé")
                .setContentText("${c.nom} : $err")
                .setAutoCancel(true).build()
            NotificationManagerCompat.from(applicationContext).notify(("prog" + c.cle).hashCode(), n)
        }
    }

    companion object {
        fun programmer(ctx: Context, id: String, quand: Long) {
            val req = OneTimeWorkRequestBuilder<EnvoiProgramme>()
                .setInitialDelay(maxOf(0L, quand - System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("id" to id))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("programme-$id", ExistingWorkPolicy.REPLACE, req)
        }

        fun annuler(ctx: Context, id: String) = WorkManager.getInstance(ctx).cancelUniqueWork("programme-$id")
    }
}
