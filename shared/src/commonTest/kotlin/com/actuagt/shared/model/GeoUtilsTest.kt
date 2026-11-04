package com.actuagt.shared.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Haversine ordering powers the home list: nearest report first from the
// selected map point. Distances use the equatorial approximation.
class GeoUtilsTest {

    @Test
    fun samePointIsZero() {
        assertEquals(0.0, distanceMeters(14.6, -90.5, 14.6, -90.5), 0.001)
    }

    @Test
    fun oneLatitudeDegreeIsAbout111Kilometers() {
        val meters = distanceMeters(14.0, -90.5, 15.0, -90.5)
        assertTrue(meters in 110_000.0..112_000.0, "was $meters")
    }

    @Test
    fun sortsNearestFirst() {
        val center = 14.6417 to -90.5133
        val far = IncidentReport(id = "far", latitude = 15.5, longitude = -90.5)
        val near = IncidentReport(id = "near", latitude = 14.65, longitude = -90.51)
        val sorted = listOf(far, near).sortedByDistanceFrom(center.first, center.second)
        assertEquals(listOf("near", "far"), sorted.map { it.id })
    }

    @Test
    fun formatsMetersAndKilometers() {
        assertEquals("850 m", formatDistance(850.0))
        assertEquals("1.2 km", formatDistance(1234.0))
    }
}
