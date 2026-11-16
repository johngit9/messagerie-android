package fr.messagerie.unifiee

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Synchro périodique appli fermée (WorkManager, toutes les 15 min environ, réseau requis) :
 * lit les nouveaux messages et prévient si une réponse est attendue. Rien n'est envoyé.
 */
class SynchroArrierePlan(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Moteur.init(applicationContext)
        if (!Moteur.cfg.configure) return Result.success()
        return try {
            withTimeoutOrNull(8 * 60_000L) {
                Moteur.actualiser()
                Moteur.attendreResumes()
                Surveillance.verifier()
            }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    companion object {
        private const val NOM = "synchro"

        /** Programme ou annule la synchro selon les réglages. */
        fun programmer(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            val cfg = Moteur.cfg
            if (cfg.configure && cfg.synchroArrierePlan) {
                val req = PeriodicWorkRequestBuilder<SynchroArrierePlan>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                wm.enqueueUniquePeriodicWork(NOM, ExistingPeriodicWorkPolicy.KEEP, req)
            } else wm.cancelUniqueWork(NOM)
        }
    }
}
