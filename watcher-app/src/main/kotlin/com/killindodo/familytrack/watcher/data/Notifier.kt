package com.killindodo.familytrack.watcher.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CH_ALERTS = "ft_alerts"
    private const val CH_INFO = "ft_info"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Family alerts", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Family updates", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun post(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    fun sos(ctx: Context, name: String) = post(
        ctx, CH_ALERTS, 1001, "🆘 SOS from $name",
        "Emergency! Open FamilyTrack to see where $name is right now.",
    )

    fun noAnswer(ctx: Context, name: String) = post(
        ctx, CH_ALERTS, 1002, "⚠️ $name did not respond",
        "No reply for 5 minutes after your location check. Phone may be off, out of signal, or battery dead.",
    )

    fun noFix(ctx: Context, name: String) = post(
        ctx, CH_INFO, 1003, "$name's phone is alive",
        "But no GPS fix right now. Try checking again in a minute.",
    )

    fun geofence(ctx: Context, name: String, entered: Boolean, label: String) = post(
        ctx, CH_ALERTS, 1004,
        if (entered) "$name arrived at $label" else "$name left $label",
        if (entered) "Home circle entered." else "Home circle exited — worth a check.",
    )

    fun lowBattery(ctx: Context, name: String, pct: Int) = post(
        ctx, CH_INFO, 1005, "🔋 $name's battery is low",
        "Battery at $pct% — the phone may switch off soon.",
    )

    fun paired(ctx: Context, name: String) = post(
        ctx, CH_INFO, 1006, "✅ $name is connected",
        "Pairing complete. $name now appears on your map.",
    )
}
