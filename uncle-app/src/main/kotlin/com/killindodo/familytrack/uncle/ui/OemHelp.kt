package com.killindodo.familytrack.uncle.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/** Honest battery-survival guidance per phone brand (OEM-specific). */
object OemHelp {

    fun guidance(ctx: Context): String? {
        val m = Build.MANUFACTURER.lowercase(Locale.US)
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
                "Xiaomi / Redmi / POCO: Settings → Apps → Manage apps → FamilyTrack → Autostart: ON; Battery saver: No restrictions. Then lock the app in Recents (swipe down on its card)."
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
                "Oppo / Realme / OnePlus: Settings → Battery → More settings → Allow background activity for FamilyTrack; App info → Autostart ON; lock the app in Recents."
            m.contains("vivo") || m.contains("iqoo") ->
                "Vivo / iQOO: i Manager → App manager → Autostart: allow FamilyTrack; Battery → High background power consumption: allow."
            m.contains("samsung") ->
                "Samsung: Settings → Battery → Background usage limits → remove FamilyTrack from Sleeping apps; add it to Unmonitored apps."
            else -> null
        }
    }

    /** Tries the brand's own autostart screen; falls back to system settings. */
    fun openBrandSettings(ctx: Context): Boolean {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        )
        for (c in candidates) {
            try {
                ctx.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun requestBatteryExemption(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) return true
        return try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
