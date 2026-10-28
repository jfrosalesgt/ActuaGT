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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    private val _confirmText = MutableStateFlow<String?>(null)
    val confirmText: StateFlow<String?> = _confirmText

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun onTitleChange(value: String) {
        _title.value = value
        _message.value = null
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
        _message.value = null
    }

    fun onCategoryChange(value: Category) {
        _category.value = value
        _message.value = null
    }

    fun onZoneChange(value: String) {
        _zone.value = value
        _message.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun attachPhoto() {
        screenModelScope.launch {
            if (_photos.value.size >= MAX_PHOTOS) {
                _message.value = "Máximo $MAX_PHOTOS fotos por reporte."
                return@launch
            }
            CameraCapture().takePhoto()?.let { _photos.value += it }
        }
    }

    fun removePhoto(index: Int) {
        _photos.value = _photos.value.filterIndexed { i, _ -> i != index }
    }

    // Voice dictation for the description: transcribe on-device, neutralise
    // through the Meta assistant, and ask the user to confirm. The raw
    // transcript is never persisted.
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
        // Copy to the field so the user can adjust it by hand.
        _confirmText.value?.let { _description.value = it }
        _confirmText.value = null
    }

    fun dismissDictation() {
        _confirmText.value = null
    }

    fun submit() {
        if (_sending.value) return
        if (_title.value.isBlank() || _description.value.isBlank()) {
            _message.value = "Completá el título y la descripción."
            return
        }
        if (_zone.value.isBlank()) {
            _message.value = "Indicá la zona del reporte."
            return
        }
        if (_photos.value.isEmpty()) {
            _message.value = "Agregá al menos una foto como evidencia."
            return
        }
        screenModelScope.launch {
            _sending.value = true
            _message.value = null
            val result = runCatching {
                val uid = authRepository.session.first()?.uid
                    ?: throw IllegalStateException("Sesión no disponible")
                // Upload first with the client-generated id so the moderator
                // sees the photo URLs on document creation.
                val reportId = repository.newPendingReportId()
                val photoUrls = _photos.value.map { photo ->
                    repository.uploadReportPhoto(reportId, photo).getOrThrow()
                }
                val firstMetadata = _photos.value.first().metadata
                repository.savePendingReport(
                    IncidentReport(
                        id = reportId,
                        title = _title.value.trim(),
                        description = _description.value.trim(),
                        category = _category.value.code,
                        latitude = firstMetadata.latitude ?: 0.0,
                        longitude = firstMetadata.longitude ?: 0.0,
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
                onFailure = { "No se pudo enviar el reporte. Inténtalo de nuevo." },
            )
            if (result.isSuccess) {
                _title.value = ""
                _description.value = ""
                _zone.value = ""
                _photos.value = emptyList()
            }
        }
    }
}
