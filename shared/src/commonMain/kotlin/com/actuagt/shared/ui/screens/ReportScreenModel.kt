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
import com.actuagt.shared.media.encodeVerificationImage
import com.actuagt.shared.media.verificationDataUrl
import com.actuagt.shared.model.CoherenceVerdict
import com.actuagt.shared.model.IncidentReport
import com.actuagt.shared.model.suggestCategoryCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

// Backs ReportScreen: citizen report with real camera evidence, submitted to
// the moderation inbox. The moderator requires at least one photo.
class ReportScreenModel(
    private val authRepository: AuthRepository = AuthGraph.authRepository,
    private val repository: ReportRepository = DataGraph.reportRepository,
) : ScreenModel {

    data class Category(val code: String, val label: String)

    val categories: List<Category> = listOf(
        Category("VIALIDAD", "Vialidad"),
        Category("AGUA_Y_DRENAJES", "Agua y drenajes"),
        Category("ALUMBRADO", "Alumbrado"),
        Category("DESECHOS_Y_AMBIENTE", "Desechos y ambiente"),
        Category("ESPACIOS_PUBLICOS", "Espacios públicos"),
        Category("OTROS", "Otros"),
    )

    companion object {
        const val MAX_PHOTOS = 4
    }

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description

    private val _category = MutableStateFlow(categories.first())
    val category: StateFlow<Category> = _category

    private val _zone = MutableStateFlow("")
    val zone: StateFlow<String> = _zone

    private val _photos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    val photos: StateFlow<List<CapturedPhoto>> = _photos

    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude: StateFlow<Double?> = _latitude

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude: StateFlow<Double?> = _longitude

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    // The Meta assistant takes a while (tens of seconds) to order the
    // idea: expose it so the UI shows progress instead of going idle.
    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing

    private val _confirmText = MutableStateFlow<NeutralizedText.Clean?>(null)
    val confirmText: StateFlow<NeutralizedText.Clean?> = _confirmText

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // Verdict of the pre-submit AI coherence check plus the uploaded photo
    // URLs and pending id it was computed from. Any form edit invalidates
    // it: the button goes back to "Verificar reporte".
    private val _verdict = MutableStateFlow<CoherenceVerdict?>(null)
    val verdict: StateFlow<CoherenceVerdict?> = _verdict

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying

    // The verdict is computed from downscaled photo bytes sent inline, so
    // verification uploads nothing: Storage receives the full-resolution
    // photos only inside submit(). Editing any field clears the verdict.

    // True once the user picks a category by hand: automatic suggestions
    // from the description must not override that choice afterwards.
    private var categoryManuallySet = false

    private fun applyCategorySuggestion() {
        val code = suggestCategoryCode(_title.value, _description.value)
        categories.firstOrNull { it.code == code }?.let { _category.value = it }
    }

    private fun invalidateVerification() {
        _verdict.value = null
    }

    fun onTitleChange(value: String) {
        _title.value = value
        _message.value = null
        invalidateVerification()
        if (!categoryManuallySet) applyCategorySuggestion()
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
        _message.value = null
        invalidateVerification()
        if (!categoryManuallySet) applyCategorySuggestion()
    }

    fun onCategoryChange(value: Category) {
        categoryManuallySet = true
        _category.value = value
        _message.value = null
        invalidateVerification()
    }

    fun onZoneChange(value: String) {
        _zone.value = value
        _message.value = null
        invalidateVerification()
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
            if (_photos.value.size >= MAX_PHOTOS) {
                _message.value = "Máximo $MAX_PHOTOS fotos por reporte."
                return@launch
            }
            if (!CameraCapture().ensureAccess()) {
                _message.value = "Permití el acceso a la cámara para tomar la foto."
                return@launch
            }
            val photo = CameraCapture().takePhoto()
            if (photo == null) {
                _message.value = "No se pudo abrir la cámara o tomar la foto. Inténtalo de nuevo."
            } else {
                _photos.value += photo
                invalidateVerification()
            }
        }
    }

    fun removePhoto(index: Int) {
        _photos.value = _photos.value.filterIndexed { i, _ -> i != index }
        invalidateVerification()
    }

    // Both the map tap and the GPS fix land here. The point owns the zone:
    // resolve the municipality from the map metadata and fill it, keeping
    // the typed value only when the lookup yields nothing.
    fun onLocationChange(latitude: Double, longitude: Double) {
        _latitude.value = latitude
        _longitude.value = longitude
        _message.value = null
        invalidateVerification()
        screenModelScope.launch {
            val municipality = UserLocationName().municipalityName(latitude, longitude)
                ?.takeIf { it.isNotBlank() } ?: return@launch
            // A newer point wins over this lookup when taps come in fast.
            if (_latitude.value != latitude || _longitude.value != longitude) return@launch
            _zone.value = municipality
        }
    }

    // GPS fix for the report point: permission-gated, null-safe. When it
    // fails the user can still tap the map to position the point by hand.
    fun locateMe() {
        if (_locating.value || _sending.value) return
        screenModelScope.launch {
            _locating.value = true
            _message.value = null
            val coordinates = UserLocation().currentCoordinates()
            _locating.value = false
            if (coordinates == null) {
                _message.value = "No se pudo obtener tu ubicación. Tocá el mapa para marcar el punto."
            } else {
                onLocationChange(coordinates.latitude, coordinates.longitude)
            }
        }
    }

    // Voice dictation for title and description: transcribe on-device, let
    // the Meta assistant order the idea, and ask the user to confirm. The
    // raw transcript is never persisted. stopDictation ends the capture
    // early through the Detener button instead of waiting for silence.
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
                        is NeutralizedText.Clean -> _confirmText.value = neutralized
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

    fun stopDictation() {
        if (!_listening.value) return
        VoiceInput().stopListening()
    }

    fun confirmDictation() {
        _confirmText.value?.let {
            _title.value = it.title
            _description.value = it.text
        }
        _confirmText.value = null
        _message.value = null
        categoryManuallySet = false
        applyCategorySuggestion()
        invalidateVerification()
    }

    fun editDictation() {
        // Copy to the fields so the user can adjust them by hand.
        _confirmText.value?.let {
            _title.value = it.title
            _description.value = it.text
        }
        _confirmText.value = null
        categoryManuallySet = false
        applyCategorySuggestion()
        invalidateVerification()
    }

    fun dismissDictation() {
        _confirmText.value = null
    }

    // Step 1 of the two-step submit: validates the form, re-runs the neutral
    // cleanup on the current title and description (the user may have edited
    // them after dictation), uploads the photos and asks the Meta assistant
    // whether they show the reported problem. The verdict card tells the
    // citizen if sending is viable and why not.
    fun verifyReport() {
        if (_verifying.value || _sending.value) return
        if (!validateForm()) return
        screenModelScope.launch {
            _verifying.value = true
            _message.value = null
            val title = _title.value.trim()
            val description = _description.value.trim()
            val cleaned = runCatching {
                repository.neutralizeText("$title. $description").getOrThrow()
            }.getOrElse {
                _verifying.value = false
                invalidateVerification()
                _message.value = "No se pudo verificar el reporte. Revisá tu conexión e inténtalo de nuevo."
                return@launch
            }
            var adoptedText = false
            when (val outcome = resolveVerifyText(title, description, cleaned)) {
                VerifyTextOutcome.BlockedSecurity -> {
                    _verifying.value = false
                    invalidateVerification()
                    _message.value = "ActuaGT solo atiende infraestructura. Lo relatado parece un tema " +
                        "de seguridad y no puede publicarse aquí."
                    return@launch
                }
                is VerifyTextOutcome.Adopt -> {
                    _title.value = outcome.title
                    _description.value = outcome.text
                    if (!categoryManuallySet) applyCategorySuggestion()
                    adoptedText = true
                }
                VerifyTextOutcome.Keep -> Unit
            }
            // Downscaled bytes travel inside the coherence call itself:
            // nothing reaches Storage when the user abandons the draft.
            val encoded = _photos.value.map { photo ->
                encodeVerificationImage(photo.bytes)?.let(::verificationDataUrl)
            }
            if (encoded.any { it == null }) {
                _verifying.value = false
                invalidateVerification()
                _message.value = "No se pudo procesar una foto. Tomala de nuevo."
                return@launch
            }
            val result = runCatching {
                repository.verifyReportCoherence(
                    _title.value.trim(),
                    _description.value.trim(),
                    encoded.filterNotNull(),
                ).getOrThrow()
            }
            _verifying.value = false
            result.fold(
                onSuccess = { verdict ->
                    _verdict.value = verdict
                    _message.value = if (adoptedText) {
                        "Ajustamos el título y la descripción para mantener un tono neutral."
                    } else {
                        null
                    }
                },
                onFailure = {
                    invalidateVerification()
                    _message.value = "No se pudo verificar el reporte. Revisá tu conexión e inténtalo de nuevo."
                },
            )
        }
    }

    // Step 2: only a fresh matching verdict lets the report through. A
    // service failure during verification never produces a verdict, so the
    // submit stays blocked instead of guessing.
    fun submit() {
        if (_sending.value) return
        if (!validateForm()) return
        val verdict = _verdict.value
        if (verdict == null) {
            _message.value = "Verificá el reporte con la IA antes de enviarlo."
            return
        }
        if (!verdict.match) {
            val reason = verdict.reason.ifBlank { "las fotos no muestran lo descrito" }
            _message.value = "Las fotos no coinciden con el reporte ($reason). " +
                "Ajustá las fotos o el texto y volvé a verificar."
            return
        }
        screenModelScope.launch {
            _sending.value = true
            _message.value = null
            val result = runCatching {
                val uid = authRepository.session.first()?.uid
                    ?: throw IllegalStateException("Sesión no disponible")
                val firstMetadata = _photos.value.first().metadata
                val latitude = _latitude.value ?: firstMetadata.latitude
                val longitude = _longitude.value ?: firstMetadata.longitude
                if (latitude == null || longitude == null) {
                    throw IllegalStateException("Ubicación no disponible")
                }
                // First and only Storage write: full-resolution photos land
                // here, with stable names, at the moment the report is sent.
                val reportId = repository.newPendingReportId()
                val photoUrls = _photos.value.mapIndexed { index, photo ->
                    uploadPhotoWithRetry(reportId, photo, index).getOrThrow()
                }
                repository.savePendingReport(
                    IncidentReport(
                        id = reportId,
                        title = _title.value.trim(),
                        description = _description.value.trim(),
                        category = _category.value.code,
                        latitude = latitude,
                        longitude = longitude,
                        photoUrls = photoUrls,
                        zone = _zone.value.trim(),
                        reportedByUid = uid,
                        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    ),
                ).getOrThrow()
            }
            _sending.value = false
            _message.value = result.fold(
                onSuccess = { "Reporte enviado. Quedará visible al reunir 3 ratificaciones." },
                onFailure = { "No se pudo enviar el reporte. Revisá tu conexión e inténtalo de nuevo." },
            )
            if (result.isSuccess) {
                _title.value = ""
                _description.value = ""
                _zone.value = ""
                _category.value = categories.first()
                categoryManuallySet = false
                _photos.value = emptyList()
                _latitude.value = null
                _longitude.value = null
                invalidateVerification()
            }
        }
    }

    private fun validateForm(): Boolean {
        if (_title.value.isBlank() || _description.value.isBlank()) {
            _message.value = "Completá el título y la descripción."
            return false
        }
        if (_zone.value.isBlank()) {
            _message.value = "Indicá la zona del reporte."
            return false
        }
        if (_photos.value.isEmpty()) {
            _message.value = "Agregá al menos una foto como evidencia."
            return false
        }
        // Manual/GPS point first, photo EXIF as fallback. Without either the
        // report cannot be placed on the map.
        val firstMetadata = _photos.value.first().metadata
        val latitude = _latitude.value ?: firstMetadata.latitude
        val longitude = _longitude.value ?: firstMetadata.longitude
        if (latitude == null || longitude == null) {
            _message.value = "Marcá la ubicación del problema en el mapa."
            return false
        }
        return true
    }

    // Mobile networks drop mid-upload: one transparent retry before
    // surfacing the failure. The draft is kept, so the user never loses it.
    private suspend fun uploadPhotoWithRetry(
        reportId: String,
        photo: CapturedPhoto,
        photoIndex: Int = 0,
        attempts: Int = 2,
    ): Result<String> {
        var lastError: Throwable? = null
        repeat(attempts) {
            val result = repository.uploadReportPhoto(reportId, photo, photoIndex)
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
        }
        return Result.failure(lastError ?: IllegalStateException("Photo upload failed"))
    }
}
