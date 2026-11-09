package com.svce.issuegram.ui.map

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun CivicMapScreen(
    modifier: Modifier = Modifier,
    issues: List<CivicIssueDto> = emptyList(),
    onOpenIssue: (CivicIssueDto) -> Unit = {},
) {
    val defaultCenter = remember { LatLng(20.5937, 78.9629) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 4f)
    }

    val coroutineScope = rememberCoroutineScope()

    var isSatelliteView by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }

    val civicPins = remember(issues) {
        issues.map { issue ->
            CivicIssuePin(issue.id.toString(), issue.ticketCode, issue.status, issue.title, issue.locationName,
                LatLng(issue.latitude, issue.longitude), issue.isResolved, issue.category,
                issue.createdAt ?: "")
        }
    }
    var initiallyCentered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(issues.firstOrNull()?.id) {
        if (!initiallyCentered && issues.isNotEmpty()) {
            val first = issues.first()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 14f))
            initiallyCentered = true
        }
    }

    var selectedPinId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val inProgressCount = remember(civicPins) { civicPins.count { !it.isResolved } }
    val resolvedCount = remember(civicPins) { civicPins.count { it.isResolved } }

    val filteredIssues = remember(selectedFilter, civicPins) {
        when (selectedFilter) {
            "Active" -> civicPins.filter { !it.isResolved }
            "Resolved" -> civicPins.filter { it.isResolved }
            else -> civicPins
        }
    }

    val selectedPin = filteredIssues.firstOrNull { it.id == selectedPinId }

    val mapProperties = remember(isSatelliteView) {
        MapProperties(
            mapType = if (isSatelliteView) MapType.HYBRID else MapType.NORMAL,
            isBuildingEnabled = true,
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
            onMapClick = {
                selectedPinId = null
            }
        ) {
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
                        selectedPinId = issue.id
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
        if (filteredIssues.isEmpty()) {
            Surface(modifier = Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.medium) {
                Text("No loaded reports match this filter.", modifier = Modifier.padding(16.dp))
            }
        }

        // Top Filter Bar (Floating Chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Active", "Resolved").forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = when (filter) {
                                "All" -> "Loaded (${civicPins.size})"
                                "Active" -> "Active ($inProgressCount)"
                                else -> "Resolved ($resolvedCount)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                )
            }
        }

        // Floating Action Controls on Top Right (Satellite Toggle + Re-center)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Satellite / Vector Toggle
            Surface(
                onClick = { isSatelliteView = !isSatelliteView },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_map),
                        contentDescription = "Toggle Satellite",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSatelliteView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Recenter to Active Zone
            Surface(
                onClick = {
                    coroutineScope.launch {
                        val target = selectedPin?.position ?: defaultCenter
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(target, if (selectedPin == null) 4f else 17.5f),
                            600
                        )
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_map_pin),
                        contentDescription = "Recenter",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Selected Issue Floating Bottom Card
        AnimatedVisibility(
            visible = selectedPin != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 12.dp)
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

                            // Status Chip
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
}
