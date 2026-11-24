@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalTextApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)

package com.svce.issuegram.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.ui.unit.Dp
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
import com.svce.issuegram.ui.components.EmptyState
import com.svce.issuegram.ui.components.Eyebrow
import com.svce.issuegram.ui.components.badgeTone
import com.svce.issuegram.ui.feed.FeedViewModel
import com.svce.issuegram.ui.motion.ExpressiveHaptic
import com.svce.issuegram.ui.motion.rememberHaptics
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EventTimeFormatter = DateTimeFormatter
    .ofPattern("MMM d, h:mm a", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private fun formatEventTime(instant: Instant): String = EventTimeFormatter.format(instant)

private val GoogleSansLightFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(300)
        )
    )
)

@Composable
fun FeedScreenV6(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel? = null,
    onIssueClick: (String) -> Unit = {},
    onAlertsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onOpenMapLocation: ((Double, Double) -> Unit)? = null,
    bottomBarPadding: Dp = 0.dp,
    isDetailExpanded: Boolean = false,
    onDetailExpandedChange: (Boolean) -> Unit = {}
) {
    val uiState by viewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) }
    val hasUnread = uiState?.hasUnreadNotifications ?: false
    val haptic = rememberHaptics()
    var isLiked by rememberSaveable { mutableStateOf(false) }

    var firebaseUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            firebaseUser = auth.currentUser
        }
        val auth = Firebase.auth
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }
    val userPhotoUrl = uiState?.currentUser?.avatarUrl ?: firebaseUser?.photoUrl?.toString()
    val userInitials = remember(uiState?.currentUser, firebaseUser) {
        uiState?.currentUser?.displayName?.split(" ")?.mapNotNull {
            it.firstOrNull()?.uppercaseChar()
        }?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.displayName?.split(" ")?.mapNotNull {
                it.firstOrNull()?.uppercaseChar()
            }?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.email?.firstOrNull()?.uppercaseChar()?.toString()
            ?: "CR"
    }

    // Intercept hardware / gesture back button to close detailed view smoothly
    BackHandler(enabled = isDetailExpanded) {
        haptic(ExpressiveHaptic.Tap)
        onDetailExpandedChange(false)
    }

    val feedListState = rememberLazyListState()
    val context = LocalContext.current
    val issues = uiState?.issues.orEmpty()
    val isRefreshing = uiState?.isRefreshing == true
    val pullToRefreshState = rememberPullToRefreshState()
    var selectedTicketId by rememberSaveable { mutableStateOf<String?>(null) }
    val detailIssue = remember(issues, selectedTicketId) {
        issues.find { it.ticketId == selectedTicketId } ?: issues.firstOrNull()
    }

    // Fast & responsive MotionScheme specs:
    // - defaultSpatialSpec for fast, snappy hero shared element transition (~380ms)
    // - fastEffectsSpec for instant, crisp cross-fading without any lag or delay (~150ms)
    val motionScheme = MaterialTheme.motionScheme
    val spatialSpec = motionScheme.defaultSpatialSpec<androidx.compose.ui.geometry.Rect>()
    val fastEffectsSpec = motionScheme.fastEffectsSpec<Float>()

    val sharedBoundsTransform = remember(spatialSpec) {
        BoundsTransform { _, _ -> spatialSpec }
    }

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = isDetailExpanded,
            transitionSpec = {
                fadeIn(animationSpec = fastEffectsSpec) togetherWith
                    fadeOut(animationSpec = fastEffectsSpec)
            },
            label = "feed_detail_shared_transition"
        ) { expanded ->
            if (!expanded) {
                // =============================================================
                // HOME FEED: Card with photo + title & Contained Refresh Indicator
                // =============================================================
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        haptic(ExpressiveHaptic.Tap)
                        viewModel?.refresh()
                    },
                    modifier = Modifier.fillMaxSize(),
                    state = pullToRefreshState,
                    indicator = {}
                ) {
                    LazyColumn(
                        state = feedListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(bottom = bottomBarPadding + Spacing.space6)
                    ) {
                        // --- TOP APP BAR ---
                        item(key = "feed_top_bar", contentType = "header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = Spacing.space4, vertical = Spacing.space2)
                            ) {
                                // Top Left: Alerts
                                Surface(
                                    onClick = onAlertsClick,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.CenterStart)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.NotificationsNone,
                                            contentDescription = stringResource(R.string.tab_notifications),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        if (hasUnread) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = (-8).dp, y = 8.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error)
                                            )
                                        }
                                    }
                                }

                                // Top Middle: Issuegram Wordmark
                                Image(
                                    painter = painterResource(R.drawable.issuegram),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .align(Alignment.Center),
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                )

                                // Top Right: Profile
                                Surface(
                                    onClick = onProfileClick,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.CenterEnd)
                                ) {
                                    if (!userPhotoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = userPhotoUrl,
                                            contentDescription = "Profile",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = userInitials,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Contained Refresh Status Banner / Pill (visible while refreshing)
                        item(key = "feed_refresh_indicator", contentType = "status") {
                            AnimatedVisibility(
                                visible = isRefreshing,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = Spacing.space2),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        shadowElevation = 3.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space2),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.space2)
                                        ) {
                                            ContainedLoadingIndicator(
                                                modifier = Modifier.size(20.dp),
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Refreshing feed...",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "feed_top_spacer") {
                            Spacer(Modifier.height(Spacing.space3))
                        }

                        if (uiState?.isLoading == true && issues.isEmpty()) {
                            item(key = "feed_loading", contentType = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        } else if (issues.isEmpty()) {
                            item(key = "feed_empty", contentType = "empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyState(
                                        icon = Icons.Outlined.BookmarkBorder,
                                        title = stringResource(R.string.my_issues_empty_title),
                                        body = "Civic issues reported in your community will appear here.",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            items(
                                items = issues,
                                key = { it.ticketId },
                                contentType = { "issue_card" }
                            ) { issue ->
                                val issuePhoto = issue.photoUri
                                val hasPhoto = !issuePhoto.isNullOrBlank()

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    onClick = {
                                        onIssueClick(issue.ticketId)
                                    }
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Photo: Shared Element connected to detailed photo
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(340.dp)
                                                .sharedElement(
                                                    sharedContentState = rememberSharedContentState(key = "issue_photo_${issue.ticketId}"),
                                                    animatedVisibilityScope = this@AnimatedContent,
                                                    boundsTransform = sharedBoundsTransform
                                                )
                                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        ) {
                                            if (hasPhoto) {
                                                val imageRequest = remember(issuePhoto) {
                                                    ImageRequest.Builder(context)
                                                        .data(issuePhoto)
                                                        .crossfade(true)
                                                        .build()
                                                }
                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = issue.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    placeholder = painterResource(R.drawable.image_placeholder),
                                                    error = painterResource(R.drawable.image_placeholder)
                                                )
                                            } else {
                                                Image(
                                                    painter = painterResource(R.drawable.image_placeholder),
                                                    contentDescription = "Card Photo",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }

                                        // Content below photo
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(Spacing.space4)
                                        ) {
                                            Text(
                                                text = issue.title,
                                                fontFamily = GoogleSansLightFamily,
                                                fontWeight = FontWeight.Light,
                                                fontSize = 21.sp,
                                                lineHeight = 27.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.sharedElement(
                                                    sharedContentState = rememberSharedContentState(key = "issue_title_${issue.ticketId}"),
                                                    animatedVisibilityScope = this@AnimatedContent,
                                                    boundsTransform = sharedBoundsTransform
                                                )
                                            )

                                            val captionText = issue.description?.takeIf { it.isNotBlank() } ?: issue.locationName
                                            if (captionText.isNotBlank()) {
                                                Spacer(Modifier.height(Spacing.space2))
                                                Text(
                                                    text = captionText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(Modifier.height(Spacing.space4))

                                            // Bottom Action Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        haptic(ExpressiveHaptic.Tap)
                                                        onIssueClick(issue.ticketId)
                                                    },
                                                    shape = CircleShape,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFBDEEEA),
                                                        contentColor = Color(0xFF00201F)
                                                    ),
                                                    contentPadding = PaddingValues(
                                                        horizontal = Spacing.space4,
                                                        vertical = Spacing.space2
                                                    )
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_book_text),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(Spacing.space2))
                                                    Text(
                                                        text = "View report",
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                }

                                                val isPostLiked = issue.isLikedByMe

                                                Surface(
                                                    onClick = {
                                                        haptic(ExpressiveHaptic.Tap)
                                                        viewModel?.toggleLike(issue.ticketId)
                                                    },
                                                    shape = MaterialShapes.Cookie9Sided.toShape(),
                                                    color = if (isPostLiked) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    },
                                                    contentColor = if (isPostLiked) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (isPostLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                            contentDescription = "Like",
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "feed_bottom_spacer") {
                            Spacer(Modifier.height(Spacing.space8))
                        }
                    }
                }
            } else {
                // =============================================================
                // DETAILED SCREEN: Shared photo (edge-to-edge behind status bar) + title landed on photo
                // =============================================================
                val detailScrollState = rememberScrollState()
                val currentIssue = detailIssue ?: issues.firstOrNull()

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
                        val currentTicketKey = currentIssue?.ticketId ?: ""
                        // PHOTO CONTAINER: Shared Element (flies edge-to-edge behind status bar, 440dp height)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "issue_photo_$currentTicketKey"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = sharedBoundsTransform
                                )
                        ) {
                            if (!currentIssue?.photoUri.isNullOrBlank()) {
                                val detailImageRequest = remember(currentIssue?.photoUri) {
                                    ImageRequest.Builder(context)
                                        .data(currentIssue?.photoUri)
                                        .crossfade(true)
                                        .build()
                                }
                                AsyncImage(
                                    model = detailImageRequest,
                                    contentDescription = currentIssue?.title.orEmpty(),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(R.drawable.image_placeholder),
                                    error = painterResource(R.drawable.image_placeholder)
                                )
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.image_placeholder),
                                    contentDescription = "Detailed Photo",
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

                            // --- TITLE LANDS DIRECTLY ON THE PHOTO (NO SUMMARY HERE) ---
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = Spacing.space4, vertical = Spacing.space4)
                            ) {
                                Text(
                                    text = currentIssue?.title.orEmpty(),
                                    fontFamily = GoogleSansLightFamily,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 23.sp,
                                    lineHeight = 29.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "issue_title_$currentTicketKey"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = sharedBoundsTransform
                                    )
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
                            val detailDescription = currentIssue?.description?.takeIf { it.isNotBlank() } ?: currentIssue?.title.orEmpty()
                            if (detailDescription.isNotBlank()) {
                                Text(
                                    text = detailDescription,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 26.sp
                                )
                            }

                            // Entire status section enclosed in an outer outlined container
                            if (currentIssue != null) {
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

                                        Timeline(events = currentIssue.timeline())
                                    }
                                }

                                // Location Map Preview (strict 16:9 aspect ratio live GoogleMap preview)
                                val issueLocation = remember(currentIssue.latitude, currentIssue.longitude) {
                                    LatLng(currentIssue.latitude, currentIssue.longitude)
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
                                                title = currentIssue.title,
                                                snippet = currentIssue.locationName
                                            )
                                        }
                                        if (onOpenMapLocation != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable {
                                                        onOpenMapLocation.invoke(currentIssue.latitude, currentIssue.longitude)
                                                    }
                                            )
                                        }
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
                                        val formatted =
                                            remember(
                                                currentIssue?.locationName,
                                                currentIssue?.area,
                                                currentIssue?.latitude,
                                                currentIssue?.longitude
                                            ) {
                                                formatLocationAddress(
                                                    locationName = currentIssue?.locationName,
                                                    area = currentIssue?.area,
                                                    latitude = currentIssue?.latitude,
                                                    longitude = currentIssue?.longitude
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
                                            authorName = currentIssue?.authorName,
                                            authorUsername = currentIssue?.authorUsername,
                                            authorId = currentIssue?.authorId,
                                            currentFirebaseUser = firebaseUser ?: Firebase.auth.currentUser
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
                                            text = currentIssue?.createdAt?.let {
                                                java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
                                                    .withZone(java.time.ZoneId.systemDefault())
                                                    .format(it)
                                            } ?: "",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(Spacing.space8))
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
                            onClick = {
                                haptic(ExpressiveHaptic.Tap)
                                onDetailExpandedChange(false)
                            },
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
                            val isDetailLiked = currentIssue?.isLikedByMe == true

                            Surface(
                                onClick = {
                                    haptic(ExpressiveHaptic.Tap)
                                    currentIssue?.let { viewModel?.toggleLike(it.ticketId) }
                                },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isDetailLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                        contentDescription = stringResource(
                                            if (isDetailLiked) R.string.detail_btn_unlike else R.string.detail_btn_like
                                        ),
                                        tint = if (isDetailLiked) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    haptic(ExpressiveHaptic.Tap)
                                    currentIssue?.let {
                                        com.svce.issuegram.ui.components.shareIssue(context, it.ticketId, it.title)
                                    }
                                },
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
        }
    }
}

@Composable
private fun Timeline(events: List<TimelineEvent>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        events.forEachIndexed { index, event ->
            val tone = event.status.badgeTone()
            val isActive = index == events.lastIndex
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
                            .background(tone.container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tone.icon,
                            contentDescription = null,
                            tint = tone.content,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (index != events.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(16.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                        color = if (isActive) Color(0xFFBDEEEA) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isActive) Color(0xFF00201F) else MaterialTheme.colorScheme.onSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isActive) {
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
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }

                    // Timestamp (no year)
                    Text(
                        text = formatEventTime(event.at),
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
