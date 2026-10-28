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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen

import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.SessionState
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.IncidentStatus
import com.actuagt.shared.model.ReportUpdate

// Officials-only dashboard: published reports of their zone ordered by
// ratification count, with a public response thread per report.
object OfficialDashboardScreen : Screen {
    @Composable
    override fun Content() {
        val sessionModel = rememberScreenModel { AuthGraph.sessionScreenModel() }
        val model = rememberScreenModel { OfficialDashboardScreenModel() }
        val session by sessionModel.sessionState.collectAsState()
        val access by model.access.collectAsState()
        val reports by model.reports.collectAsState()
        val resolvingId by model.resolvingId.collectAsState()
        val expandedId by model.expandedId.collectAsState()
        val reply by model.reply.collectAsState()
        val updates by model.updates.collectAsState()
        val reviewQueue by model.reviewQueue.collectAsState()
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
                    val requestModel = rememberScreenModel { OfficialRequestScreenModel() }
                    OfficialRequestForm(requestModel)
                }
                is OfficialDashboardScreenModel.Access.Granted -> {
                    Text(text = "Zona: ${current.zone} • ${reports.size} reportes")
                    if (reviewQueue.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pruebas por revisar (${reviewQueue.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        reviewQueue.forEach { report ->
                            ProofReviewRow(
                                report = report,
                                reply = reply,
                                resolving = resolvingId == report.id,
                                onReplyChange = model::onReplyChange,
                                onApprove = { model.approveProof(report) },
                                onReject = { model.rejectProof(report) },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(reports, key = { it.id }) { report ->
                            OfficialReportRow(
                                report = report,
                                expanded = expandedId == report.id,
                                updates = if (expandedId == report.id) updates else emptyList(),
                                reply = reply,
                                resolving = resolvingId == report.id,
                                onToggle = { model.toggleExpanded(report) },
                                onReplyChange = model::onReplyChange,
                                onTakeCase = { model.takeCase(report) },
                                onSendReply = { model.sendReply(report) },
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
    expanded: Boolean,
    updates: List<ReportUpdate>,
    reply: String,
    resolving: Boolean,
    onToggle: () -> Unit,
    onReplyChange: (String) -> Unit,
    onTakeCase: () -> Unit,
    onSendReply: () -> Unit,
    onResolve: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp).clickable(onClick = onToggle)) {
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
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Respuestas oficiales", style = MaterialTheme.typography.labelLarge)
                if (updates.isEmpty()) {
                    Text(
                        text = "Aún sin respuestas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                updates.forEach { update ->
                    Text(
                        text = "• ${update.message.ifBlank { "(cambio de estado)" }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reply,
                    onValueChange = onReplyChange,
                    label = { Text(text = "Respuesta pública") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onTakeCase,
                        enabled = !resolving && report.status == IncidentStatus.PUBLISHED,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Tomar caso")
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    OutlinedButton(
                        onClick = onSendReply,
                        enabled = !resolving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "Responder")
                    }
                }
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
            } else {
                Text(
                    text = "Tocar para responder",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProofReviewRow(
    report: IncidentReport,
    reply: String,
    resolving: Boolean,
    onReplyChange: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = report.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Evidencia original: ${report.photoUrls.size} fotos • Motivo: ${report.proofReviewReason.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = reply,
                onValueChange = onReplyChange,
                label = { Text(text = "Motivo (para rechazar)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !resolving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Rechazar prueba")
                }
                Spacer(modifier = Modifier.padding(4.dp))
                Button(
                    onClick = onApprove,
                    enabled = !resolving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (resolving) {
                        CircularProgressIndicator()
                    } else {
                        Text(text = "Aprobar prueba")
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialRequestForm(model: OfficialRequestScreenModel) {
    val fullName by model.fullName.collectAsState()
    val institution by model.institution.collectAsState()
    val position by model.position.collectAsState()
    val email by model.email.collectAsState()
    val zone by model.zone.collectAsState()
    val credential by model.credential.collectAsState()
    val sending by model.sending.collectAsState()
    val message by model.message.collectAsState()

    Column {
        Text(
            text = "Esta pantalla es solo para funcionarios. Si trabajás en una institución, solicitá acceso:",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = fullName,
            onValueChange = model::onFullNameChange,
            label = { Text(text = "Nombre completo") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = institution,
            onValueChange = model::onInstitutionChange,
            label = { Text(text = "Institución") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = position,
            onValueChange = model::onPositionChange,
            label = { Text(text = "Cargo") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = email,
            onValueChange = model::onEmailChange,
            label = { Text(text = "Correo institucional") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = zone,
            onValueChange = model::onZoneChange,
            label = { Text(text = "Zona (municipio)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (credential == null) "Sin credencial" else "Credencial adjunta",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = model::attachCredential, enabled = !sending) {
                Text(text = "Fotografiar credencial")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = model::submit, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
            if (sending) {
                CircularProgressIndicator()
            } else {
                Text(text = "Solicitar acceso")
            }
        }
        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it)
        }
    }
}
