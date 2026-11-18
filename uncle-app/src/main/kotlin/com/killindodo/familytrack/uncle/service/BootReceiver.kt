package com.killindodo.familytrack.uncle.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Phone rebooted → bring the quiet worker back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            UncleService.start(context)
            UncleJobs.scheduleAll(context)
        }
    }
}
