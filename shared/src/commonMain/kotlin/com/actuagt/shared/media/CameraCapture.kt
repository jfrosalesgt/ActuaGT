package com.actuagt.shared.media

// Opens the device camera and captures a photo with its metadata.
// Returns null when the user cancels. The capture UI lives in the platform
// shells: Android wires the TakePicture contract, iOS the system image picker.
expect class CameraCapture() {
    suspend fun takePhoto(): CapturedPhoto?

    // False when the device cannot take photos (e.g. no camera app), so the
    // UI can explain instead of failing silently.
    fun isCaptureAvailable(): Boolean

    // Requests the camera permission when the platform needs it (Android
    // throws SecurityException on some OEMs without it). False when the
    // user denies it, so the UI can explain instead of failing silently.
    suspend fun ensureAccess(): Boolean
}
