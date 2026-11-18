package com.killindodo.familytrack.watcher.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules / cancels the +5 min "did not respond" exact alarms. */
object AlarmHelper {
    private const val DELAY_MS = 5 * 60_000L

    private fun pi(ctx: Context, msgId: Long, uncleId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            (msgId and 0x7FFFFFFF).toInt(),
            Intent(ctx, NoAnswerReceiver::class.java)
                .putExtra("msgId", msgId)
                .putExtra("uncleId", uncleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(ctx: Context, msgId: Long, uncleId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val trigger = System.currentTimeMillis() + DELAY_MS
        val pending = pi(ctx, msgId, uncleId)
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactOk) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun cancel(ctx: Context, msgId: Long, uncleId: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, msgId, uncleId))
    }
}
