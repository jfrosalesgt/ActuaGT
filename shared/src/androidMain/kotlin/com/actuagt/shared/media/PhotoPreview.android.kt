package com.actuagt.shared.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max

// Android preview: downsampled so a 4-photo report never holds full
// bitmaps in memory. Null when the bytes are not a readable image.
actual fun decodePreviewImage(bytes: ByteArray): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inSampleSize = 8 }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: return null
    return bitmap.asImageBitmap()
}

// Android verification payload: longest side capped at 1024 px, JPEG 80.
// A 12 MP camera photo shrinks to roughly 100-200 KB: the coherence call
// stays far under the callable size limit without losing the damage detail.
actual fun encodeVerificationImage(bytes: ByteArray): ByteArray? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_VERIFICATION_DIMENSION_PX) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: return null
    val longest = max(decoded.width, decoded.height)
    val scaled = if (longest > MAX_VERIFICATION_DIMENSION_PX) {
        val scale = MAX_VERIFICATION_DIMENSION_PX.toFloat() / longest
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt(),
            (decoded.height * scale).toInt(),
            true,
        ).also { decoded.recycle() }
    } else {
        decoded
    }
    ByteArrayOutputStream().use { out ->
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        scaled.recycle()
        if (!ok) return null
        return out.toByteArray()
    }
}

private const val MAX_VERIFICATION_DIMENSION_PX = 1024
private const val JPEG_QUALITY = 80
