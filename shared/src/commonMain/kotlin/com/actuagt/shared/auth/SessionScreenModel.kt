package com.actuagt.shared.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Voyager ScreenModel that exposes the session as observable UI state.
// Error copy is user-facing Spanish; technical causes stay in logs.
class SessionScreenModel(
    private val repository: AuthRepository,
) : ScreenModel {

    val sessionState: StateFlow<SessionState> = repository.session
        .map { user ->
            if (user == null) SessionState.SignedOut else SessionState.SignedIn(user)
        }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState.Loading,
        )

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun signInAnonymously() = launchSignIn { repository.signInAnonymously() }

    fun signInWithGoogle() = launchSignIn { repository.signInWithGoogle() }

    fun signOut() {
        screenModelScope.launch { repository.signOut() }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun launchSignIn(block: suspend () -> Result<Unit>) {
        if (_signingIn.value) return
        screenModelScope.launch {
            _signingIn.value = true
            _errorMessage.value = null
            block().onFailure {
                _errorMessage.value = "No se pudo iniciar sesión. Inténtalo de nuevo."
            }
            _signingIn.value = false
        }
    }
}
