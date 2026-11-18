package com.killindodo.familytrack.uncle.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.killindodo.familytrack.core.GeofDirection
import com.killindodo.familytrack.core.FtMessage
import com.killindodo.familytrack.core.SmsProtocol
import com.killindodo.familytrack.uncle.data.GeofenceEntity
import com.killindodo.familytrack.uncle.data.UncleDb
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Registers home circles with Android's own (battery-light) geofencing engine. */
object GeofenceBridge {

    fun register(context: Context, list: List<GeofenceEntity>) {
        if (list.isEmpty()) return
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val client = LocationServices.getGeofencingClient(context)
        val fences = list.map { g ->
            Geofence.Builder()
                .setRequestId(g.geofId)
                .setCircularRegion(g.lat, g.lng, g.radiusM.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
                )
                .setNotificationResponsiveness(30_000)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTriggerDisposition(GeofencingRequest.INITIAL_TRIGGER_DONT_FIRE)
            .addGeofences(fences)
            .build()
        val pi = PendingIntent.getBroadcast(
            context, 9001,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        try {
            client.addGeofences(request, pi)
        } catch (_: Exception) {
            // Missing background permission or OEM quirk — the circle simply
            // won't be watched until a later registration succeeds.
        }
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2.0 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

/** OS says a circle was entered/left → confirm with our own fix → text the family. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        val enter = ev.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        val ids = ev.triggeringGeofences?.map { it.requestId } ?: return
        val app = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val dao = UncleDb.get(app).dao()
            val p = dao.pairing() ?: return@launch
            val g = dao.geofences().firstOrNull { it.geofId in ids } ?: return@launch

            val res = GpsOneShot(app).get(15_000)
            val bat = batteryPct(app)
            val dir = if (enter) GeofDirection.IN else GeofDirection.OUT
            val loc = (res as? GpsOneShot.R.Got)?.loc

            // GPS-drift guard: confirm the OS event with our own fix when we can.
            val confirmed = when {
                loc == null -> true
                enter -> GeofenceBridge.distanceMeters(loc.latitude, loc.longitude, g.lat, g.lng) <= g.radiusM * 1.2
                else -> GeofenceBridge.distanceMeters(loc.latitude, loc.longitude, g.lat, g.lng) > g.radiusM * 0.8
            }
            if (!confirmed) return@launch

            val (la, lo) = if (loc != null) loc.latitude to loc.longitude else g.lat to g.lng
            Sender.sendWithFallback(
                app, p.watcherNumber,
                SmsProtocol.encode(
                    FtMessage.GeofEvtMsg(g.geofId, dir, la, lo, bat, System.currentTimeMillis()),
                ),
            )
        }
    }
}
