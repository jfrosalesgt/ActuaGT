package com.actuagt.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

// Default view: Guatemala City, matching the feed map.
private val DEFAULT_CENTER = LatLng(14.6349, -90.5515)

@Composable
actual fun ReportLocationPicker(
    latitude: Double?,
    longitude: Double?,
    onSelect: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier,
) {
    val selected = if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selected ?: DEFAULT_CENTER, 14f)
    }
    // Marker follows the latest selection (e.g. after a GPS fix).
    @Suppress("DEPRECATION")
    val markerState = rememberMarkerState(position = selected ?: DEFAULT_CENTER)

    // Keep the camera and the marker on the latest selection (e.g. after
    // a GPS fix or a map tap).
    LaunchedEffect(selected) {
        selected?.let {
            camera.position = CameraPosition.fromLatLngZoom(it, 15f)
            markerState.position = it
        }
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        onMapClick = { point -> onSelect(point.latitude, point.longitude) },
    ) {
        if (selected != null) {
            Marker(
                state = markerState,
                title = "Punto del reporte",
            )
        }
    }
}
