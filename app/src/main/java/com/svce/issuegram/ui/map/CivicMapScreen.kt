package com.svce.issuegram.ui.map

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val DefaultMockCivicIssues = listOf(
    CivicIssueDto(
        id = 2481,
        ticketCode = "IG-2481",
        authorId = "priya",
        authorUsername = "Priya S.",
        title = "Overflowing drain near the bus stop",
        caption = "Water has been standing for three days and is spilling onto the footpath. Mosquitoes are breeding around it.",
        category = "Drainage",
        locationName = "2nd Avenue bus stop, Anna Nagar",
        latitude = 13.0850,
        longitude = 80.2101,
        status = "IN_PROGRESS",
        createdAt = "2 days ago",
    ),
    CivicIssueDto(
        id = 2479,
        ticketCode = "IG-2479",
        authorId = "karthik",
        authorUsername = "Karthik R.",
        title = "Streetlight not working on lane 4",
        caption = "The whole stretch is dark after 7 PM. Hard to walk after the shops close.",
        category = "Streetlight",
        locationName = "Lane 4, near the park gate, Anna Nagar",
        latitude = 13.0835,
        longitude = 80.2120,
        status = "ASSIGNED",
        createdAt = "3 days ago",
    ),
    CivicIssueDto(
        id = 2476,
        ticketCode = "IG-2476",
        authorId = "divya",
        authorUsername = "Divya M.",
        title = "Deep pothole swallowing two-wheelers",
        caption = "Deep crater in the middle of the road causing traffic slowdowns and accidents.",
        category = "Pothole",
        locationName = "Usman Road junction, T. Nagar",
        latitude = 13.0418,
        longitude = 80.2341,
        status = "REPORTED",
        createdAt = "4 days ago",
    ),
    CivicIssueDto(
        id = 2472,
        ticketCode = "IG-2472",
        authorId = "arjun",
        authorUsername = "Arjun P.",
        title = "Garbage not collected for a week",
        caption = "The bin at the corner is overflowing and dogs are scattering waste on the street.",
        category = "Garbage",
        locationName = "Ranganathan Street corner, T. Nagar",
        latitude = 13.0405,
        longitude = 80.2325,
        status = "RESOLVED",
        createdAt = "5 days ago",
    ),
    CivicIssueDto(
        id = 2468,
        ticketCode = "IG-2468",
        authorId = "sneha",
        authorUsername = "Sneha K.",
        title = "Broken water pipeline leaking clean water",
        caption = "Clean drinking water leaking continuously onto the road for over 24 hours.",
        category = "Water",
        locationName = "Race Course Road, Guindy",
        latitude = 13.0067,
        longitude = 80.2025,
        status = "RESOLVED",
        createdAt = "1 week ago",
    ),
    CivicIssueDto(
        id = 2465,
        ticketCode = "IG-2465",
        authorId = "vijay",
        authorUsername = "Vijay N.",
        title = "Damaged footpath pavers tripping pedestrians",
        caption = "Several concrete slabs are displaced or missing near the college gate.",
        category = "Road",
        locationName = "SVCE Campus Main Gate, Sriperumbudur",
        latitude = 12.9866,
        longitude = 79.9718,
        status = "IN_PROGRESS",
        createdAt = "1 week ago",
    )
)

@Composable
fun CivicMapScreen(
    modifier: Modifier = Modifier,
    issues: List<CivicIssueDto> = emptyList(),
    onOpenIssue: (CivicIssueDto) -> Unit = {},
    isPickingLocation: Boolean = false,
    initialPickLocation: LatLng? = null,
    onLocationPicked: (Double, Double) -> Unit = { _, _ -> },
    onCancelPickLocation: () -> Unit = {},
    bottomBarPadding: Dp = 0.dp,
) {
    val displayIssues = remember(issues) {
        if (issues.isNotEmpty()) issues else DefaultMockCivicIssues
    }

    val defaultCenter = remember(displayIssues) {
        if (displayIssues.isNotEmpty()) {
            LatLng(displayIssues.first().latitude, displayIssues.first().longitude)
        } else {
            LatLng(13.0827, 80.2707)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            initialPickLocation ?: defaultCenter,
            if (initialPickLocation != null) 14f else 12.5f
        )
    }

    val coroutineScope = rememberCoroutineScope()

    var pickedLatLng by remember(initialPickLocation, isPickingLocation) {
        mutableStateOf(initialPickLocation)
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
    androidx.compose.runtime.LaunchedEffect(displayIssues.firstOrNull()?.id) {
        if (!initiallyCentered && displayIssues.isNotEmpty()) {
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

    val mapProperties = remember(isSatelliteView) {
        MapProperties(
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
                    snippet = String.format(java.util.Locale.US, "%.5f, %.5f", pickedLatLng!!.latitude, pickedLatLng!!.longitude),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
            if (!isPickingLocation) {
                filteredIssues.forEach { issue ->
                    androidx.compose.runtime.key(issue.id) {
                        Marker(
                            state = rememberUpdatedMarkerState(position = issue.position),
                            title = "${issue.id} - ${issue.title}",
                            snippet = issue.locationName,
                            icon = if (issue.isResolved) {
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            } else {
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
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
                        text = "Tap on the map to pick issue location",
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

        // Recenter / Focus to Target floating button (Bottom-Right, above bottom navigation bar and card)
        val isCardVisible = !isPickingLocation && selectedPinId.value != null
        val recenterBottomPadding by animateDpAsState(
            targetValue = if (isCardVisible) bottomBarPadding + 195.dp else bottomBarPadding + 16.dp,
            label = "recenterBottomPadding"
        )

        Surface(
            onClick = {
                coroutineScope.launch {
                    val targetPin = filteredIssues.firstOrNull { it.id == selectedPinId.value }
                    val target = targetPin?.position ?: defaultCenter
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(target, if (targetPin == null) 13.5f else 16.5f),
                        500
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
                    contentDescription = "Recenter",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
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
                            String.format(java.util.Locale.US, "Selected: %.5f, %.5f", pickedLatLng!!.latitude, pickedLatLng!!.longitude)
                        else "Tap anywhere on the map to place a pin",
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
                            Text("Cancel")
                        }
                        androidx.compose.material3.Button(
                            onClick = {
                                pickedLatLng?.let { onLocationPicked(it.latitude, it.longitude) }
                            },
                            enabled = pickedLatLng != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use this location")
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
