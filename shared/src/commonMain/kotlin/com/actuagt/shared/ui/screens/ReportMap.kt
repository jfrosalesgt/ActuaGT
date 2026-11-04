package com.actuagt.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.actuagt.shared.model.IncidentReport

// Platform map of published reports plus the ratification queue. Tapping a
// marker selects the report with the existing ratification panel. Tapping
// empty map drops the citizen's search pin; the overlay button centers the
// pin on the GPS fix.
@Composable
expect fun ReportMap(
    reports: List<IncidentReport>,
    onSelect: (IncidentReport) -> Unit,
    selectedLatitude: Double?,
    selectedLongitude: Double?,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
    onMyLocationClick: () -> Unit,
    locating: Boolean,
    modifier: Modifier = Modifier,
)
