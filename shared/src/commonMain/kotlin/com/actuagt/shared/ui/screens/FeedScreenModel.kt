package com.actuagt.shared.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.actuagt.shared.data.DataGraph
import com.actuagt.shared.data.ReportRepository
import com.actuagt.shared.media.CameraCapture
import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.Ratification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

// Backs FeedScreen: exposes the verified feed and the ratification flow
// (optional photo + description) for the selected report.
class FeedScreenModel(
    private val repository: ReportRepository = DataGraph.reportRepository,
) : ScreenModel {

    val feed: StateFlow<List<IncidentReport>> = repository.verifiedFeed
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<IncidentReport?>(null)
    val selected: StateFlow<IncidentReport?> = _selected

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _photo = MutableStateFlow<CapturedPhoto?>(null)
    val photo: StateFlow<CapturedPhoto?> = _photo

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun select(report: IncidentReport) {
        _selected.value = report
        _description.value = ""
        _photo.value = null
        _message.value = null
    }

    fun clearSelection() {
        _selected.value = null
        _description.value = ""
        _photo.value = null
        _message.value = null
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
    }

    fun clearMessage() {
        _message.value = null
    }

    fun attachPhoto() {
        screenModelScope.launch {
            CameraCapture().takePhoto()?.let { _photo.value = it }
        }
    }

    fun submitRatification(userUid: String) {
        val report = _selected.value ?: return
        if (_sending.value) return
        screenModelScope.launch {
            _sending.value = true
            _message.value = null
            val result = runCatching {
                val photoUrl = _photo.value?.let {
                    repository.uploadReportPhoto(report.id, it).getOrThrow()
                }
                repository.addRatification(
                    Ratification(
                        reportId = report.id,
                        userUid = userUid,
                        photoUrl = photoUrl,
                        comment = _description.value.ifBlank { null },
                        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    ),
                ).getOrThrow()
            }
            _sending.value = false
            _message.value = result.fold(
                onSuccess = { "¡Gracias! Tu ratificación fue registrada." },
                onFailure = { "No se pudo enviar la ratificación. Inténtalo de nuevo." },
            )
            if (result.isSuccess) {
                _description.value = ""
                _photo.value = null
            }
        }
    }
}
