package com.actuagt.shared.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.actuagt.shared.model.IncidentReport

// Platform map of published reports plus the ratification queue. Tapping a
// marker selects the report with the existing ratification panel.
@Composable
expect fun ReportMap(
    reports: List<IncidentReport>,
    onSelect: (IncidentReport) -> Unit,
    modifier: Modifier = Modifier,
)
