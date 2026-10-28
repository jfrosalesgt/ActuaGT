package com.actuagt.shared.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.AuthRepository
import com.actuagt.shared.data.DataGraph
import com.actuagt.shared.data.ReportRepository
import com.actuagt.shared.media.CameraCapture
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.OfficialRequest
import com.actuagt.shared.model.ReportUpdate
import com.actuagt.shared.model.UserRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Backs OfficialDashboardScreen: grants access only to OFFICIAL profiles,
// then streams their zone reports ordered by ratification count.
class OfficialDashboardScreenModel(
    private val authRepository: AuthRepository = AuthGraph.authRepository,
    private val reportRepository: ReportRepository = DataGraph.reportRepository,
) : ScreenModel {

    companion object {
        // Admin sentinel: sees published reports and proof reviews of every zone.
        const val ALL_ZONES = "*"
    }

    sealed interface Access {
        data object Checking : Access

        data object Denied : Access

        data class Granted(val zone: String) : Access
    }

    private val _access = MutableStateFlow<Access>(Access.Checking)
    val access: StateFlow<Access> = _access

    @OptIn(ExperimentalCoroutinesApi::class)
    val reports: StateFlow<List<IncidentReport>> = access.flatMapLatest { access ->
        val zoneReports: Flow<List<IncidentReport>> = when (access) {
            is Access.Granted ->
                if (access.zone == ALL_ZONES) reportRepository.observeAllPublishedReports()
                else reportRepository.observeZoneReports(access.zone)
            else -> flowOf(emptyList())
        }
        zoneReports
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val reviewQueue: StateFlow<List<IncidentReport>> = access.flatMapLatest { access ->
        val queue: Flow<List<IncidentReport>> = when (access) {
            is Access.Granted ->
                if (access.zone == ALL_ZONES) reportRepository.observeAllProofReviews()
                else reportRepository.observeProofReviews(access.zone)
            else -> flowOf(emptyList())
        }
        queue
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    val requests: StateFlow<List<OfficialRequest>> = isAdmin.flatMapLatest { admin ->
        if (admin) reportRepository.observeOfficialRequests()
        else flowOf(emptyList())
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _resolvingId = MutableStateFlow<String?>(null)
    val resolvingId: StateFlow<String?> = _resolvingId

    private val _expandedId = MutableStateFlow<String?>(null)
    val expandedId: StateFlow<String?> = _expandedId

    private val _reply = MutableStateFlow("")
    val reply: StateFlow<String> = _reply

    @OptIn(ExperimentalCoroutinesApi::class)
    val updates: StateFlow<List<ReportUpdate>> = expandedId.flatMapLatest { reportId ->
        val stream: Flow<List<ReportUpdate>> = reportId?.let {
            reportRepository.observeReportUpdates(it)
        } ?: flowOf(emptyList())
        stream
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun load(uid: String) {
        screenModelScope.launch {
            _access.value = Access.Checking
            val profile = authRepository.getUserProfile(uid)
            _isAdmin.value = profile?.role == UserRole.ADMIN
            val zone = when {
                profile?.role == UserRole.OFFICIAL && profile.zone.isNotBlank() -> profile.zone
                profile?.role == UserRole.ADMIN -> ALL_ZONES
                else -> null
            }
            _access.value = if (zone != null) Access.Granted(zone) else Access.Denied
        }
    }

    fun approveRequest(request: OfficialRequest) {
        if (_resolvingId.value != null) return
        screenModelScope.launch {
            _resolvingId.value = request.uid
            _message.value = null
            val result = reportRepository.setOfficialRole(
                uid = request.uid,
                zone = request.zone,
                institution = request.institution,
                approved = true,
                reviewReason = null,
            )
            _resolvingId.value = null
            _message.value = result.fold(
                onSuccess = { "Funcionario aprobado." },
                onFailure = { "No se pudo aprobar. Inténtalo de nuevo." },
            )
        }
    }

    fun rejectRequest(request: OfficialRequest) {
        if (_resolvingId.value != null) return
        if (_reply.value.isBlank()) {
            _message.value = "Escribí el motivo del rechazo antes de rechazar la solicitud."
            return
        }
        screenModelScope.launch {
            _resolvingId.value = request.uid
            _message.value = null
            val result = reportRepository.setOfficialRole(
                uid = request.uid,
                zone = null,
                institution = null,
                approved = false,
                reviewReason = _reply.value,
            )
            _resolvingId.value = null
            _message.value = result.fold(
                onSuccess = {
                    _reply.value = ""
                    "Solicitud rechazada."
                },
                onFailure = { "No se pudo rechazar. Inténtalo de nuevo." },
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun toggleExpanded(report: IncidentReport) {
        _expandedId.value = if (_expandedId.value == report.id) null else report.id
        _reply.value = ""
        _message.value = null
    }

    fun onReplyChange(value: String) {
        _reply.value = value
        _message.value = null
    }

    // Takes a published case into management.
    fun takeCase(report: IncidentReport) {
        actOn(report, status = "in_progress", withPhoto = false)
    }

    fun sendReply(report: IncidentReport) {
        if (_reply.value.isBlank()) {
            _message.value = "Escribí una respuesta antes de enviarla."
            return
        }
        actOn(report, status = null, withPhoto = false)
    }

    // Captures the "finished work" proof photo and marks the report resolved.
    // The server verifies the OFFICIAL profile and zone before accepting it.
    fun resolveWithCamera(report: IncidentReport) {
        actOn(report, status = "resolved", withPhoto = true)
    }

    fun approveProof(report: IncidentReport) {
        if (_resolvingId.value != null) return
        screenModelScope.launch {
            _resolvingId.value = report.id
            _message.value = null
            val result = reportRepository.officialRespond(
                reportId = report.id,
                message = null,
                status = null,
                proofPhoto = null,
                manualReview = "approved",
                reviewReason = null,
            )
            _resolvingId.value = null
            _message.value = result.fold(
                onSuccess = { "Prueba aprobada manualmente." },
                onFailure = { "No se pudo aprobar la prueba. Inténtalo de nuevo." },
            )
        }
    }

    fun rejectProof(report: IncidentReport) {
        if (_resolvingId.value != null) return
        if (_reply.value.isBlank()) {
            _message.value = "Escribí el motivo del rechazo antes de rechazar la prueba."
            return
        }
        screenModelScope.launch {
            _resolvingId.value = report.id
            _message.value = null
            val result = reportRepository.officialRespond(
                reportId = report.id,
                message = null,
                status = null,
                proofPhoto = null,
                manualReview = "rejected",
                reviewReason = _reply.value,
            )
            _resolvingId.value = null
            _message.value = result.fold(
                onSuccess = {
                    _reply.value = ""
                    "Prueba rechazada; el caso vuelve a estar en gestión."
                },
                onFailure = { "No se pudo rechazar la prueba. Inténtalo de nuevo." },
            )
        }
    }

    private fun actOn(report: IncidentReport, status: String?, withPhoto: Boolean) {
        if (_resolvingId.value != null) return
        screenModelScope.launch {
            val photo = if (withPhoto) {
                CameraCapture().takePhoto() ?: return@launch.also {
                    _message.value = "No se capturó la foto de obra terminada."
                }
            } else null
            val replyText = _reply.value.ifBlank { null }
            _resolvingId.value = report.id
            _message.value = null
            val result = reportRepository.officialRespond(
                reportId = report.id,
                message = replyText,
                status = status,
                proofPhoto = photo,
            )
            _resolvingId.value = null
            _message.value = result.fold(
                onSuccess = {
                    _reply.value = ""
                    "Respuesta registrada."
                },
                onFailure = { "No se pudo registrar. Revisá tu acceso de funcionario e inténtalo de nuevo." },
            )
        }
    }
}
