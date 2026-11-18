package com.killindodo.familytrack.watcher.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.killindodo.familytrack.watcher.data.WatcherDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms die on reboot — re-arm them for any check still unanswered. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = WatcherDb.get(app).dao()
                dao.unansweredChecks().forEach { c ->
                    AlarmHelper.schedule(app, c.msgId, c.uncleId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
