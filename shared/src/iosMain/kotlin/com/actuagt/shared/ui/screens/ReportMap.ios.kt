package com.actuagt.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.actuagt.shared.model.IncidentReport

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
) = Unit
