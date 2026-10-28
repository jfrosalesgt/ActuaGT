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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
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
import com.actuagt.shared.model.IncidentReport

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

        val feed by feedModel.feed.collectAsState()
        val queue by feedModel.ratificationQueue.collectAsState()
        val selected by feedModel.selected.collectAsState()
        val listening by feedModel.listening.collectAsState()
        val confirmText by feedModel.confirmText.collectAsState()
        val session by sessionModel.sessionState.collectAsState()
        val userUid = (session as? SessionState.SignedIn)?.user?.uid.orEmpty()

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
            Text(
                text = "Reportes ciudadanos",
                style = MaterialTheme.typography.headlineSmall,
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
            if (queue.isNotEmpty()) {
                Text(
                    text = "Validar en tu zona (${queue.size})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                queue.forEach { report ->
                    ReportRow(
                        report = report,
                        onClick = { feedModel.select(report) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (showMap) {
                Text(text = "Vista de mapa (pendiente)")
            } else if (feed.isEmpty()) {
                Text(text = "Aún no hay reportes verificados.")
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(feed, key = { it.id }) { report ->
                        ReportRow(
                            report = report,
                            onClick = { feedModel.select(report) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
private fun ReportRow(report: IncidentReport, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = report.title, style = MaterialTheme.typography.titleMedium)
            Text(text = report.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¿Qué viste?",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { model.dictate() },
                    enabled = !sending && !listening,
                ) {
                    Text(text = if (listening) "Escuchando…" else "Dictar por voz")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { model.onDescriptionChange(it) },
                label = { Text(text = "¿Qué viste?") },
                modifier = Modifier.fillMaxWidth(),
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
