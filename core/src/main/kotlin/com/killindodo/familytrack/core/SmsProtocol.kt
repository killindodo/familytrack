package com.killindodo.familytrack.core

import java.util.Locale

/**
 * FamilyTrack invisible-SMS protocol.
 * Format: "FT1|TYPE|field|field|…" — pipe-delimited, version tag first.
 * Never crashes: parse() returns null for anything malformed.
 */
sealed interface FtMessage {
    /** uncle→watcher: "I scanned your QR / typed your code". */
    data class PairMsg(val code: String, val uncleName: String) : FtMessage

    /** watcher→uncle: "Family wants to see where you are". */
    data class CheckMsg(val msgId: Long, val ts: Long) : FtMessage

    /** uncle→watcher: answer to a CHECK (or follow-up after SOS). */
    data class FixMsg(
        val msgId: Long,
        val lat: Double,
        val lng: Double,
        val accuracyM: Double,
        val speedMps: Double,
        val batteryPct: Int,
        val ts: Long,
    ) : FtMessage

    /** uncle→watcher: phone alive but no fix available. */
    data class NoFixMsg(val msgId: Long, val reason: FixReason, val batteryPct: Int, val ts: Long) : FtMessage

    /** uncle→watcher: big red button. Coordinates may be absent if no fix yet. */
    data class SosMsg(val lat: Double?, val lng: Double?, val accuracyM: Double?, val batteryPct: Int, val ts: Long) : FtMessage

    /** uncle→watcher: battery < 15% (one-shot per drop). */
    data class LowBatMsg(val batteryPct: Int, val ts: Long) : FtMessage

    /** watcher→uncle: set/update a home circle on the uncle phone. */
    data class GeofMsg(val geofId: String, val lat: Double, val lng: Double, val radiusM: Double, val label: String) : FtMessage

    /** uncle→watcher: "circle saved". */
    data class GeofAckMsg(val geofId: String, val batteryPct: Int, val ts: Long) : FtMessage

    /** uncle→watcher: entered/left the circle. */
    data class GeofEvtMsg(val geofId: String, val direction: GeofDirection, val lat: Double, val lng: Double, val batteryPct: Int, val ts: Long) : FtMessage
}

enum class FixReason { GPS_OFF, NO_FIX, LOC_OFF }

enum class GeofDirection { IN, OUT }

object SmsProtocol {
    const val PREFIX = "FT1"

    fun encode(msg: FtMessage): String = when (msg) {
        is FtMessage.PairMsg -> j(PREFIX, "PAIR", clean(msg.code), clean(msg.uncleName))
        is FtMessage.CheckMsg -> j(PREFIX, "CHECK", msg.msgId, msg.ts)
        is FtMessage.FixMsg -> j(
            PREFIX, "FIX", msg.msgId, d6(msg.lat), d6(msg.lng), d1(msg.accuracyM), d1(msg.speedMps), msg.batteryPct, msg.ts,
        )
        is FtMessage.NoFixMsg -> j(PREFIX, "NOFIX", msg.msgId, msg.reason.name, msg.batteryPct, msg.ts)
        is FtMessage.SosMsg -> j(PREFIX, "SOS", opt6(msg.lat), opt6(msg.lng), opt1(msg.accuracyM), msg.batteryPct, msg.ts)
        is FtMessage.LowBatMsg -> j(PREFIX, "LOWBAT", msg.batteryPct, msg.ts)
        is FtMessage.GeofMsg -> j(PREFIX, "GEOF", clean(msg.geofId), d6(msg.lat), d6(msg.lng), d1(msg.radiusM), clean(msg.label))
        is FtMessage.GeofAckMsg -> j(PREFIX, "GEOFACK", clean(msg.geofId), msg.batteryPct, msg.ts)
        is FtMessage.GeofEvtMsg -> j(PREFIX, "GEFEVT", clean(msg.geofId), msg.direction.name, d6(msg.lat), d6(msg.lng), msg.batteryPct, msg.ts)
    }

    fun parse(text: String?): FtMessage? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split('|')
        if (parts.isEmpty() || parts[0] != PREFIX) return null
        return try {
            when (parts.getOrNull(1)) {
                "PAIR" -> FtMessage.PairMsg(parts[2], parts[3])
                "CHECK" -> FtMessage.CheckMsg(parts[2].toLong(), parts[3].toLong())
                "FIX" -> FtMessage.FixMsg(
                    parts[2].toLong(), parts[3].toDouble(), parts[4].toDouble(),
                    parts[5].toDouble(), parts[6].toDouble(), parts[7].toInt(), parts[8].toLong(),
                )
                "NOFIX" -> FtMessage.NoFixMsg(parts[2].toLong(), FixReason.valueOf(parts[3]), parts[4].toInt(), parts[5].toLong())
                "SOS" -> FtMessage.SosMsg(
                    parts[3].toDoubleOrNull(), parts[4].toDoubleOrNull(), parts[5].toDoubleOrNull(),
                    parts[6].toInt(), parts[7].toLong(),
                )
                "LOWBAT" -> FtMessage.LowBatMsg(parts[2].toInt(), parts[3].toLong())
                "GEOF" -> FtMessage.GeofMsg(parts[2], parts[3].toDouble(), parts[4].toDouble(), parts[5].toDouble(), parts[6])
                "GEOFACK" -> FtMessage.GeofAckMsg(parts[2], parts[3].toInt(), parts[4].toLong())
                "GEFEVT" -> FtMessage.GeofEvtMsg(
                    parts[2], GeofDirection.valueOf(parts[3]), parts[4].toDouble(),
                    parts[5].toDouble(), parts[6].toInt(), parts[7].toLong(),
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun j(vararg fields: Any?): String = fields.joinToString("|")

    private fun d6(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun d1(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun opt6(v: Double?): String = if (v == null) "" else d6(v)

    private fun opt1(v: Double?): String = if (v == null) "" else d1(v)

    /** Free-text fields must never contain the field separator. */
    private fun clean(s: String): String = s.replace("|", " ").trim()
}
