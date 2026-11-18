package com.killindodo.familytrack.core

/**
 * One location fix produced by an uncle device (on-demand check-in or SOS).
 */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val speedMps: Double,
    val batteryPct: Int,
    val timestampMs: Long,
)

/** Uncle identity + latest known state, as shown on the watcher map card. */
data class UncleStatus(
    val id: String,
    val name: String,
    val lastFix: LocationFix?,
)

/** Alert types delivered to the watcher via FCM. */
enum class AlertType {
    SOS,
    NO_ANSWER_TO_CHECK_IN,
    LOW_BATTERY,
    GEOFENCE_ENTER,
    GEOFENCE_LEAVE,
}

/** Command written to `uncles/{id}/commands/{id}` by the watcher. */
data class CheckInCommand(
    val type: String = "CHECK_IN",
    val createdAtMs: Long,
    val answeredAtMs: Long? = null,
    val expired: Boolean = false,
)
