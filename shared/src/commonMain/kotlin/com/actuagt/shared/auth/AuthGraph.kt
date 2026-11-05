package com.actuagt.shared.auth

// Manual dependency graph for auth. Wiring stays explicit: the platform
// shells (androidApp/iOS) inject their own GoogleIdTokenProvider at startup.
object AuthGraph {

    // Placeholder until the platform shell injects its Sign-In provider:
    // Google login reports "not configured" instead of crashing.
    private var googleIdTokenProvider: GoogleIdTokenProvider = GoogleIdTokenProvider { null }

    // Called once by the platform entry point before any session is read.
    // The auth repository is lazy, so injection always wins when it runs
    // before the first composition.
    fun initGoogleSignIn(provider: GoogleIdTokenProvider) {
        googleIdTokenProvider = provider
    }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(googleIdTokenProvider = googleIdTokenProvider)
    }

    fun sessionScreenModel(): SessionScreenModel =
        SessionScreenModel(authRepository)
}
