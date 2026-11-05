package com.actuagt.shared.media

// Coordinates chosen for a report: GPS fix or a point the user tapped.
data class ReportCoordinates(
    val latitude: Double,
    val longitude: Double,
)

// Current device position for report geolocation. Returns null when the
// permission is denied, location is off, or no fix is available, so the UI
// can fall back to manual map positioning.
expect class UserLocation() {
    suspend fun currentCoordinates(): ReportCoordinates?

    fun isLocationAvailable(): Boolean
}

// Municipality name for a report point, resolved from the map metadata
// (Android Geocoder). Null when the backend is missing or offline: the
// citizen keeps whatever zone was typed by hand.
expect class UserLocationName() {
    suspend fun municipalityName(latitude: Double, longitude: Double): String?
}
