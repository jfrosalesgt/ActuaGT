package com.actuagt.shared.auth

import kotlinx.coroutines.flow.Flow

// Supplies the Google ID token collected from platform sign-in UI
// (Credential Manager on Android, GIDSignIn on iOS). Platform
// implementations land together with the app shells in a later phase.
fun interface GoogleIdTokenProvider {
    suspend fun fetchIdToken(): String?
}

// Session boundary used by the UI. Implementations stay platform-agnostic;
// only the GoogleIdTokenProvider is platform-specific.
interface AuthRepository {
    val session: Flow<AppUser?>

    suspend fun signInAnonymously(): Result<Unit>

    suspend fun signInWithGoogle(): Result<Unit>

    suspend fun signOut()
}
