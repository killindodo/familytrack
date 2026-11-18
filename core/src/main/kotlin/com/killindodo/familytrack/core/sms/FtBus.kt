package com.killindodo.familytrack.core.sms

import com.killindodo.familytrack.core.FtMessage
import kotlinx.coroutines.flow.MutableSharedFlow

/** One incoming FT1 message: who sent it (phone number) and what it says. */
data class FtEvent(val from: String, val msg: FtMessage)

/**
 * In-app bus: the SMS receiver publishes here; each app's service collects.
 * Single shared instance — both apps embed this code in their own process.
 */
object FtBus {
    val incoming = MutableSharedFlow<FtEvent>(extraBufferCapacity = 64)
}
