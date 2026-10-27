package com.actuagt.shared.media

actual class CameraCapture {
    actual suspend fun takePhoto(): CapturedPhoto? {
        TODO("Wire with androidApp: launch the camera with the TakePicture contract, then ExifMetadataExtractor.extract(bytes).")
    }
}
