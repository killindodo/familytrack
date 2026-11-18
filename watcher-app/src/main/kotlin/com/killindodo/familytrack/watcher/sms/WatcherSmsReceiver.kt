package com.killindodo.familytrack.watcher.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.killindodo.familytrack.core.FixSource
import com.killindodo.familytrack.core.FtMessage
import com.killindodo.familytrack.core.GeofDirection
import com.killindodo.familytrack.core.SmsProtocol
import com.killindodo.familytrack.watcher.data.FixEntity
import com.killindodo.familytrack.watcher.data.Notifier
import com.killindodo.familytrack.watcher.data.UncleEntity
import com.killindodo.familytrack.watcher.data.WatcherDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives FT1 messages from the uncles' phones. Persists everything even
 * when the app is closed (manifest receiver runs without the app), posts
 * alerts, and cancels the 5-minute timer when an answer arrives.
 */
class WatcherSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = msgs.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val from = msgs.firstOrNull()?.originatingAddress ?: return
        val msg = SmsProtocol.parse(body) ?: return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, from, msg)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: Context, from: String, m: FtMessage) {
        Notifier.ensureChannels(app)
        val dao = WatcherDb.get(app).dao()

        when (m) {
            is FtMessage.PairMsg -> {
                val code = dao.pairCode(m.code)
                if (code != null && code.usedAt == null) {
                    dao.markPairCodeUsed(m.code, System.currentTimeMillis())
                    val count = dao.uncleCount()
                    dao.insertUncle(
                        UncleEntity(
                            name = m.uncleName.ifBlank { "Uncle" },
                            number = from,
                            colorIndex = count % PALETTE.size,
                            pairedAt = System.currentTimeMillis(),
                        ),
                    )
                    Notifier.paired(app, m.uncleName.ifBlank { "Uncle" })
                }
                // Unknown/used code: silently ignore (wrong number typos).
            }

            is FtMessage.FixMsg -> {
                val uncle = dao.uncleByNumber(from) ?: return
                val check = dao.check(m.msgId)
                val source = if (check != null) FixSource.CHECK else FixSource.SOS
                storeFix(dao, uncle, m.lat, m.lng, m.accuracyM, m.speedMps, m.batteryPct, source)
                if (check != null && check.answeredAt == null) {
                    dao.markCheckAnswered(m.msgId, System.currentTimeMillis())
                    AlarmHelper.cancel(app, m.msgId, uncle.id)
                }
            }

            is FtMessage.NoFixMsg -> {
                val uncle = dao.uncleByNumber(from)
                if (uncle != null) {
                    val check = dao.check(m.msgId)
                    if (check != null && check.answeredAt == null) {
                        dao.markCheckAnswered(m.msgId, System.currentTimeMillis())
                        AlarmHelper.cancel(app, m.msgId, uncle.id)
                    }
                    Notifier.noFix(app, uncle.name)
                }
            }

            is FtMessage.SosMsg -> {
                val uncle = dao.uncleByNumber(from) ?: return
                if (m.lat != null && m.lng != null) {
                    storeFix(
                        dao, uncle, m.lat, m.lng,
                        m.accuracyM ?: -1.0, 0.0, m.batteryPct, FixSource.SOS,
                    )
                }
                Notifier.sos(app, uncle.name)
            }

            is FtMessage.GeofEvtMsg -> {
                val uncle = dao.uncleByNumber(from) ?: return
                val g = dao.geofencesFor(uncle.id).firstOrNull { it.geofId == m.geofId }
                if (m.lat != 0.0 || m.lng != 0.0) {
                    storeFix(dao, uncle, m.lat, m.lng, -1.0, 0.0, m.batteryPct, FixSource.GEOF)
                }
                Notifier.geofence(app, uncle.name, m.direction == GeofDirection.IN, g?.label ?: "home")
            }

            is FtMessage.LowBatMsg -> {
                val uncle = dao.uncleByNumber(from)
                Notifier.lowBattery(app, uncle?.name ?: "Uncle", m.batteryPct)
            }

            is FtMessage.GeofAckMsg -> {
                // Circle registered on his phone — nothing to show.
            }

            is FtMessage.CheckMsg, is FtMessage.GeofMsg -> {
                // Watcher never receives these.
            }
        }
    }

    private suspend fun storeFix(
        dao: com.killindodo.familytrack.watcher.data.WatcherDao,
        uncle: UncleEntity,
        lat: Double,
        lng: Double,
        acc: Double,
        spd: Double,
        bat: Int,
        source: FixSource,
    ) {
        val now = System.currentTimeMillis()
        dao.updateUncle(
            uncle.copy(
                lastLat = lat,
                lastLng = lng,
                lastAcc = acc,
                lastSpd = spd,
                lastBat = bat,
                lastSeenAt = now,
                lastSource = source.name,
            ),
        )
        dao.insertFix(
            FixEntity(
                uncleId = uncle.id,
                lat = lat,
                lng = lng,
                acc = acc,
                spd = spd,
                bat = bat,
                receivedAt = now,
                source = source.name,
            ),
        )
    }

    companion object {
        val PALETTE = listOf(0xFF10B981, 0xFFF59E0B, 0xFF3B82F6, 0xFFEF4444, 0xFF8B5CF6, 0xFFEC4899)
    }
}
