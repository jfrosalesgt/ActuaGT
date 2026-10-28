package com.actuagt.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.actuagt.shared.model.IncidentReport
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState

private class ReportClusterItem(val report: IncidentReport) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(report.latitude, report.longitude)
    override fun getTitle(): String = report.title
    override fun getSnippet(): String = report.status.name.lowercase()
    override fun getZIndex(): Float = 0f
}

@Composable
actual fun ReportMap(
    reports: List<IncidentReport>,
    onSelect: (IncidentReport) -> Unit,
    modifier: Modifier,
) {
    val items = remember(reports) {
        reports.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .map(::ReportClusterItem)
    }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(14.6349, -90.5515), 11f)
    }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
    ) {
        Clustering(
            items = items,
            onClusterItemClick = {
                onSelect(it.report)
                true
            },
        )
    }
}
