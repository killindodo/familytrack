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

/** Where a stored fix came from (watcher-side history bookkeeping). */
enum class FixSource { CHECK, SOS, GEOF, LOWBAT }

/** Alert types delivered to the watcher as local notifications. */
enum class AlertType {
    SOS,
    NO_ANSWER_TO_CHECK_IN,
    LOW_BATTERY,
    GEOFENCE_ENTER,
    GEOFENCE_LEAVE,
}
