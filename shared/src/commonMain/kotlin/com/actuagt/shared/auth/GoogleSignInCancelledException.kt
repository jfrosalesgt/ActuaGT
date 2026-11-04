package com.actuagt.shared.auth

// The user dismissed the Google account picker: SessionScreenModel treats
// this as a silent no-op instead of a sign-in error.
class GoogleSignInCancelledException : Exception()
