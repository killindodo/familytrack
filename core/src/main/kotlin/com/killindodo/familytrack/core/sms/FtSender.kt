package com.killindodo.familytrack.core.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sends one FT1 SMS and reports success/failure exactly once.
 * Classic sent-intent pattern, works across minSdk 26 → 35.
 * Each send owns its own receiver/flag — concurrent sends are safe.
 */
object FtSender {

    fun send(context: Context, to: String, body: String, onResult: (sent: Boolean, error: String?) -> Unit) {
        try {
            val sm = smsManager(context)
            val app = context.applicationContext
            val done = AtomicBoolean(false)

            val action = "ft.sent.${System.nanoTime()}"
            val pi = PendingIntent.getBroadcast(
                app,
                (System.nanoTime() and 0x7FFFFFFF).toInt(),
                Intent(action).setPackage(app.packageName),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            lateinit var receiver: BroadcastReceiver

            fun finishOnce(sent: Boolean, error: String?) {
                if (done.compareAndSet(false, true)) {
                    try { app.unregisterReceiver(receiver) } catch (_: Exception) {}
                    onResult(sent, error)
                }
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, i: Intent) {
                    val ok = resultCode == Activity.RESULT_OK
                    val err = if (ok) null else when (resultCode) {
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE"
                        SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
                        SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
                        else -> "ERROR_$resultCode"
                    }
                    finishOnce(ok, err)
                }
            }

            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            // 30 s safety: if no sent-intent ever arrives, report failure.
            Handler(app.mainLooper).postDelayed({ finishOnce(false, "TIMEOUT") }, 30_000L)

            sm.sendTextMessage(to, null, body, pi, null)
        } catch (e: Exception) {
            onResult(false, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun smsManager(context: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
}
