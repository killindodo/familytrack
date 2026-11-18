package com.killindodo.familytrack.core.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.killindodo.familytrack.core.SmsProtocol

/**
 * Receives system SMS broadcasts and publishes parsed FT1 messages on the bus.
 * Does NOT abort the broadcast — the message still lands in the normal
 * Messages app, so an SOS is never lost even if the app is dead.
 */
class FtReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val body = msgs.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val from = msgs.firstOrNull()?.originatingAddress ?: return
        val parsed = SmsProtocol.parse(body) ?: return
        FtBus.incoming.tryEmit(FtEvent(from, parsed))
    }
}
