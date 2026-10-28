package com.actuagt.shared.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.actuagt.shared.model.IncidentReport

@Composable
actual fun ReportMap(
    reports: List<IncidentReport>,
    onSelect: (IncidentReport) -> Unit,
    modifier: Modifier,
) {
    Text(text = "Mapa no disponible en iOS todavía.", modifier = modifier)
}
