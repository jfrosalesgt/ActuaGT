package com.actuagt.shared.media

// Capture date and GPS coordinates recovered from a photo.
data class PhotoMetadata(
    val capturedAtEpochMillis: Long?,
    val latitude: Double?,
    val longitude: Double?,
)
