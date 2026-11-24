@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalTextApi::class
)

package com.svce.issuegram.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.svce.issuegram.R
import com.svce.issuegram.data.TimelineEvent
import com.svce.issuegram.data.timeline
import com.svce.issuegram.domain.formatLocationAddress
import com.svce.issuegram.domain.resolveReporterDisplayName
import com.svce.issuegram.ui.components.Eyebrow
import com.svce.issuegram.ui.components.badgeTone
import com.svce.issuegram.ui.components.shareIssue
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val EventDateFormatter = DateTimeFormatter
    .ofPattern("MMM d, yyyy", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private fun formatEventDate(instant: Instant): String = EventDateFormatter.format(instant)

private val EventTimeFormatter = DateTimeFormatter
    .ofPattern("MMM d, h:mm a", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private fun formatEventTime(instant: Instant): String = EventTimeFormatter.format(instant)

private val GoogleSansLightFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(300),
            FontVariation.width(100f),
            FontVariation.slant(0f)
        )
    )
)

@Composable
fun IssueDetailScreen(
    ticketId: String,
    onBack: () -> Unit,
    viewModel: IssueDetailViewModel? = null,
    onOpenFullMap: ((LatLng) -> Unit)? = null
) {
    BackHandler { onBack() }

    LaunchedEffect(ticketId) {
        viewModel?.loadIssue(ticketId)
    }

    val uiState by viewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    val currentUser by viewModel?.currentUser?.collectAsState() ?: remember { mutableStateOf(null) }
    val isAdmin = currentUser?.isAdmin == true
    val context = LocalContext.current
    val issue = uiState?.issue

    if (issue == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val detailScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Scrollable content: Photo + Title + Detailed Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(detailScrollState)
        ) {
            // PHOTO CONTAINER: Edge-to-edge behind status bar, 440dp height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                if (!issue.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(issue.photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = issue.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.image_placeholder),
                        error = painterResource(R.drawable.image_placeholder)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.image_placeholder),
                        contentDescription = issue.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Bottom gradient scrim for title landing on photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Title lands directly on the photo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = Spacing.space4, vertical = Spacing.space4)
                ) {
                    Text(
                        text = issue.title,
                        fontFamily = GoogleSansLightFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 23.sp,
                        lineHeight = 29.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                }
            }

            // Detailed page content below the photo
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space4, vertical = Spacing.space4)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.space4)
            ) {
                // 1. Detailed Summary / Story Description on Top
                Text(
                    text = issue.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_no_additional_details),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp
                )

                // Entire status section enclosed in an outer outlined container
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.space4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space3)
                    ) {
                        Eyebrow(stringResource(R.string.detail_status_eyebrow))
                        Timeline(events = issue.timeline(), currentStatus = issue.status)
                    }
                }

                // Admin Status Action Tab Bar (Exact Kimon Segmented Capsule UI) placed below timeline box
                if (isAdmin) {
                    AdminStatusTabBar(
                        currentStatus = issue.status,
                        onStatusSelected = { newStatus ->
                            viewModel?.updateStatus(issue.ticketId, newStatus)
                        }
                    )
                }

                // Location Map Preview (strict 16:9 aspect ratio preview with live GoogleMap)
                val issueLocation = remember(issue.latitude, issue.longitude) {
                    LatLng(issue.latitude, issue.longitude)
                }
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(issueLocation, 15f)
                }
                LaunchedEffect(issueLocation) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(issueLocation, 15f)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                scrollGesturesEnabled = false,
                                zoomGesturesEnabled = false,
                                rotationGesturesEnabled = false,
                                tiltGesturesEnabled = false,
                                mapToolbarEnabled = false,
                                myLocationButtonEnabled = false
                            )
                        ) {
                            Marker(
                                state = rememberUpdatedMarkerState(position = issueLocation),
                                title = issue.title,
                                snippet = issue.locationName
                            )
                        }
                        if (onOpenFullMap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        onOpenFullMap.invoke(issueLocation)
                                    }
                            )
                        }
                    }
                }

                // Address Card (two lines)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.space4),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Location",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(Spacing.space3))

                        Column(modifier = Modifier.weight(1f)) {
                            val formatted = remember(issue.locationName, issue.area, issue.latitude, issue.longitude) {
                                formatLocationAddress(
                                    locationName = issue.locationName,
                                    area = issue.area,
                                    latitude = issue.latitude,
                                    longitude = issue.longitude
                                )
                            }
                            Text(
                                text = formatted.primary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!formatted.secondary.isNullOrBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = formatted.secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Bottom row: Reporter pill on left, Date pill on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bottom Left: Reporter with Person icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.space2)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_user),
                                contentDescription = "Reported by",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            val reporterName = resolveReporterDisplayName(
                                authorName = issue.authorName,
                                authorUsername = issue.authorUsername,
                                authorId = issue.authorId,
                                currentFirebaseUser = com.google.firebase.Firebase.auth.currentUser
                            )
                            Text(
                                text = reporterName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Bottom Right: Date pill with Date icon
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.space2)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar_check),
                                contentDescription = "Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatEventDate(issue.createdAt),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))
            }
        }

        // PINNED TOP BAR: Back button and Actions (Like & Share) stay fixed at the top while scrolling
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.space4, vertical = Spacing.space2)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isLiked = uiState?.isLiked ?: false
                Surface(
                    onClick = { viewModel?.toggleLike(issue.ticketId) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = stringResource(if (isLiked) R.string.detail_btn_unlike else R.string.detail_btn_like),
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    onClick = { shareIssue(context, issue.ticketId, issue.title) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(events: List<TimelineEvent>, currentStatus: com.svce.issuegram.data.model.IssueStatus) {
    val activeIndex = events.indexOfFirst { it.status == currentStatus }.takeIf { it >= 0 } ?: (events.size - 1)
    Column(modifier = Modifier.fillMaxWidth()) {
        events.forEachIndexed { index, event ->
            val tone = event.status.badgeTone()
            val isCurrent = index == activeIndex
            val isPast = index < activeIndex
            val isFuture = index > activeIndex
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Left status icon & connecting timeline vertical line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFuture) MaterialTheme.colorScheme.surfaceContainerHighest else tone.container
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tone.icon,
                            contentDescription = null,
                            tint = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else tone.content,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (index != events.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(16.dp)
                                .background(
                                    if (index < activeIndex) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    }
                                )
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.space2))

                // Milestone chip on the left + timestamp on the right
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Pill Chip (compact)
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isCurrent -> Color(0xFFBDEEEA)
                            isPast -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        contentColor = when {
                            isCurrent -> Color(0xFF00201F)
                            isPast -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00796B))
                                )
                            }
                            Text(
                                text = stringResource(event.status.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }

                    // Timestamp
                    Text(
                        text = formatEventTime(event.at),
                        style = ExpressiveTextStyles.dataSmall,
                        color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private val ADMIN_STATUS_OPTIONS = listOf(
    com.svce.issuegram.data.model.IssueStatus.Open,
    com.svce.issuegram.data.model.IssueStatus.Acknowledged,
    com.svce.issuegram.data.model.IssueStatus.Assigned,
    com.svce.issuegram.data.model.IssueStatus.InProgress,
    com.svce.issuegram.data.model.IssueStatus.Resolved
)

@Composable
private fun AdminStatusTabBar(
    currentStatus: com.svce.issuegram.data.model.IssueStatus,
    onStatusSelected: (com.svce.issuegram.data.model.IssueStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = remember(currentStatus) {
        val idx = ADMIN_STATUS_OPTIONS.indexOf(currentStatus)
        if (idx >= 0) {
            idx
        } else {
            0
        }
    }

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 380f
        ),
        label = "admin_tab_spring"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val tabCount = ADMIN_STATUS_OPTIONS.size
            val tabWidth = maxWidth / tabCount

            // 1. Continuous Floating Indicator Pill with Spring Physics (Exact Kimon UI)
            Box(
                modifier = Modifier
                    .offset {
                        val xOffsetPx = (tabWidth.toPx() * animatedIndex).roundToInt()
                        IntOffset(x = xOffsetPx, y = 0)
                    }
                    .width(tabWidth)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            // 2. Interactive Label Layer with Interpolated Color States (Exact Kimon UI)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ADMIN_STATUS_OPTIONS.forEachIndexed { index, status ->
                    val distance = (animatedIndex - index).absoluteValue.coerceIn(0f, 1f)
                    val textColor = lerp(
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        distance
                    )
                    val fontWeight = if (distance < 0.5f) FontWeight.Bold else FontWeight.Medium

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onStatusSelected(status)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(status.labelRes),
                            fontWeight = fontWeight,
                            fontSize = 11.sp,
                            letterSpacing = (-0.3).sp,
                            color = textColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
