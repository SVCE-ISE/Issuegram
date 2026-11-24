package com.svce.issuegram.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.svce.issuegram.data.location.getLastKnownLocation

@Composable
fun LocationPickerDialog(initialLatitude: Double?, initialLongitude: Double?, onDismiss: () -> Unit, onSelected: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val lastKnown = remember(hasLocationPermission) {
        if (hasLocationPermission) getLastKnownLocation(context) else null
    }
    var selected by remember {
        mutableStateOf(
            if (initialLatitude != null && initialLongitude != null) {
                LatLng(initialLatitude, initialLongitude)
            } else {
                lastKnown?.let { LatLng(it.latitude, it.longitude) }
            }
        )
    }
    val camera = rememberCameraPositionState {
        val start = selected ?: lastKnown?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(20.5937, 78.9629)
        position = CameraPosition.fromLatLngZoom(start, if (selected == null && lastKnown == null) 4f else 15f)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where is the issue?") },
        text = {
            Column {
                Text("Tap the map to select the location.")
                GoogleMap(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    cameraPositionState = camera,
                    properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                    onMapClick = { selected = it },
                    uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = hasLocationPermission)
                ) {
                    selected?.let { Marker(state = rememberUpdatedMarkerState(position = it)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                selected?.let { onSelected(it.latitude, it.longitude) }
            }, enabled = selected != null) { Text("Use this location") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
