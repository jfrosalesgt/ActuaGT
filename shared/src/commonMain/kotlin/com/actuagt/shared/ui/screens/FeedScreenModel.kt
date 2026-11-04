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
import com.actuagt.shared.media.userMessageForCleanupFailure
import com.actuagt.shared.media.UserLocation
import com.actuagt.shared.media.UserLocationName
import com.actuagt.shared.media.VoiceInput
import com.actuagt.shared.model.DEFAULT_MAP_LABEL
import com.actuagt.shared.model.DEFAULT_MAP_LATITUDE
import com.actuagt.shared.model.DEFAULT_MAP_LONGITUDE
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.Ratification
import com.actuagt.shared.model.sortedByDistanceFrom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

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

    // Zone membership changes rarely (only via official approval), so the
    // profile lookup is cached per uid instead of refetched on every
    // session emission.
    private var cachedZoneUid: String? = null
    private var cachedZone: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val ratificationQueue: StateFlow<List<IncidentReport>> =
        authRepository.session.flatMapLatest { user ->
            val queue: Flow<List<IncidentReport>> = if (user == null || user.isAnonymous) {
                flowOf(emptyList())
            } else {
                val zone = if (user.uid == cachedZoneUid) {
                    cachedZone.orEmpty()
                } else {
                    val fresh = authRepository.getUserProfile(user.uid)?.zone
                    cachedZoneUid = user.uid
                    cachedZone = fresh
                    fresh.orEmpty()
                }
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

    // The Meta assistant takes a while to order the idea: expose it so
    // the UI shows progress instead of going idle.
    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing

    private val _confirmText = MutableStateFlow<String?>(null)
    val confirmText: StateFlow<String?> = _confirmText

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // Map focus point chosen by the citizen (map tap or GPS). Null means no
    // selection yet: the map centers the Parque Central default.
    private val _mapLatitude = MutableStateFlow<Double?>(null)
    val mapLatitude: StateFlow<Double?> = _mapLatitude

    private val _mapLongitude = MutableStateFlow<Double?>(null)
    val mapLongitude: StateFlow<Double?> = _mapLongitude

    // Display name of the focus point, resolved from the map metadata.
    // Null falls back to the default label in the UI.
    private val _mapLocationName = MutableStateFlow<String?>(null)
    val mapLocationName: StateFlow<String?> = _mapLocationName

    private val _locatingMap = MutableStateFlow(false)
    val locatingMap: StateFlow<Boolean> = _locatingMap

    private val mapCenter: StateFlow<Triple<Double, Double, String?>> =
        combine(_mapLatitude, _mapLongitude, _mapLocationName) { lat, lng, name ->
            Triple(lat ?: DEFAULT_MAP_LATITUDE, lng ?: DEFAULT_MAP_LONGITUDE, name)
        }.stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Triple(DEFAULT_MAP_LATITUDE, DEFAULT_MAP_LONGITUDE, null),
        )

    // Both lists nearest-first from the current map focus.
    val sortedFeed: StateFlow<List<IncidentReport>> =
        combine(feed, mapCenter) { reports, center ->
            reports.sortedByDistanceFrom(center.first, center.second)
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortedQueue: StateFlow<List<IncidentReport>> =
        combine(ratificationQueue, mapCenter) { reports, center ->
            reports.sortedByDistanceFrom(center.first, center.second)
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectMapPoint(latitude: Double, longitude: Double) {
        _mapLatitude.value = latitude
        _mapLongitude.value = longitude
        screenModelScope.launch {
            _mapLocationName.value =
                UserLocationName().municipalityName(latitude, longitude)?.takeIf { it.isNotBlank() }
        }
    }

    fun clearMapPoint() {
        _mapLatitude.value = null
        _mapLongitude.value = null
        _mapLocationName.value = null
    }

    fun locateMapPoint() {
        if (_locatingMap.value) return
        screenModelScope.launch {
            _locatingMap.value = true
            val coordinates = UserLocation().currentCoordinates()
            _locatingMap.value = false
            if (coordinates == null) {
                _message.value = "No se pudo obtener tu ubicación."
            } else {
                selectMapPoint(coordinates.latitude, coordinates.longitude)
            }
        }
    }

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
            val voice = VoiceInput()
            val transcript = voice.transcribe()
            _listening.value = false
            if (transcript.isNullOrBlank()) {
                // Silence cancels on its own; only real failures complain.
                _message.value = if (voice.wasSilentEnd()) {
                    null
                } else {
                    "No se pudo transcribir. Inténtalo de nuevo."
                }
                return@launch
            }
            _processing.value = true
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
                    _message.value = userMessageForCleanupFailure(it)
                },
            )
            _processing.value = false
        }
    }

    // Ends the capture early through the Detener button instead of
    // waiting for the silence timeout.
    fun stopDictation() {
        if (!_listening.value) return
        VoiceInput().stopListening()
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
        if (!CameraCapture().isCaptureAvailable()) {
            _message.value = "Este dispositivo no tiene cámara disponible."
            return
        }
        screenModelScope.launch {
            if (!CameraCapture().ensureAccess()) {
                _message.value = "Permití el acceso a la cámara para tomar la foto."
                return@launch
            }
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
                val photoUrl = uploadPhotoWithRetry(report.id, _photo.value!!).getOrThrow()
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

    // Same transient-network retry as the report submit: the ratification
    // draft survives a failed upload.
    private suspend fun uploadPhotoWithRetry(
        reportId: String,
        photo: CapturedPhoto,
        attempts: Int = 2,
    ): Result<String> {
        var lastError: Throwable? = null
        repeat(attempts) {
            val result = repository.uploadReportPhoto(reportId, photo)
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("Photo upload failed"))
    }
}
