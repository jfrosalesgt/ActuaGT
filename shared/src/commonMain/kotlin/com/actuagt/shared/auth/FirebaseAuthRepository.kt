package com.actuagt.shared.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Firebase-backed session repository, callable from commonMain.
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val googleIdTokenProvider: GoogleIdTokenProvider,
) : AuthRepository {

    override val session: Flow<AppUser?> =
        auth.authStateChanged.map { it?.toAppUser() }

    override suspend fun signInAnonymously(): Result<Unit> = runCatching {
        auth.signInAnonymously()
        Unit
    }

    override suspend fun signInWithGoogle(): Result<Unit> = runCatching {
        val idToken = googleIdTokenProvider.fetchIdToken()
            ?: throw IllegalStateException("Google Sign-In is not configured on this platform yet")
        val credential = GoogleAuthProvider.credential(idToken, null)
        auth.signInWithCredential(credential)
        Unit
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    private fun FirebaseUser.toAppUser() = AppUser(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoURL,
        isAnonymous = isAnonymous,
    )
}
