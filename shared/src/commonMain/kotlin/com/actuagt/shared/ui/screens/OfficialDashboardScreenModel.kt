package com.actuagt.shared.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.AuthRepository
import com.actuagt.shared.data.DataGraph
import com.actuagt.shared.data.ReportRepository
import com.actuagt.shared.media.CameraCapture
import com.actuagt.shared.model.IncidentReport
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
            is Access.Granted -> reportRepository.observeZoneReports(access.zone)
            else -> flowOf(emptyList())
        }
        zoneReports
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _resolvingId = MutableStateFlow<String?>(null)
    val resolvingId: StateFlow<String?> = _resolvingId

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun load(uid: String) {
        screenModelScope.launch {
            _access.value = Access.Checking
            val profile = authRepository.getUserProfile(uid)
            _access.value = if (profile?.role == UserRole.OFFICIAL && profile.zone.isNotBlank()) {
                Access.Granted(profile.zone)
            } else {
                Access.Denied
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // Captures the "finished work" proof photo and marks the report resolved.
    fun resolveWithCamera(report: IncidentReport) {
        if (_resolvingId.value != null) return
        screenModelScope.launch {
            val photo = CameraCapture().takePhoto() ?: return@launch
            _resolvingId.value = report.id
            _message.value = null
            val result = reportRepository.resolveReport(report.id, photo)
            _resolvingId.value = null
            _message.value = result.fold(
                onSuccess = { "Reporte marcado como resuelto." },
                onFailure = { "No se pudo marcar como resuelto. Inténtalo de nuevo." },
            )
        }
    }
}
