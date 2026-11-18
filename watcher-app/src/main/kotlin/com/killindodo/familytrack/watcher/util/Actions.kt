package com.killindodo.familytrack.watcher.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.killindodo.familytrack.core.FtMessage
import com.killindodo.familytrack.core.SmsProtocol
import com.killindodo.familytrack.core.sms.FtSender
import com.killindodo.familytrack.watcher.data.CheckEntity
import com.killindodo.familytrack.watcher.data.GeofEntity
import com.killindodo.familytrack.watcher.data.UncleEntity
import com.killindodo.familytrack.watcher.data.WatcherDb
import com.killindodo.familytrack.watcher.sms.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Prefs {
    private const val FILE = "watcher_prefs"

    fun permsDone(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("perms_done", false)

    fun markPermsDone(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("perms_done", true).apply()
    }

    fun watcherNumber(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("my_number", "").orEmpty()

    fun setWatcherNumber(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("my_number", v).apply()
    }
}

/** Sends a CHECK and arms the +5 min did-not-respond alarm. */
fun checkUncle(ctx: Context, uncle: UncleEntity, onResult: (Boolean) -> Unit) {
    val msgId = System.currentTimeMillis()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        WatcherDb.get(ctx).dao().insertCheck(CheckEntity(msgId = msgId, uncleId = uncle.id, sentAt = msgId))
        AlarmHelper.schedule(ctx, msgId, uncle.id)
    }
    FtSender.send(ctx, uncle.number, SmsProtocol.encode(FtMessage.CheckMsg(msgId, msgId))) { sent, _ ->
        onResult(sent)
    }
}

/** Sends the home circle to his phone (and remembers it for the map). */
fun sendHomeCircle(
    ctx: Context,
    uncle: UncleEntity,
    lat: Double,
    lng: Double,
    radiusM: Double,
    onResult: (Boolean) -> Unit,
) {
    val geofId = "home_${uncle.id}"
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        WatcherDb.get(ctx).dao().putGeof(
            GeofEntity(
                geofId = geofId, uncleId = uncle.id, lat = lat, lng = lng,
                radiusM = radiusM, label = "Home", createdAt = System.currentTimeMillis(),
            ),
        )
    }
    FtSender.send(
        ctx, uncle.number,
        SmsProtocol.encode(FtMessage.GeofMsg(geofId, lat, lng, radiusM, "Home")),
    ) { sent, _ -> onResult(sent) }
}

/** Opens Google Maps directions (falls back to any maps app). */
fun navigateTo(ctx: Context, lat: Double, lng: Double) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lng"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}

fun hasSmsPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
