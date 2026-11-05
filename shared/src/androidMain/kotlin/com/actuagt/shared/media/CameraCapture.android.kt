package com.actuagt.shared.media

actual class CameraCapture actual constructor() {
    actual suspend fun takePhoto(): CapturedPhoto? {
        val bytes = AndroidCamera.capture() ?: return null
        return CapturedPhoto(bytes, ExifMetadataExtractor.extract(bytes))
    }

    actual fun isCaptureAvailable(): Boolean = AndroidCamera.isAvailable()

    actual suspend fun ensureAccess(): Boolean = AndroidCamera.ensurePermission()
}
