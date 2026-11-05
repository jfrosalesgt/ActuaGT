package com.actuagt.shared.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.actuagt.shared.model.DEFAULT_MAP_LATITUDE
import com.actuagt.shared.model.DEFAULT_MAP_LONGITUDE
import com.actuagt.shared.model.IncidentReport
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private class ReportClusterItem(val report: IncidentReport) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(report.latitude, report.longitude)
    override fun getTitle(): String = report.title
    override fun getSnippet(): String? = report.status.name.lowercase()
    override fun getZIndex(): Float = 0f
}

// Default view: Parque Central de Guatemala, matching the list fallback.
private val DEFAULT_CENTER = LatLng(DEFAULT_MAP_LATITUDE, DEFAULT_MAP_LONGITUDE)

@Composable
actual fun ReportMap(
    reports: List<IncidentReport>,
    onSelect: (IncidentReport) -> Unit,
    selectedLatitude: Double?,
    selectedLongitude: Double?,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
    onMyLocationClick: () -> Unit,
    locating: Boolean,
    modifier: Modifier,
) {
    val items = remember(reports) {
        reports.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .map(::ReportClusterItem)
    }
    val selected = if (selectedLatitude != null && selectedLongitude != null) {
        LatLng(selectedLatitude, selectedLongitude)
    } else {
        null
    }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selected ?: DEFAULT_CENTER, 12f)
    }
    val pinState = rememberMarkerState(position = selected ?: DEFAULT_CENTER)
    LaunchedEffect(selected) {
        selected?.let {
            camera.position = CameraPosition.fromLatLngZoom(it, 14f)
            pinState.position = it
        }
    }
    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.matchParentSize(),
            cameraPositionState = camera,
            onMapClick = { point -> onMapTap(point.latitude, point.longitude) },
        ) {
            Clustering(
                items = items,
                onClusterItemClick = {
                    onSelect(it.report)
                    true
                },
            )
            if (selected != null) {
                Marker(
                    state = pinState,
                    title = "Ubicación buscada",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                )
            }
        }
        // Solid primary over the map tiles: the outlined variant washed out
        // against light streets and was unreadable.
        Button(
            onClick = onMyLocationClick,
            enabled = !locating,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        ) {
            Text(text = if (locating) "Ubicando…" else "Mi ubicación")
        }
    }
}
