package com.actuagt.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen

// Citizen report form: real camera evidence plus neutral text, submitted to
// the moderation inbox. Reports go public only after 3 ratifications.
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
        val confirmText by model.confirmText.collectAsState()
        val message by model.message.collectAsState()

        confirmText?.let { cleaned ->
            AlertDialog(
                onDismissRequest = model::dismissDictation,
                title = { Text(text = "Confirmá el texto") },
                text = { Text(text = cleaned) },
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
                Text(
                    text = "Nuevo reporte",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = model::onTitleChange,
                    label = { Text(text = "Título") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Descripción",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = model::dictate,
                        enabled = !sending && !listening,
                    ) {
                        Text(text = if (listening) "Escuchando…" else "Dictar por voz")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = model::onDescriptionChange,
                    label = { Text(text = "Descripción") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
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
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = zone,
                    onValueChange = model::onZoneChange,
                    label = { Text(text = "Zona (municipio)") },
                    modifier = Modifier.fillMaxWidth(),
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
                Button(
                    onClick = model::submit,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (sending) {
                        CircularProgressIndicator()
                    } else {
                        Text(text = "Enviar reporte")
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
