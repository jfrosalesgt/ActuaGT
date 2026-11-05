package com.actuagt.shared.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// Android location bridge: MainActivity registers once. fetch() asks for
// the location permission when needed, then tries a fresh fix with the last
// known location as fallback. Null means "position manually on the map".
object AndroidLocation {
    private const val FIX_TIMEOUT_MILLIS = 15_000L

    private var activity: ComponentActivity? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var permissionResult: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    fun register(activity: ComponentActivity) {
        if (permissionLauncher != null && this.activity === activity) return
        this.activity = activity
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            permissionResult?.complete(granted)
            permissionResult = null
        }
    }

    fun isAvailable(): Boolean {
        val host = activity ?: return false
        if (!hasPermission(host)) return false
        val manager = host.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return manager != null &&
            (manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    suspend fun fetch(): ReportCoordinates? = mutex.withLock {
        val host = activity ?: return null
        if (!hasPermission(host)) {
            val deferred = CompletableDeferred<Boolean>()
            permissionResult = deferred
            permissionLauncher?.launch(Manifest.permission.ACCESS_FINE_LOCATION) ?: return null
            if (!deferred.await()) return null
        }
        val client = LocationServices.getFusedLocationProviderClient(host)
        val fresh = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
            val deferred = CompletableDeferred<ReportCoordinates?>()
            val source = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, source.token)
                .addOnSuccessListener { location ->
                    deferred.complete(
                        location?.let { ReportCoordinates(it.latitude, it.longitude) },
                    )
                }
                .addOnFailureListener { deferred.complete(null) }
            deferred.await()
        }
        if (fresh != null) return fresh
        val last = CompletableDeferred<ReportCoordinates?>()
        client.lastLocation
            .addOnSuccessListener { location ->
                last.complete(location?.let { ReportCoordinates(it.latitude, it.longitude) })
            }
            .addOnFailureListener { last.complete(null) }
        last.await()
    }

    // Municipality for a report point from the map metadata. Runs off the
    // main thread; the Geocoder backend is missing on some devices, so
    // anything unexpected degrades to null and the typed zone survives.
    suspend fun municipalityName(latitude: Double, longitude: Double): String? {
        val host = activity ?: return null
        if (!Geocoder.isPresent()) return null
        return try {
            withContext(Dispatchers.IO) {
                // Deprecated in API 33 but still served everywhere; the
                // listener replacement would need an API-level branch.
                @Suppress("DEPRECATION")
                val addresses = Geocoder(host, Locale("es", "GT"))
                    .getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.locality
                    ?.takeIf { it.isNotBlank() }
                    ?: addresses?.firstOrNull()?.subAdminArea?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun hasPermission(host: ComponentActivity): Boolean =
        ContextCompat.checkSelfPermission(host, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(host, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
