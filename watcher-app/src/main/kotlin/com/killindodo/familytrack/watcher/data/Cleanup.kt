package com.killindodo.familytrack.watcher.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Deletes history older than 30 days (and finished checks older than 7). */
object Cleanup {
    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "ft_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}

class CleanupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val dao = WatcherDb.get(applicationContext).dao()
        val now = System.currentTimeMillis()
        dao.deleteOldFixes(now - 30L * 24 * 60 * 60 * 1000)
        dao.deleteOldChecks(now - 7L * 24 * 60 * 60 * 1000)
        return Result.success()
    }
}
