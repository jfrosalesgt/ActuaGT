package com.actuagt.shared.media

actual class UserLocation actual constructor() {
    // iOS wiring is pending (no Mac/Xcode available): the report form falls
    // back to manual map positioning.
    actual suspend fun currentCoordinates(): ReportCoordinates? = null

    actual fun isLocationAvailable(): Boolean = false
}

actual class UserLocationName actual constructor() {
    actual suspend fun municipalityName(latitude: Double, longitude: Double): String? = null
}
