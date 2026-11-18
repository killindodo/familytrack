package com.killindodo.familytrack.uncle.service

import android.content.Context
import com.killindodo.familytrack.core.sms.FtSender
import com.killindodo.familytrack.uncle.data.OutboxEntity
import com.killindodo.familytrack.uncle.data.UncleDb
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Sends FT1 messages; on failure, saves to the outbox and schedules a retry. */
object Sender {
    fun sendWithFallback(context: Context, to: String, body: String) {
        val app = context.applicationContext
        FtSender.send(app, to, body) { sent, _ ->
            if (!sent) {
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    UncleDb.get(app).dao().enqueue(
                        OutboxEntity(dest = to, body = body, createdAt = System.currentTimeMillis()),
                    )
                    UncleJobs.scheduleFlushNow(app)
                }
            }
        }
    }
}

/** Drains the outbox in order; stops at the first failure (retried later). */
object Outbox {
    suspend fun flush(app: Context): Int {
        val dao = UncleDb.get(app).dao()
        var sent = 0
        for (row in dao.outbox()) {
            val ok = suspendCancellableCoroutine { c ->
                FtSender.send(app, row.dest, row.body) { s, _ -> c.resume(s) }
            }
            if (ok) {
                dao.removeOutbox(row.id)
                sent++
            } else {
                break
            }
        }
        return sent
    }
}
