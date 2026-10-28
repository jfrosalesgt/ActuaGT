package com.actuagt.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

// Single incident opened from a push notification deep link. When the
// report is gone (or never visible to this user) it says so plainly.
class ReportDetailScreen(val reportId: String) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ReportDetailScreenModel(reportId) }
        val loaded by model.loaded.collectAsState()
        val report by model.report.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Button(onClick = { navigator.pop() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Volver")
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (!loaded) {
                CircularProgressIndicator()
            } else {
                val current = report
                if (current == null) {
                    Text(text = "El reporte ya no está disponible.")
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = current.description,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Estado: ${current.status.name.lowercase()} • " +
                                    "Ratificaciones: ${current.ratificationsCount} • " +
                                    "Fotos: ${current.photoUrls.size}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
