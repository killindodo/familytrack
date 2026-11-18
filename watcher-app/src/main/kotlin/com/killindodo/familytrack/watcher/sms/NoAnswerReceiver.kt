package com.killindodo.familytrack.watcher.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.killindodo.familytrack.watcher.data.Notifier
import com.killindodo.familytrack.watcher.data.WatcherDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires 5 minutes after a check if the uncle never answered. */
class NoAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msgId = intent.getLongExtra("msgId", -1L)
        val uncleId = intent.getLongExtra("uncleId", -1L)
        if (msgId == -1L) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = WatcherDb.get(app).dao()
                val row = dao.check(msgId)
                if (row != null && row.answeredAt == null) {
                    val uncle = dao.uncle(uncleId)
                    Notifier.noAnswer(app, uncle?.name ?: "Uncle")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
