package com.killindodo.familytrack.uncle.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Battery level 0–100, or -1 when unknown. */
fun batteryPct(context: Context): Int = try {
    val bm = context.getSystemService(BatteryManager::class.java)
    val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (p in 1..100) p else -1
} catch (_: Exception) {
    -1
}

/**
 * Takes ONE high-accuracy GPS fix on demand. GPS hardware works offline;
 * only the permission or disabled providers stop us.
 */
class GpsOneShot(private val context: Context) {

    sealed interface R {
        data class Got(val loc: Location) : R
        data object Disabled : R
        data object Denied : R
        data object None : R
    }

    suspend fun get(timeoutMs: Long = 20_000L): R =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            fun finish(r: R) {
                if (cont.isActive) cont.resume(r)
            }

            val fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!fine) {
                finish(R.Denied)
                return@suspendCancellableCoroutine
            }

            val enabled = try {
                val lm = context.getSystemService(LocationManager::class.java)
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {
                false
            }
            if (!enabled) {
                finish(R.Disabled)
                return@suspendCancellableCoroutine
            }

            val client = LocationServices.getFusedLocationProviderClient(context)
            val done = AtomicBoolean(false)
            val cts = CancellationTokenSource()

            fun once(r: R) {
                if (done.compareAndSet(false, true)) finish(r)
            }

            fun fallback() {
                try {
                    client.lastLocation
                        .addOnSuccessListener { l -> once(if (l != null) R.Got(l) else R.None) }
                        .addOnFailureListener { once(R.None) }
                } catch (_: Exception) {
                    once(R.None)
                }
            }

            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { l ->
                        if (l != null) once(R.Got(l)) else fallback()
                    }
                    .addOnFailureListener { fallback() }
            } catch (_: Exception) {
                once(R.None)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                cts.cancel()
                once(R.None)
            }, timeoutMs)

            cont.invokeOnCancellation { cts.cancel() }
        }
}
