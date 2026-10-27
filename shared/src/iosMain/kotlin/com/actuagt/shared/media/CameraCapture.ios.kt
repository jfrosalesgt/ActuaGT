package com.actuagt.shared.media

actual class CameraCapture {
    actual suspend fun takePhoto(): CapturedPhoto? {
        TODO("Wire with the iOS app: present the system image picker, then ExifMetadataExtractor.extract(bytes).")
    }
}
