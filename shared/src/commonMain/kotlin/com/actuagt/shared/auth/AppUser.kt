package com.actuagt.shared.auth

// Platform-agnostic snapshot of the signed-in Firebase user.
data class AppUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean,
)
