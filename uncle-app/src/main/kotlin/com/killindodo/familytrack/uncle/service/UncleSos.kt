package com.killindodo.familytrack.uncle.service

import android.content.Context
import com.killindodo.familytrack.core.FtMessage
import com.killindodo.familytrack.core.SmsProtocol
import com.killindodo.familytrack.uncle.data.UncleDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The big red button. Sends SOS immediately — with coords when we can get
 * them fast, otherwise without, followed up by a FIX as soon as a fix lands.
 */
object UncleSos {
    fun trigger(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val pairing = UncleDb.get(app).dao().pairing() ?: return@launch
            val bat = batteryPct(app)
            val ts = System.currentTimeMillis()
            val loc = (GpsOneShot(app).get(5_000) as? GpsOneShot.R.Got)?.loc

            val sosBody = if (loc != null) {
                SmsProtocol.encode(FtMessage.SosMsg(loc.latitude, loc.longitude, loc.accuracy.toDouble(), bat, ts))
            } else {
                SmsProtocol.encode(FtMessage.SosMsg(null, null, null, bat, ts))
            }
            Sender.sendWithFallback(app, pairing.watcherNumber, sosBody)

            if (loc == null) {
                val later = (GpsOneShot(app).get(20_000) as? GpsOneShot.R.Got)?.loc
                if (later != null) {
                    Sender.sendWithFallback(
                        app, pairing.watcherNumber,
                        SmsProtocol.encode(
                            FtMessage.FixMsg(
                                ts, later.latitude, later.longitude,
                                later.accuracy.toDouble(), later.speed.toDouble(),
                                batteryPct(app), System.currentTimeMillis(),
                            ),
                        ),
                    )
                }
            }
        }
    }
}
