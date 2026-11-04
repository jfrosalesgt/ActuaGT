package com.actuagt.shared.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

// iOS shows the metadata row until the system image picker is wired.
actual fun decodePreviewImage(bytes: ByteArray): ImageBitmap? = null

// iOS verification payload: longest side capped at 1024 px, JPEG 0.8,
// mirroring the Android encoder so both platforms stay under the callable
// size limit with the same damage detail.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun encodeVerificationImage(bytes: ByteArray): ByteArray? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.convert())
    }
    val image = UIImage.imageWithData(data) ?: return null
    val width = image.size.useContents { this.width }
    val height = image.size.useContents { this.height }
    val longest = maxOf(width, height)
    if (longest <= 0.0) return null
    val scale = if (longest > MAX_VERIFICATION_DIMENSION_PX) {
        MAX_VERIFICATION_DIMENSION_PX / longest
    } else {
        1.0
    }
    val targetWidth = width * scale
    val targetHeight = height * scale
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val jpeg = scaled?.let { UIImageJPEGRepresentation(it, JPEG_COMPRESSION_QUALITY) }
        ?: return null
    return jpeg.bytes?.readBytes(jpeg.length.toInt())
}

private const val MAX_VERIFICATION_DIMENSION_PX = 1024.0
private const val JPEG_COMPRESSION_QUALITY = 0.8
