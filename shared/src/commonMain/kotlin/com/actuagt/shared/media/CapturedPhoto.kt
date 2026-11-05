package com.actuagt.shared.media

// Photo taken with the device camera plus its extracted metadata.
data class CapturedPhoto(
    val bytes: ByteArray,
    val metadata: PhotoMetadata,
)
