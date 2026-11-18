package com.killindodo.familytrack.uncle.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object UncleJobs {
    /** 15-min watchdog: restarts the service if an OEM killed it; drains the outbox. */
    fun scheduleAll(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ft_watchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    /** Quick retry after a failed send. */
    fun scheduleFlushNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ft_flush",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FlushWorker>()
                .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.MINUTES)
                .build(),
        )
    }
}

class WatchdogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!UncleService.running) UncleService.start(applicationContext)
        Outbox.flush(applicationContext)
        return Result.success()
    }
}

class FlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Outbox.flush(applicationContext)
        return Result.success()
    }
}
