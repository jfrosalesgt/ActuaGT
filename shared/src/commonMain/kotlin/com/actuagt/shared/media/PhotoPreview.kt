package com.actuagt.shared.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64

// Decodes report-photo bytes into a small UI preview. Null when the bytes
// cannot be decoded (or the platform cannot decode yet): callers fall back
// to the metadata row.
expect fun decodePreviewImage(bytes: ByteArray): ImageBitmap?

// Downscales camera bytes into a small JPEG for the pre-submit AI
// coherence check. Null when the bytes cannot be decoded: verification
// needs the image content, so it cannot proceed without it.
expect fun encodeVerificationImage(bytes: ByteArray): ByteArray?

// Data URL the assistant accepts as an image_url without any Storage
// upload: verification sees the photos while Storage stays clean until
// the report is actually submitted.
fun verificationDataUrl(jpegBytes: ByteArray): String =
    "data:image/jpeg;base64," + Base64.Default.encode(jpegBytes)
