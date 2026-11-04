package com.actuagt.shared.media

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// Real camera bridge for Android: MainActivity registers once, and the shared
// CameraCapture awaits the TakePicture result. EXIF data is read from the
// captured file bytes by ExifMetadataExtractor.
object AndroidCamera {
    private const val FILE_PROVIDER_AUTHORITY = "com.actuagt.app.fileprovider"
    private const val TAG = "ActuaGT"
    private const val CAPTURE_TIMEOUT_MILLIS = 60_000L
    private const val PERMISSION_TIMEOUT_MILLIS = 60_000L

    private var activity: ComponentActivity? = null
    private var launcher: ActivityResultLauncher<Uri>? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var permissionResult: CompletableDeferred<Boolean>? = null
    private var pending: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    fun register(activity: ComponentActivity) {
        // Launchers bind to the registering activity: a recreated activity
        // needs fresh ones, otherwise captures die silently on the old host.
        if (launcher != null && this.activity === activity) return
        this.activity = activity
        launcher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            pending?.complete(success)
            pending = null
        }
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionResult?.complete(granted)
            permissionResult = null
        }
    }

    // Some OEMs (e.g. Xiaomi) deny the IMAGE_CAPTURE intent when the caller
    // lacks the runtime CAMERA permission, so request it before capturing.
    suspend fun ensurePermission(): Boolean {
        val host = activity ?: return false
        if (ContextCompat.checkSelfPermission(host, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        val deferred = CompletableDeferred<Boolean>()
        permissionResult = deferred
        permissionLauncher?.launch(Manifest.permission.CAMERA) ?: return false
        val granted = withTimeoutOrNull(PERMISSION_TIMEOUT_MILLIS) { deferred.await() }
        if (granted == null) {
            Log.d(TAG, "camera permission answer timed out")
            permissionResult = null
        }
        return granted == true
    }

    fun isAvailable(): Boolean {
        val host = activity ?: return false
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            host.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            host.packageManager.queryIntentActivities(intent, 0)
        }
        return handlers.isNotEmpty()
    }

    suspend fun capture(): ByteArray? = mutex.withLock {
        val host = activity
        if (host == null) {
            Log.d(TAG, "capture without a registered activity")
            return null
        }
        val activeLauncher = launcher
        if (activeLauncher == null) {
            Log.d(TAG, "capture without a TakePicture launcher")
            return null
        }
        if (!isAvailable()) {
            Log.d(TAG, "no camera app handles IMAGE_CAPTURE")
            return null
        }
        val file = File(host.cacheDir, "actuagt_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(host, FILE_PROVIDER_AUTHORITY, file)
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        // An emulator without a camera app throws here instead of answering.
        try {
            activeLauncher.launch(uri)
        } catch (e: Exception) {
            Log.d(TAG, "TakePicture launch failed", e)
            pending = null
            file.delete()
            return null
        }
        val success = withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) { deferred.await() }
        if (success == null) {
            Log.d(TAG, "TakePicture answer timed out")
        }
        pending = null
        return if (success == true && file.exists()) {
            file.readBytes().also { file.delete() }
        } else {
            file.delete()
            null
        }
    }
}
