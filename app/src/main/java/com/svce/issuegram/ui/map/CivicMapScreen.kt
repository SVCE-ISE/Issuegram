package com.svce.issuegram.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.svce.issuegram.R
import com.svce.issuegram.data.location.currentDeviceLocation
import com.svce.issuegram.data.location.getLastKnownLocation
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.domain.displayTime
import kotlinx.coroutines.launch

data class CivicIssuePin(
    val id: String,
    val ticketCode: String,
    val status: String,
    val title: String,
    val locationName: String,
    val position: LatLng,
    val isResolved: Boolean,
    val category: String,
    val timestamp: String,
)

@Composable
fun CivicMapScreen(
    modifier: Modifier = Modifier,
    issues: List<CivicIssueDto> = emptyList(),
    onOpenIssue: (CivicIssueDto) -> Unit = {},
    isPickingLocation: Boolean = false,
    initialPickLocation: LatLng? = null,
    initialCenter: LatLng? = null,
    onLocationPicked: (Double, Double) -> Unit = { _, _ -> },
    onCancelPickLocation: () -> Unit = {},
    bottomBarPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var pickedLatLng by remember(initialPickLocation, isPickingLocation) {
        mutableStateOf(initialPickLocation)
    }

    val displayIssues = issues

    val defaultCenter = remember(displayIssues, initialCenter) {
        if (initialCenter != null) {
            initialCenter
        } else if (displayIssues.isNotEmpty()) {
            LatLng(displayIssues.first().latitude, displayIssues.first().longitude)
        } else {
            // Check if device location is available, otherwise default to Chennai/Bangalore metro
            val lastKnown = if (hasLocationPermission) getLastKnownLocation(context) else null
            if (lastKnown != null) {
                LatLng(lastKnown.latitude, lastKnown.longitude)
            } else {
                LatLng(13.0827, 80.2707)
            }
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        val initialLastKnown = if (hasLocationPermission) getLastKnownLocation(context) else null
        val startCenter = initialPickLocation ?: initialCenter ?: initialLastKnown?.let { LatLng(it.latitude, it.longitude) } ?: defaultCenter
        val startZoom = if (initialPickLocation != null || initialCenter != null || initialLastKnown != null) 15f else 12.5f
        position = CameraPosition.fromLatLngZoom(startCenter, startZoom)
    }

    fun updateUserLocation(targetZoom: Float = 15.5f, animate: Boolean = true, updatePickedIfEmpty: Boolean = false) {
        if (!hasLocationPermission) return
        coroutineScope.launch {
            val lastKnown = getLastKnownLocation(context)
            if (lastKnown != null) {
                val lastLatLng = LatLng(lastKnown.latitude, lastKnown.longitude)
                userLocation = lastLatLng
                if (updatePickedIfEmpty && isPickingLocation && pickedLatLng == null) {
                    pickedLatLng = lastLatLng
                }
                if (animate) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(lastLatLng, targetZoom), 500)
                }
            }
            val fresh = runCatching { currentDeviceLocation(context) }.getOrNull()
            if (fresh != null) {
                val freshLatLng = LatLng(fresh.latitude, fresh.longitude)
                userLocation = freshLatLng
                if (updatePickedIfEmpty && isPickingLocation && pickedLatLng == null) {
                    pickedLatLng = freshLatLng
                }
                if (animate && (lastKnown == null || freshLatLng.latitude != lastKnown.latitude || freshLatLng.longitude != lastKnown.longitude)) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(freshLatLng, targetZoom), 500)
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            updateUserLocation(targetZoom = 15.5f, animate = true, updatePickedIfEmpty = isPickingLocation)
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            if (initialCenter == null && initialPickLocation == null) {
                updateUserLocation(targetZoom = 15f, animate = true, updatePickedIfEmpty = isPickingLocation)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(initialCenter) {
        if (initialCenter != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(initialCenter, 16f)
            )
        }
    }

    var isSatelliteView by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val civicPins = remember(displayIssues) {
        displayIssues.map { issue ->
            CivicIssuePin(
                issue.id.toString(), issue.ticketCode, issue.status, issue.title, issue.locationName,
                LatLng(issue.latitude, issue.longitude), issue.isResolved, issue.category,
                issue.createdAt ?: ""
            )
        }
    }
    var initiallyCentered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(displayIssues.firstOrNull()?.id) {
        if (!initiallyCentered && displayIssues.isNotEmpty() && initialCenter == null && initialPickLocation == null && !hasLocationPermission && userLocation == null) {
            val first = displayIssues.first()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 13f))
            initiallyCentered = true
        }
    }

    val selectedPinId = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val inProgressCount = remember(civicPins) { civicPins.count { !it.isResolved } }
    val resolvedCount = remember(civicPins) { civicPins.count { it.isResolved } }

    val filteredIssues = remember(selectedFilter, civicPins) {
        when (selectedFilter) {
            "Active" -> civicPins.filter { !it.isResolved }
            "Resolved" -> civicPins.filter { it.isResolved }
            else -> civicPins
        }
    }

    val mapProperties = remember(isSatelliteView, hasLocationPermission) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission,
            mapType = if (isSatelliteView) MapType.HYBRID else MapType.NORMAL,
            isBuildingEnabled = false,
            isIndoorEnabled = false,
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Real Live Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = { latLng ->
                if (isPickingLocation) {
                    pickedLatLng = latLng
                } else {
                    selectedPinId.value = null
                }
            }
        ) {
            if (isPickingLocation && pickedLatLng != null) {
                Marker(
                    state = rememberUpdatedMarkerState(position = pickedLatLng!!),
                    title = "Selected Location",
                    snippet = String.format(java.util.Locale.US, "%.6f, %.6f", pickedLatLng!!.latitude, pickedLatLng!!.longitude),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
            if (!isPickingLocation) {
                filteredIssues.forEach { issue ->
                    androidx.compose.runtime.key(issue.id) {
                        Marker(
                            state = rememberUpdatedMarkerState(position = issue.position),
                            title = "${issue.ticketCode.ifBlank { "#" + issue.id }} - ${issue.title}",
                            snippet = issue.locationName,
                            icon = if (issue.isResolved) {
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            } else {
                                val isHighOrCritical = issue.status.equals("CRITICAL", ignoreCase = true) ||
                                        issue.category.contains("Emergency", ignoreCase = true) ||
                                        issue.category.contains("Power", ignoreCase = true)
                                if (isHighOrCritical) {
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                                } else {
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                                }
                            },
                            onClick = {
                                selectedPinId.value = issue.id
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLng(issue.position),
                                        500
                                    )
                                }
                                true
                            }
                        )
                    }
                }
            }
        }

        // Top Controls: Safe under status bar
        if (isPickingLocation) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_map_pin),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.map_tap_to_pick),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // Top Filter Bar & Map Layers Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Scrollable category chips
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // All Chip
                    FilterChip(
                        selected = selectedFilter == "All",
                        onClick = { selectedFilter = "All" },
                        label = {
                            Text(
                                text = "All (${civicPins.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedFilter == "All") FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = BorderStroke(
                            0.5.dp,
                            if (selectedFilter == "All") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    )

                    // Active Chip
                    FilterChip(
                        selected = selectedFilter == "Active",
                        onClick = { selectedFilter = if (selectedFilter == "Active") "All" else "Active" },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (selectedFilter == "Active") MaterialTheme.colorScheme.onPrimary else Color(0xFFE65100),
                                        CircleShape
                                    )
                            )
                        },
                        label = {
                            Text(
                                text = "Active ($inProgressCount)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedFilter == "Active") FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = BorderStroke(
                            0.5.dp,
                            if (selectedFilter == "Active") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    )

                    // Resolved Chip
                    FilterChip(
                        selected = selectedFilter == "Resolved",
                        onClick = { selectedFilter = if (selectedFilter == "Resolved") "All" else "Resolved" },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (selectedFilter == "Resolved") MaterialTheme.colorScheme.onPrimary else Color(0xFF2E7D32),
                                        CircleShape
                                    )
                            )
                        },
                        label = {
                            Text(
                                text = "Resolved ($resolvedCount)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedFilter == "Resolved") FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = BorderStroke(
                            0.5.dp,
                            if (selectedFilter == "Resolved") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Satellite / Map Layer Toggle Button
                Surface(
                    onClick = { isSatelliteView = !isSatelliteView },
                    shape = CircleShape,
                    color = if (isSatelliteView) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(
                        0.5.dp,
                        if (isSatelliteView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_map),
                            contentDescription = "Toggle Satellite",
                            modifier = Modifier.size(19.dp),
                            tint = if (isSatelliteView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // My Location floating button (Bottom-Right, above bottom navigation bar and card)
        val isCardVisible = !isPickingLocation && selectedPinId.value != null
        val recenterBottomPadding by animateDpAsState(
            targetValue = if (isCardVisible) bottomBarPadding + 195.dp else bottomBarPadding + 16.dp,
            label = "recenterBottomPadding"
        )

        Surface(
            onClick = {
                if (hasLocationPermission) {
                    updateUserLocation(targetZoom = 16f, animate = true, updatePickedIfEmpty = isPickingLocation)
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 5.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = recenterBottomPadding)
                .size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "My Location",
                    modifier = Modifier.size(22.dp),
                    tint = if (hasLocationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Selected Issue Floating Bottom Card
        MapSelectedIssueCard(
            pins = filteredIssues,
            selectedPinId = selectedPinId,
            issues = displayIssues,
            onOpenIssue = onOpenIssue,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = bottomBarPadding + 12.dp)
        )

        if (isPickingLocation) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = bottomBarPadding + 14.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (pickedLatLng != null)
                            stringResource(R.string.map_selected_coords, pickedLatLng!!.latitude, pickedLatLng!!.longitude)
                        else stringResource(R.string.map_tap_anywhere),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (pickedLatLng != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onCancelPickLocation,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                        androidx.compose.material3.Button(
                            onClick = {
                                pickedLatLng?.let { onLocationPicked(it.latitude, it.longitude) }
                            },
                            enabled = pickedLatLng != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.map_use_this_location))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSelectedIssueCard(
    pins: List<CivicIssuePin>,
    selectedPinId: androidx.compose.runtime.State<String?>,
    issues: List<CivicIssueDto>,
    onOpenIssue: (CivicIssueDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedPin = pins.firstOrNull { it.id == selectedPinId.value }
    AnimatedVisibility(
        visible = selectedPin != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        selectedPin?.let { pin ->
            Card(
                onClick = { issues.firstOrNull { it.id.toString() == pin.id }?.let(onOpenIssue) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = pin.ticketCode.ifBlank { "#${pin.id}" },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (pin.isResolved) Color(0xFF1B5E20).copy(alpha = 0.85f)
                            else Color(0xFFE65100).copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Text(
                                    text = pin.status.replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = pin.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_map_pin),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = pin.locationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Reported: ${displayTime(pin.timestamp)} • Category: ${pin.category}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
