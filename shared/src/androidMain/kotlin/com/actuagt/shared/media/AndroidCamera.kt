package com.actuagt.shared.media

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Real camera bridge for Android: MainActivity registers once, and the shared
// CameraCapture awaits the TakePicture result. EXIF data is read from the
// captured file bytes by ExifMetadataExtractor.
object AndroidCamera {
    private const val FILE_PROVIDER_AUTHORITY = "com.actuagt.app.fileprovider"

    private var activity: ComponentActivity? = null
    private var launcher: ActivityResultLauncher<Uri>? = null
    private var pending: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    fun register(activity: ComponentActivity) {
        if (launcher != null) return
        this.activity = activity
        launcher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            pending?.complete(success)
            pending = null
        }
    }

    suspend fun capture(): ByteArray? = mutex.withLock {
        val host = activity ?: return null
        val activeLauncher = launcher ?: return null
        val file = File(host.cacheDir, "actuagt_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(host, FILE_PROVIDER_AUTHORITY, file)
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        activeLauncher.launch(uri)
        val success = deferred.await()
        return if (success && file.exists()) {
            file.readBytes().also { file.delete() }
        } else {
            file.delete()
            null
        }
    }
}
