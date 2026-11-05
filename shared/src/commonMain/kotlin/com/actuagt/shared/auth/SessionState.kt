package com.actuagt.shared.auth

// UI-oriented session states observed by the screens.
sealed interface SessionState {
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: AppUser) : SessionState
}
