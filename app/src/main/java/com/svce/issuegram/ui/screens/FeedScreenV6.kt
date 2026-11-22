@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalTextApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package com.svce.issuegram.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.svce.issuegram.R
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.MockAlerts
import com.svce.issuegram.data.MockIssues
import com.svce.issuegram.data.ProfileMock
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import coil.compose.AsyncImage
import com.svce.issuegram.data.TimelineEvent
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.data.timeline
import com.svce.issuegram.ui.components.CategoryAvatar
import com.svce.issuegram.ui.components.Eyebrow
import com.svce.issuegram.ui.components.StatusBadge
import com.svce.issuegram.ui.components.absoluteDateTime
import com.svce.issuegram.ui.components.badgeTone
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
            FontVariation.weight(300),
        ),
    ),
)

@Composable
fun FeedScreenV6(
    modifier: Modifier = Modifier,
    onIssueClick: (String) -> Unit = {},
    onAlertsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    bottomBarPadding: Dp = 0.dp,
    isDetailExpanded: Boolean = false,
    onDetailExpandedChange: (Boolean) -> Unit = {},
) {
    val hasUnread = remember { MockAlerts.all.any { it.unread } }
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
    val userPhotoUrl = firebaseUser?.photoUrl?.toString()
    val userInitials = remember(firebaseUser) {
        firebaseUser?.displayName?.split(" ")?.mapNotNull { it.firstOrNull()?.uppercaseChar() }?.joinToString("")?.takeIf { it.isNotBlank() }
            ?: ProfileMock.initials
    }

    // Intercept hardware / gesture back button to close detailed view smoothly
    BackHandler(enabled = isDetailExpanded) {
        haptic(ExpressiveHaptic.Tap)
        onDetailExpandedChange(false)
    }

    val feedScrollState = rememberScrollState()

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
            label = "feed_detail_shared_transition",
        ) { expanded ->
            if (!expanded) {
                // =============================================================
                // HOME FEED: Card with photo + title
                // =============================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(feedScrollState)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(bottom = bottomBarPadding + Spacing.space6),
                ) {
                    // --- TOP APP BAR ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = Spacing.space4, vertical = Spacing.space2),
                    ) {
                        // Top Left: Alerts
                        Surface(
                            onClick = onAlertsClick,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.CenterStart),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsNone,
                                    contentDescription = stringResource(R.string.tab_notifications),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
                                if (hasUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-8).dp, y = 8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error),
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
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        )

                        // Top Right: Profile
                        Surface(
                            onClick = onProfileClick,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.CenterEnd),
                        ) {
                            if (!userPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = userPhotoUrl,
                                    contentDescription = "Profile",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = userInitials,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.space3))

                    // --- FEED PHOTO CARD ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Photo: Shared Element connected to detailed photo
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "issue_cat_photo"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = sharedBoundsTransform,
                                    )
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.feed_cat_photo),
                                    contentDescription = "Card Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }

                            // Content below photo
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.space4),
                            ) {
                                // 2 lines Google Sans Light headline: Shared Element connected to detailed title
                                Text(
                                    text = "Community cat sheltered near the 4th Avenue park entrance",
                                    fontFamily = GoogleSansLightFamily,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 21.sp,
                                    lineHeight = 27.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "issue_cat_title"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = sharedBoundsTransform,
                                    ),
                                )

                                Spacer(Modifier.height(Spacing.space2))

                                // Summary text below the headline (max 1 line)
                                Text(
                                    text = "Spotted resting safely yesterday evening. Neighbors provided fresh water and food while awaiting checkup.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Spacer(Modifier.height(Spacing.space4))

                                // Bottom Action Row: Soft Teal "View report" button on left, Expressive Like button on right
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        onClick = {
                                            haptic(ExpressiveHaptic.Tap)
                                            onDetailExpandedChange(true)
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFBDEEEA),
                                            contentColor = Color(0xFF00201F),
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = Spacing.space4,
                                            vertical = Spacing.space2,
                                        ),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_book_text),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(Spacing.space2))
                                        Text(
                                            text = "View report",
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }

                                    Surface(
                                        onClick = {
                                            haptic(ExpressiveHaptic.Tap)
                                            isLiked = !isLiked
                                        },
                                        shape = MaterialShapes.Cookie9Sided.toShape(),
                                        color = if (isLiked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        },
                                        contentColor = if (isLiked) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                contentDescription = "Like",
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.space8))
                }
            } else {
                // =============================================================
                // DETAILED SCREEN: Shared photo (edge-to-edge behind status bar) + title landed on photo
                // =============================================================
                val detailScrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    // Scrollable content: Photo + Title + Detailed Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(detailScrollState),
                    ) {
                        // PHOTO CONTAINER: Shared Element (flies edge-to-edge behind status bar, 440dp height)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "issue_cat_photo"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                    boundsTransform = sharedBoundsTransform,
                                ),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.feed_cat_photo),
                                contentDescription = "Detailed Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )

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
                                                Color.Black.copy(alpha = 0.85f),
                                            ),
                                        ),
                                    ),
                            )

                            // --- TITLE LANDS DIRECTLY ON THE PHOTO (NO SUMMARY HERE) ---
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = Spacing.space4, vertical = Spacing.space4),
                            ) {
                                Text(
                                    text = "Community cat sheltered near the 4th Avenue park entrance",
                                    fontFamily = GoogleSansLightFamily,
                                    fontWeight = FontWeight.Light,
                                    fontSize = 23.sp,
                                    lineHeight = 29.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White,
                                    modifier = Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "issue_cat_title"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = sharedBoundsTransform,
                                    ),
                                )
                            }
                        }

                        // Detailed page content below the photo
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.space4, vertical = Spacing.space4)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
                        ) {
                            // 1. Detailed Summary / Story Description on Top
                            Text(
                                text = "The community cat was found curled up near the pedestrian gate during yesterday's evening rain. Community members provided a dry box, blanket, fresh water, and food. A local volunteer vet has been contacted to verify vaccination and microchip status tomorrow morning. Updates will be posted here as the situation progresses.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 26.sp,
                            )

                            val currentIssue = remember {
                                IssueStore.issues.firstOrNull { it.ticketId == "IG-2481" }
                                    ?: MockIssues.all.first()
                            }

                            // Entire status section enclosed in an outer outlined container
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    modifier = Modifier.padding(Spacing.space4),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.space3),
                                ) {
                                    Eyebrow(stringResource(R.string.detail_status_eyebrow))

                                    Timeline(events = currentIssue.timeline())
                                }
                            }

                            // Location Map Preview (strict 16:9 aspect ratio placeholder for future real map)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.detail_map_placeholder),
                                    contentDescription = "Location Map",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }

                            // Address Card (two lines)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.space4),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = "Location",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    Spacer(Modifier.width(Spacing.space3))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "4th Avenue, Near Tower Park Entrance",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Anna Nagar East, Chennai, Tamil Nadu 600040",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            // Bottom row: Reporter pill on left, Date pill on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Bottom Left: Praveen with Person icon
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space2),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_user),
                                            contentDescription = "Reported by",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Praveen",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }

                                // Bottom Right: Date pill with Date icon
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space2),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_calendar_check),
                                            contentDescription = "Date",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Nov 18, 2026",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(Spacing.space8))
                        }
                    }

                    // PINNED TOP BAR: Back button and 3-dots stay fixed at the top while scrolling
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = Spacing.space4, vertical = Spacing.space2)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = {
                                haptic(ExpressiveHaptic.Tap)
                                onDetailExpandedChange(false)
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Surface(
                            onClick = { haptic(ExpressiveHaptic.Tap) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
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
                verticalAlignment = Alignment.Top,
            ) {
                // Left status icon & connecting timeline vertical line
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(tone.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = tone.icon,
                            contentDescription = null,
                            tint = tone.content,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (index != events.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(16.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Status Pill Chip (compact)
                    Surface(
                        shape = CircleShape,
                        color = if (isActive) Color(0xFFBDEEEA) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isActive) Color(0xFF00201F) else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00796B)),
                                )
                            }
                            Text(
                                text = stringResource(event.status.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                            )
                        }
                    }

                    // Timestamp (no year)
                    Text(
                        text = formatEventTime(event.at),
                        style = ExpressiveTextStyles.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
