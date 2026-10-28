package com.actuagt.shared.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.AuthRepository
import com.actuagt.shared.data.DataGraph
import com.actuagt.shared.data.ReportRepository
import com.actuagt.shared.media.CameraCapture
import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.media.NeutralizedText
import com.actuagt.shared.media.VoiceInput
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.Ratification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

// Backs FeedScreen: exposes the verified feed, the same-zone ratification
// queue, and the ratification flow (photo + description + voice) for the
// selected report. Only registered (non-anonymous) citizens with a zone see
// the queue and can ratify.
class FeedScreenModel(
    private val authRepository: AuthRepository = AuthGraph.authRepository,
    private val repository: ReportRepository = DataGraph.reportRepository,
) : ScreenModel {

    val feed: StateFlow<List<IncidentReport>> = repository.verifiedFeed
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val ratificationQueue: StateFlow<List<IncidentReport>> =
        authRepository.session.flatMapLatest { user ->
            val queue: Flow<List<IncidentReport>> = if (user == null || user.isAnonymous) {
                flowOf(emptyList())
            } else {
                val zone = authRepository.getUserProfile(user.uid)?.zone.orEmpty()
                if (zone.isBlank()) flowOf(emptyList())
                else repository.observeRatificationQueue(zone)
            }
            queue
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<IncidentReport?>(null)
    val selected: StateFlow<IncidentReport?> = _selected

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _photo = MutableStateFlow<CapturedPhoto?>(null)
    val photo: StateFlow<CapturedPhoto?> = _photo

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    private val _confirmText = MutableStateFlow<String?>(null)
    val confirmText: StateFlow<String?> = _confirmText

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
        _message.value = null
    }

    // Voice dictation for the ratification description: transcribe on-device,
    // neutralise through the Meta assistant, and ask the user to confirm.
    fun dictate() {
        if (_listening.value || _sending.value) return
        screenModelScope.launch {
            _listening.value = true
            _message.value = null
            val transcript = VoiceInput().transcribe()
            _listening.value = false
            if (transcript.isNullOrBlank()) {
                _message.value = "No se pudo transcribir. Inténtalo de nuevo."
                return@launch
            }
            repository.neutralizeText(transcript).fold(
                onSuccess = { neutralized ->
                    when (neutralized) {
                        is NeutralizedText.Clean -> _confirmText.value = neutralized.text
                        NeutralizedText.SecurityAlert -> _message.value =
                            "ActuaGT solo atiende infraestructura. Lo relatado parece un tema " +
                                "de seguridad y no puede publicarse aquí."
                    }
                },
                onFailure = {
                    _message.value = "El servicio de limpieza no está disponible. Escribí la descripción manualmente."
                },
            )
        }
    }

    fun confirmDictation() {
        _confirmText.value?.let { _description.value = it }
        _confirmText.value = null
        _message.value = null
    }

    fun editDictation() {
        _confirmText.value?.let { _description.value = it }
        _confirmText.value = null
    }

    fun dismissDictation() {
        _confirmText.value = null
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
        // The server only counts ratifications with photo evidence and a
        // written description, so both are required up front.
        if (_photo.value == null) {
            _message.value = "Adjuntá una foto como evidencia."
            return
        }
        if (_description.value.isBlank()) {
            _message.value = "Describí lo que viste para que tu ratificación cuente."
            return
        }
        screenModelScope.launch {
            _sending.value = true
            _message.value = null
            val result = runCatching {
                val photoUrl = repository.uploadReportPhoto(report.id, _photo.value!!).getOrThrow()
                repository.addRatification(
                    Ratification(
                        reportId = report.id,
                        userUid = userUid,
                        photoUrl = photoUrl,
                        comment = _description.value.trim(),
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
