package com.actuagt.shared.media

actual class CameraCapture actual constructor() {
    actual suspend fun takePhoto(): CapturedPhoto? {
        val bytes = AndroidCamera.capture() ?: return null
        return CapturedPhoto(bytes, ExifMetadataExtractor.extract(bytes))
    }
}
