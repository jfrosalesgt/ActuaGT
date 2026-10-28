package com.actuagt.shared.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.actuagt.shared.auth.AuthGraph
import com.actuagt.shared.auth.AuthRepository
import com.actuagt.shared.data.DataGraph
import com.actuagt.shared.data.ReportRepository
import com.actuagt.shared.media.CameraCapture
import com.actuagt.shared.media.CapturedPhoto
import com.actuagt.shared.model.OfficialRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

// Backs the official-access application form. The client only creates
// `pending` requests; approval happens outside the client.
class OfficialRequestScreenModel(
    private val authRepository: AuthRepository = AuthGraph.authRepository,
    private val reportRepository: ReportRepository = DataGraph.reportRepository,
) : ScreenModel {

    private val _fullName = MutableStateFlow("")
    val fullName: StateFlow<String> = _fullName

    private val _institution = MutableStateFlow("")
    val institution: StateFlow<String> = _institution

    private val _position = MutableStateFlow("")
    val position: StateFlow<String> = _position

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _zone = MutableStateFlow("")
    val zone: StateFlow<String> = _zone

    private val _credential = MutableStateFlow<CapturedPhoto?>(null)
    val credential: StateFlow<CapturedPhoto?> = _credential

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun onFullNameChange(value: String) { _fullName.value = value; _message.value = null }
    fun onInstitutionChange(value: String) { _institution.value = value; _message.value = null }
    fun onPositionChange(value: String) { _position.value = value; _message.value = null }
    fun onEmailChange(value: String) { _email.value = value; _message.value = null }
    fun onZoneChange(value: String) { _zone.value = value; _message.value = null }

    fun attachCredential() {
        screenModelScope.launch {
            CameraCapture().takePhoto()?.let { _credential.value = it }
        }
    }

    fun clearCredential() {
        _credential.value = null
    }

    fun submit() {
        if (_sending.value) return
        if (_fullName.value.isBlank() || _institution.value.isBlank() ||
            _position.value.isBlank() || _email.value.isBlank() || _zone.value.isBlank()
        ) {
            _message.value = "Completá todos los campos de la solicitud."
            return
        }
        screenModelScope.launch {
            _sending.value = true
            _message.value = null
            val result = runCatching {
                val uid = authRepository.session.first()?.uid
                    ?: throw IllegalStateException("Sesión no disponible")
                reportRepository.submitOfficialRequest(
                    OfficialRequest(
                        uid = uid,
                        fullName = _fullName.value.trim(),
                        institution = _institution.value.trim(),
                        position = _position.value.trim(),
                        institutionalEmail = _email.value.trim(),
                        zone = _zone.value.trim(),
                        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    ),
                    _credential.value,
                ).getOrThrow()
            }
            _sending.value = false
            _message.value = result.fold(
                onSuccess = { "Solicitud enviada. Te avisaremos cuando sea revisada." },
                onFailure = { "No se pudo enviar la solicitud. Inténtalo de nuevo." },
            )
            if (result.isSuccess) {
                _fullName.value = ""
                _institution.value = ""
                _position.value = ""
                _email.value = ""
                _zone.value = ""
                _credential.value = null
            }
        }
    }
}
