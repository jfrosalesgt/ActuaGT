package com.actuagt.shared.media

// Opens the device camera and captures a photo with its metadata.
// Returns null when the user cancels. The capture UI lives in the platform
// shells: Android wires the TakePicture contract, iOS the system image picker.
expect class CameraCapture() {
    suspend fun takePhoto(): CapturedPhoto?
}
