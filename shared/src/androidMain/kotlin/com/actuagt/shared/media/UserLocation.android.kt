package com.actuagt.shared.media

actual class UserLocation actual constructor() {
    actual suspend fun currentCoordinates(): ReportCoordinates? = AndroidLocation.fetch()

    actual fun isLocationAvailable(): Boolean = AndroidLocation.isAvailable()
}

actual class UserLocationName actual constructor() {
    actual suspend fun municipalityName(latitude: Double, longitude: Double): String? =
        AndroidLocation.municipalityName(latitude, longitude)
}
