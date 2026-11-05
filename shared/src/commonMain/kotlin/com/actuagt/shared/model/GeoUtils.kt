package com.actuagt.shared.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Home map/list geography: the default focus is the Parque Central de
// Guatemala, used when the citizen has not picked a point yet.
const val DEFAULT_MAP_LATITUDE = 14.6417
const val DEFAULT_MAP_LONGITUDE = -90.5133
const val DEFAULT_MAP_LABEL = "Parque Central de Guatemala"

private const val EARTH_RADIUS_METERS = 6_371_000.0

// Haversine distance between two points, in meters.
fun distanceMeters(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double,
): Double {
    val deg = PI / 180.0
    val lat1 = fromLatitude * deg
    val lat2 = toLatitude * deg
    val dLat = (toLatitude - fromLatitude) * deg
    val dLng = (toLongitude - fromLongitude) * deg
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
}

// Nearest report first from a reference point; reports without coordinates
// sink to the end.
fun List<IncidentReport>.sortedByDistanceFrom(latitude: Double, longitude: Double): List<IncidentReport> =
    sortedBy { report ->
        if (report.latitude == 0.0 && report.longitude == 0.0) {
            Double.MAX_VALUE
        } else {
            distanceMeters(latitude, longitude, report.latitude, report.longitude)
        }
    }

// Short Spanish label for list rows: "850 m" or "1.2 km".
fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        "${meters.toInt()} m"
    } else {
        val tenths = (meters / 100).toInt()
        "${tenths / 10}.${tenths % 10} km"
    }

// Spanish label for a report category code; unknown codes read as Otros.
fun categoryLabel(code: String): String = when (code) {
    "VIALIDAD" -> "Vialidad"
    "AGUA_Y_DRENAJES" -> "Agua y drenajes"
    "ALUMBRADO" -> "Alumbrado"
    "DESECHOS_Y_AMBIENTE" -> "Desechos y ambiente"
    "ESPACIOS_PUBLICOS" -> "Espacios públicos"
    else -> "Otros"
}

// Five-decimal coordinate for point labels ("14.63453, -90.5515").
fun formatCoordinate(value: Double): String {
    val rounded = kotlin.math.round(value * 100_000) / 100_000.0
    return rounded.toString()
}
