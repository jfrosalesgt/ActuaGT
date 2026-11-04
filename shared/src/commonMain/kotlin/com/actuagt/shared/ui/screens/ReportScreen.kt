package com.actuagt.shared.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.actuagt.shared.media.decodePreviewImage
import com.actuagt.shared.ui.components.ActuaGTTextField
import com.actuagt.shared.ui.components.SectionBanner
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import kotlin.math.roundToInt

// Citizen report form: real camera evidence plus neutral text, submitted to
// the moderation inbox. Reports go public only after 3 ratifications.
private fun formatCoordinate(value: Double): String {
    val rounded = (value * 100_000).roundToInt() / 100_000.0
    return rounded.toString()
}

object ReportScreen : Screen {
    @Composable
    override fun Content() {
        val model = rememberScreenModel { ReportScreenModel() }
        val title by model.title.collectAsState()
        val description by model.description.collectAsState()
        val category by model.category.collectAsState()
        val zone by model.zone.collectAsState()
        val photos by model.photos.collectAsState()
        val sending by model.sending.collectAsState()
        val listening by model.listening.collectAsState()
        val processing by model.processing.collectAsState()
        val confirmText by model.confirmText.collectAsState()
        val message by model.message.collectAsState()
        val verdict by model.verdict.collectAsState()
        val verifying by model.verifying.collectAsState()
        val latitude by model.latitude.collectAsState()
        val longitude by model.longitude.collectAsState()
        val locating by model.locating.collectAsState()

        confirmText?.let { cleaned ->
            AlertDialog(
                onDismissRequest = model::dismissDictation,
                title = { Text(text = "Confirmá el texto") },
                text = {
                    Column {
                        Text(
                            text = cleaned.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = cleaned.text)
                    }
                },
                confirmButton = {
                    TextButton(onClick = model::confirmDictation) {
                        Text(text = "Usar este texto")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = model::dismissDictation) {
                            Text(text = "Descartar")
                        }
                        TextButton(onClick = model::editDictation) {
                            Text(text = "Editar")
                        }
                    }
                },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            item {
                SectionBanner(
                    title = "Reportar",
                    subtitle = "Describe el problema con evidencia y ubicación",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Contanos qué ves con tu voz: la IA ordena la idea y completa el título y la descripción. Tocá Detener cuando termines.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (listening) {
                    OutlinedButton(
                        onClick = model::stopDictation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Detener")
                    }
                } else {
                    Button(
                        onClick = model::dictate,
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Dictar por voz")
                    }
                }
                if (processing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text(text = "Ordenando las ideas, espera un momento")
                    }
                }
                message?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = it)
                }
                Spacer(modifier = Modifier.height(12.dp))
                ActuaGTTextField(
                    value = title,
                    onValueChange = model::onTitleChange,
                    label = "Título",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Descripción", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                ActuaGTTextField(
                    value = description,
                    onValueChange = model::onDescriptionChange,
                    label = "Descripción",
                    minLines = 3,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Categoría", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
            }
            item {
                LazyRow {
                    items(model.categories, key = { it.code }) { option ->
                        FilterChip(
                            selected = option == category,
                            onClick = { model.onCategoryChange(option) },
                            label = { Text(text = option.label) },
                            modifier = Modifier.padding(end = 8.dp),
                            // Explicit container and label colors: the
                            // defaults inherited the background and the
                            // unselected chips were unreadable.
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ActuaGTTextField(
                    value = zone,
                    onValueChange = model::onZoneChange,
                    label = "Zona (municipio)",
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Ubicación", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val point = latitude?.let { lat -> longitude?.let { lng -> lat to lng } }
                    Text(
                        text = if (point != null) {
                            "Punto: ${formatCoordinate(point.first)}, ${formatCoordinate(point.second)}"
                        } else {
                            "Sin punto marcado"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    // Tonal filled style: the outlined variant inherited the
                    // background and was unreadable. Primary stays reserved
                    // for Verificar/Enviar.
                    FilledTonalButton(
                        onClick = model::locateMe,
                        enabled = !sending && !locating,
                    ) {
                        Text(text = if (locating) "Ubicando…" else "Usar mi ubicación")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ReportLocationPicker(
                    latitude = latitude,
                    longitude = longitude,
                    onSelect = model::onLocationChange,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tocá el mapa para mover el punto a donde está el problema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Evidencia: ${photos.size}/${ReportScreenModel.MAX_PHOTOS} fotos",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = model::attachPhoto,
                        enabled = !sending && photos.size < ReportScreenModel.MAX_PHOTOS,
                    ) {
                        Text(text = "Tomar foto")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            itemsIndexed(photos) { index, photo ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    val preview = remember(photo.bytes) { decodePreviewImage(photo.bytes) }
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "Foto ${index + 1}",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Foto ${index + 1}" + photo.metadata.capturedAtEpochMillis?.let { " • con fecha" }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { model.removePhoto(index) }) {
                        Text(text = "Quitar")
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                verdict?.let { v ->
                    Text(text = "Dictamen de la IA", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (v.match) {
                            "Las fotos coinciden con lo reportado. Podés enviarlo."
                        } else {
                            v.reason.ifBlank { "Las fotos no muestran lo descrito." }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = { if (verdict?.match == true) model.submit() else model.verifyReport() },
                    enabled = !sending && !verifying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    when {
                        sending || verifying -> CircularProgressIndicator()
                        verdict?.match == true -> Text(text = "Enviar reporte")
                        else -> Text(text = "Verificar reporte")
                    }
                }
                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it)
                }
            }
        }
    }
}
