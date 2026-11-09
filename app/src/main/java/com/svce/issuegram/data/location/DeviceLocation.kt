package com.svce.issuegram.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun currentDeviceLocation(context: Context): Location = withTimeout(20_000) {
    val permitted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    check(permitted) { "Allow location access, or choose the location on the map." }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    check(LocationManagerCompat.isLocationEnabled(manager)) { "Turn on location, or choose the location on the map." }
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { manager.isProviderEnabled(it) }
        ?: error("Location is unavailable. Choose the location on the map.")
    suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) {
                    if (location != null && SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos <= 120_000_000_000L)
                        continuation.resume(location)
                    else continuation.resumeWithException(IllegalStateException("Couldn't get a recent location. Choose it on the map."))
                }
            }
        } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
    }
}
