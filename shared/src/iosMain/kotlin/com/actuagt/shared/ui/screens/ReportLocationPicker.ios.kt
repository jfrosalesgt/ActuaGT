package com.actuagt.shared.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ReportLocationPicker(
    latitude: Double?,
    longitude: Double?,
    onSelect: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier,
) {
    Text(text = "Mapa no disponible en iOS todavía.", modifier = modifier)
}
