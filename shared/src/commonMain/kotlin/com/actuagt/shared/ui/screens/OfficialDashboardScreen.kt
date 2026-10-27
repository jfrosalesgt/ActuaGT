package com.actuagt.shared.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.SessionState
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.IncidentStatus

// Officials-only dashboard: loads solely for OFFICIAL profiles and shows
// their zone reports ordered by ratification count, with "finished work"
// photo resolution per report.
object OfficialDashboardScreen : Screen {
    @Composable
    override fun Content() {
        val sessionModel = rememberScreenModel { AuthGraph.sessionScreenModel() }
        val model = rememberScreenModel { OfficialDashboardScreenModel() }
        val session by sessionModel.sessionState.collectAsState()
        val access by model.access.collectAsState()
        val reports by model.reports.collectAsState()
        val resolvingId by model.resolvingId.collectAsState()
        val message by model.message.collectAsState()

        LaunchedEffect(session) {
            val uid = (session as? SessionState.SignedIn)?.user?.uid
            if (uid != null) model.load(uid)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Text(
                text = "Panel oficial",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (val current = access) {
                is OfficialDashboardScreenModel.Access.Checking -> {
                    CircularProgressIndicator()
                }
                is OfficialDashboardScreenModel.Access.Denied -> {
                    Text(text = "Acceso restringido: esta pantalla es solo para funcionarios.")
                }
                is OfficialDashboardScreenModel.Access.Granted -> {
                    Text(text = "Zona: ${current.zone} • ${reports.size} reportes")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(reports, key = { it.id }) { report ->
                            OfficialReportRow(
                                report = report,
                                resolving = resolvingId == report.id,
                                onResolve = { model.resolveWithCamera(report) },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            message?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it)
            }
        }
    }
}

@Composable
private fun OfficialReportRow(
    report: IncidentReport,
    resolving: Boolean,
    onResolve: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (resolving) CircularProgressIndicator()
            }
            Text(text = report.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ratificaciones: ${report.ratificationsCount} • Estado: ${report.status.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onResolve,
                enabled = !resolving && report.status != IncidentStatus.RESOLVED,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (report.status == IncidentStatus.RESOLVED) {
                        "Obra terminada"
                    } else {
                        "Subir foto de obra terminada"
                    },
                )
            }
        }
    }
}
