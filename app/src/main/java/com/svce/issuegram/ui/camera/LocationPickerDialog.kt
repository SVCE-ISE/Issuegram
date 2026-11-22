package com.svce.issuegram.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun LocationPickerDialog(initialLatitude: Double?, initialLongitude: Double?, onDismiss: () -> Unit, onSelected: (Double, Double) -> Unit) {
    var selected by remember { mutableStateOf(if (initialLatitude != null && initialLongitude != null) LatLng(initialLatitude, initialLongitude) else null) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selected ?: LatLng(20.5937, 78.9629), if (selected == null) 4f else 16f)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Where is the issue?") }, text = {
        Column {
            Text("Tap the map to select the location.")
            GoogleMap(modifier = Modifier.fillMaxWidth().height(320.dp), cameraPositionState = camera,
                onMapClick = { selected = it }, uiSettings = MapUiSettings(zoomControlsEnabled = true)) {
                selected?.let { Marker(state = rememberUpdatedMarkerState(position = it)) }
            }
        }
    }, confirmButton = { TextButton(onClick = { selected?.let { onSelected(it.latitude, it.longitude) } }, enabled = selected != null) { Text("Use this location") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
