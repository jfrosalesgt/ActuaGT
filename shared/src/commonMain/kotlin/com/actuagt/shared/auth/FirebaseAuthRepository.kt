package com.actuagt.shared.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import com.actuagt.shared.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Firebase-backed session repository, callable from commonMain.
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val firestore: FirebaseFirestore = Firebase.firestore,
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

    override suspend fun getUserProfile(uid: String): User? = runCatching {
        val snapshot = firestore.collection(USERS_COLLECTION).document(uid).get()
        if (!snapshot.exists) null else snapshot.data<User>()
    }.getOrNull()

    override suspend fun ensureUserProfile(user: AppUser): Result<User> = runCatching {
        getUserProfile(user.uid)?.let { return@runCatching it }
        val profile = User(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl,
            isAnonymous = user.isAnonymous,
        )
        firestore.collection(USERS_COLLECTION).document(user.uid).set(profile)
        profile
    }

    companion object {
        const val USERS_COLLECTION = "users"
    }

    private fun FirebaseUser.toAppUser() = AppUser(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoURL,
        isAnonymous = isAnonymous,
    )
}
