package com.actuagt.shared.data

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.toStorageData(): Data = Data(toNSData())

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(
        bytes = allocArrayOf(this@toNSData),
        length = size.toULong(),
    )
}
