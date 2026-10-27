package com.actuagt.shared.model

import kotlinx.serialization.Serializable

// App-level roles. Stored as lowercase strings in Firestore.
@Serializable
enum class UserRole {
    CITIZEN,
    OFFICIAL,
}

// Platform-agnostic app user profile, mirrored from Firebase Auth.
@Serializable
data class User(
    val uid: String,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false,
    val role: UserRole = UserRole.CITIZEN,
    val zone: String = "",
)
