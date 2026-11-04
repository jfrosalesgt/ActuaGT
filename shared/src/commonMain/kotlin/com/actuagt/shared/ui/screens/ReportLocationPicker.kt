package com.actuagt.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Embedded location picker for the report form: shows the selected point
// and lets the user move it by tapping. Null coordinates mean no point yet.
@Composable
expect fun ReportLocationPicker(
    latitude: Double?,
    longitude: Double?,
    onSelect: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
)
