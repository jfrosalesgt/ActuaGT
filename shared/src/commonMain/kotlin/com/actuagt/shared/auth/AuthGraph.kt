package com.actuagt.shared.auth

// Manual dependency graph for auth. Wiring stays explicit until the platform
// shells (androidApp/iOS) provide their own GoogleIdTokenProvider.
object AuthGraph {

    // Placeholder: reports "not configured" until platform Sign-In lands.
    private val googleIdTokenProvider = GoogleIdTokenProvider { null }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(googleIdTokenProvider = googleIdTokenProvider)
    }

    fun sessionScreenModel(): SessionScreenModel =
        SessionScreenModel(authRepository)
}
