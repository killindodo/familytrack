package com.killindodo.familytrack.uncle.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.app.Service
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.killindodo.familytrack.core.FixReason
import com.killindodo.familytrack.core.FtMessage
import com.killindodo.familytrack.core.SmsProtocol
import com.killindodo.familytrack.core.sms.FtBus
import com.killindodo.familytrack.uncle.data.PairingEntity
import com.killindodo.familytrack.uncle.data.UncleDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The quiet worker. Stays alive with an honest notification; answers
 * check-ins with ONE GPS fix; applies home circles; warns about low battery.
 */
class UncleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db by lazy { UncleDb.get(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        running = true
        startInForeground()
        registerBatteryWatcher()
        UncleJobs.scheduleAll(applicationContext)
        scope.launch { collectIncoming() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        scope.cancel()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun startInForeground() {
        val channel = NotificationChannelCompat.Builder(
            CH_ID, NotificationManagerCompat.IMPORTANCE_LOW,
        ).setName("FamilyTrack status").build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)

        val notif = NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("FamilyTrack is ready")
            .setContentText("Shares location when family checks in")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this, NOTIF_ID, notif,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
    }

    private suspend fun collectIncoming() {
        FtBus.incoming.collect { ev ->
            val p = db.dao().pairing() ?: return@collect
            if (ev.from != p.watcherNumber) return@collect
            when (val m = ev.msg) {
                is FtMessage.CheckMsg -> answerCheck(p, m)
                is FtMessage.GeofMsg -> applyGeofence(p, m)
                else -> {}
            }
        }
    }

    private suspend fun answerCheck(p: PairingEntity, m: FtMessage.CheckMsg) {
        val bat = batteryPct(applicationContext)
        val res = GpsOneShot(applicationContext).get()
        val body = when (res) {
            is GpsOneShot.R.Got -> SmsProtocol.encode(
                FtMessage.FixMsg(
                    m.msgId, res.loc.latitude, res.loc.longitude,
                    res.loc.accuracy.toDouble(), res.loc.speed.toDouble(), bat,
                    System.currentTimeMillis(),
                ),
            )
            is GpsOneShot.R.Disabled -> SmsProtocol.encode(
                FtMessage.NoFixMsg(m.msgId, FixReason.GPS_OFF, bat, System.currentTimeMillis()),
            )
            is GpsOneShot.R.Denied -> SmsProtocol.encode(
                FtMessage.NoFixMsg(m.msgId, FixReason.LOC_OFF, bat, System.currentTimeMillis()),
            )
            GpsOneShot.R.None -> SmsProtocol.encode(
                FtMessage.NoFixMsg(m.msgId, FixReason.NO_FIX, bat, System.currentTimeMillis()),
            )
        }
        Sender.sendWithFallback(applicationContext, p.watcherNumber, body)
    }

    private suspend fun applyGeofence(p: PairingEntity, m: FtMessage.GeofMsg) {
        db.dao().putGeofence(
            com.killindodo.familytrack.uncle.data.GeofenceEntity(
                m.geofId, m.lat, m.lng, m.radiusM, m.label,
            ),
        )
        GeofenceBridge.register(applicationContext, db.dao().geofences())
        Sender.sendWithFallback(
            applicationContext, p.watcherNumber,
            SmsProtocol.encode(
                FtMessage.GeofAckMsg(m.geofId, batteryPct(applicationContext), System.currentTimeMillis()),
            ),
        )
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = context.getSharedPreferences(FLAG_FILE, MODE_PRIVATE)
            when (intent.action) {
                Intent.ACTION_BATTERY_LOW -> {
                    if (!prefs.getBoolean(LOWBAT_SENT, false)) {
                        prefs.edit().putBoolean(LOWBAT_SENT, true).apply()
                        scope.launch {
                            val p = db.dao().pairing() ?: return@launch
                            Sender.sendWithFallback(
                                applicationContext, p.watcherNumber,
                                SmsProtocol.encode(
                                    FtMessage.LowBatMsg(batteryPct(applicationContext), System.currentTimeMillis()),
                                ),
                            )
                        }
                    }
                }
                Intent.ACTION_BATTERY_OKAY -> prefs.edit().putBoolean(LOWBAT_SENT, false).apply()
            }
        }
    }

    private fun registerBatteryWatcher() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Current level check at startup (missed broadcasts while dead).
        val pct = batteryPct(this)
        val prefs = getSharedPreferences(FLAG_FILE, MODE_PRIVATE)
        if (pct in 1..14 && !prefs.getBoolean(LOWBAT_SENT, false)) {
            prefs.edit().putBoolean(LOWBAT_SENT, true).apply()
            scope.launch {
                val p = db.dao().pairing() ?: return@launch
                Sender.sendWithFallback(
                    applicationContext, p.watcherNumber,
                    SmsProtocol.encode(FtMessage.LowBatMsg(pct, System.currentTimeMillis())),
                )
            }
        }
    }

    companion object {
        @Volatile
        var running: Boolean = false
            private set

        private const val CH_ID = "ft_status"
        private const val NOTIF_ID = 42
        private const val FLAG_FILE = "ft_flags"
        private const val LOWBAT_SENT = "lowbat_sent"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, UncleService::class.java),
            )
        }
    }
}
