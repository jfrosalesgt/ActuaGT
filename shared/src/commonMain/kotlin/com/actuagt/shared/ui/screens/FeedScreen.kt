package com.actuagt.shared.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import com.actuagt.shared.ui.components.ActuaGTTextField
import com.actuagt.shared.ui.components.SectionBanner
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.SessionState
import com.actuagt.shared.model.DEFAULT_MAP_LABEL
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.categoryLabel
import com.actuagt.shared.model.distanceMeters
import com.actuagt.shared.model.formatCoordinate
import com.actuagt.shared.model.formatDistance
import com.actuagt.shared.ui.components.EmptyState
import com.actuagt.shared.ui.components.StatusChip

// Citizen report feed with map/list view switch. Tapping a report opens the
// ratification panel to confirm it with a photo and a description.
object FeedScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val feedModel = rememberScreenModel { FeedScreenModel() }
        val sessionModel = rememberScreenModel { AuthGraph.sessionScreenModel() }
        var showMap by remember { mutableStateOf(true) }
        val options = listOf("Mapa", "Lista")
        val listOptions = listOf("Ratificados", "Por ratificar")
        var listSegment by remember { mutableStateOf(0) }

        val feed by feedModel.sortedFeed.collectAsState()
        val queue by feedModel.sortedQueue.collectAsState()
        val selected by feedModel.selected.collectAsState()
        val mapLatitude by feedModel.mapLatitude.collectAsState()
        val mapLongitude by feedModel.mapLongitude.collectAsState()
        val mapLocationName by feedModel.mapLocationName.collectAsState()
        val locatingMap by feedModel.locatingMap.collectAsState()
        val listening by feedModel.listening.collectAsState()
        val confirmText by feedModel.confirmText.collectAsState()
        val session by sessionModel.sessionState.collectAsState()
        val userUid = (session as? SessionState.SignedIn)?.user?.uid.orEmpty()
        val focusName = mapLocationName ?: DEFAULT_MAP_LABEL

        confirmText?.let { cleaned ->
            AlertDialog(
                onDismissRequest = feedModel::dismissDictation,
                title = { Text(text = "Confirmá el texto") },
                text = { Text(text = cleaned) },
                confirmButton = {
                    TextButton(onClick = feedModel::confirmDictation) {
                        Text(text = "Usar este texto")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = feedModel::dismissDictation) {
                            Text(text = "Descartar")
                        }
                        TextButton(onClick = feedModel::editDictation) {
                            Text(text = "Editar")
                        }
                    }
                },
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            SectionBanner(
                title = "Inicio",
                subtitle = "Reportes verificados y casos por validar cerca de ti",
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = (index == 0) == showMap,
                        onClick = { showMap = index == 0 },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                    ) {
                        Text(text = label)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (showMap) {
                ReportMap(
                    reports = queue + feed,
                    onSelect = { feedModel.select(it) },
                    selectedLatitude = mapLatitude,
                    selectedLongitude = mapLongitude,
                    onMapTap = { lat, lng -> feedModel.selectMapPoint(lat, lng) },
                    onMyLocationClick = { feedModel.locateMapPoint() },
                    locating = locatingMap,
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ratificados: ${feed.size} · Por ratificar: ${queue.size}",
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                Text(
                    text = focusName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = listSegment == index,
                            onClick = { listSegment = index },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = listOptions.size,
                            ),
                        ) {
                            Text(text = label)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val visible = if (listSegment == 0) feed else queue
                if (visible.isEmpty()) {
                    EmptyState(
                        text = if (listSegment == 0) {
                            "Aún no hay reportes ratificados."
                        } else {
                            "No hay reportes pendientes de ratificar."
                        },
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(visible, key = { it.id }) { report ->
                            ReportRow(
                                report = report,
                                distanceLabel = distanceLabel(
                                    report = report,
                                    focusLatitude = mapLatitude,
                                    focusLongitude = mapLongitude,
                                ),
                                onClick = { feedModel.select(report) },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            selected?.let { report ->
                Spacer(modifier = Modifier.height(12.dp))
                RatifyPanel(
                    report = report,
                    userUid = userUid,
                    listening = listening,
                    model = feedModel,
                )
            }
        }
    }
}

@Composable
private fun ReportRow(
    report: IncidentReport,
    distanceLabel: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(status = report.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = report.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Ratificaciones: ${report.ratificationsCount}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                if (distanceLabel != null) {
                    Text(
                        text = distanceLabel,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun distanceLabel(
    report: IncidentReport,
    focusLatitude: Double?,
    focusLongitude: Double?,
): String? {
    if (focusLatitude == null || focusLongitude == null) return null
    if (report.latitude == 0.0 && report.longitude == 0.0) return null
    val meters = distanceMeters(
        focusLatitude,
        focusLongitude,
        report.latitude,
        report.longitude,
    )
    return "a ${formatDistance(meters)}"
}

@Composable
private fun RatifyPanel(
    report: IncidentReport,
    userUid: String,
    listening: Boolean,
    model: FeedScreenModel,
) {
    val description by model.description.collectAsState()
    val photoAttached = model.photo.collectAsState().value != null
    val sending by model.sending.collectAsState()
    val processing by model.processing.collectAsState()
    val message by model.message.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = "Ratificar: ${report.title}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { model.clearSelection() }) {
                    Text(text = "Cerrar")
                }
            }
            Text(
                text = buildString {
                    append(categoryLabel(report.category))
                    append(" · ${report.ratificationsCount} ratificaciones")
                    if (report.zone.isNotBlank()) append(" · ${report.zone}")
                    if (report.latitude != 0.0 || report.longitude != 0.0) {
                        append(" · Punto: ${formatCoordinate(report.latitude)}, ${formatCoordinate(report.longitude)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¿Qué viste?",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (listening) {
                    OutlinedButton(onClick = { model.stopDictation() }) {
                        Text(text = "Detener")
                    }
                } else {
                    OutlinedButton(
                        onClick = { model.dictate() },
                        enabled = !sending,
                    ) {
                        Text(text = "Dictar por voz")
                    }
                }
            }
            Text(
                text = "Contanos qué ves con tu voz: la IA ordena la idea y completa la descripción. Tocá Detener cuando termines.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (processing) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(text = "Ordenando las ideas, espera un momento")
                }
            }
            message?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it)
            }
            Spacer(modifier = Modifier.height(4.dp))
            ActuaGTTextField(
                value = description,
                onValueChange = { model.onDescriptionChange(it) },
                label = "¿Qué viste?",
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { model.attachPhoto() },
                enabled = !sending,
            ) {
                Text(text = if (photoAttached) "Foto adjunta" else "Adjuntar foto")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { model.submitRatification(userUid) },
                enabled = !sending && userUid.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Enviar ratificación")
            }
            if (sending) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }
            message?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it)
            }
        }
    }
}
