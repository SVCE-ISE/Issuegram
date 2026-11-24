package com.svce.issuegram.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun getLastKnownLocation(context: Context): Location? {
    val permitted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!permitted) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(
        "fused",
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    return providers
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}

suspend fun currentDeviceLocation(context: Context): Location = withTimeout(20_000) {
    val permitted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    check(permitted) { "Allow location access, or choose the location on the map." }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    check(LocationManagerCompat.isLocationEnabled(manager)) { "Turn on location, or choose the location on the map." }
    val providers = listOf(
        "fused",
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER
    ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    val provider = providers.firstOrNull()
        ?: error("Location is unavailable. Choose the location on the map.")
    suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) {
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        val lastKnown = getLastKnownLocation(context)
                        if (lastKnown != null) {
                            continuation.resume(lastKnown)
                        } else {
                            continuation.resumeWithException(IllegalStateException("Couldn't get a recent location. Choose it on the map."))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                val lastKnown = getLastKnownLocation(context)
                if (lastKnown != null) {
                    continuation.resume(lastKnown)
                } else {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
}

/**
 * Resolves coordinates to a human-readable address. Returns null when no
 * address is available (offline, no Geocoder backend, or timeout), so callers
 * can fall back to displaying coordinates.
 */
suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? =
    withTimeoutOrNull(5_000) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                results?.firstOrNull()?.let { address ->
                    address.getAddressLine(0)?.takeIf { it.isNotBlank() }
                        ?: listOfNotNull(address.thoroughfare, address.locality, address.adminArea)
                            .joinToString(", ")
                            .takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }
    }
