package com.svce.issuegram.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.svce.issuegram.R
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PixelCameraScreen(
    modifier: Modifier = Modifier,
) {
    var isVideoMode by remember { mutableStateOf(false) }
    var isShutterTriggered by remember { mutableStateOf(false) }
    var isPhotoCaptured by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val shutterFlashAlpha by animateFloatAsState(
        targetValue = if (isShutterTriggered) 0.85f else 0.0f,
        animationSpec = tween(durationMillis = 80),
        label = "shutterFlash"
    )

    LaunchedEffect(isShutterTriggered) {
        if (isShutterTriggered) {
            delay(100)
            isShutterTriggered = false
        }
    }

    // Pixel Material Teal Accent
    val pixelTeal = Color(0xFFAFC8CD)
    val pixelDarkText = Color(0xFF0F2A30)
    val controlBg = Color(0xFF1E2022)

    val shutterInnerColor by animateColorAsState(
        targetValue = if (isVideoMode) Color(0xFFE53935) else Color.White,
        label = "shutterInnerColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AnimatedContent(
            targetState = isPhotoCaptured,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(320)) togetherWith
                        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(320))
                } else {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(320)) togetherWith
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(320))
                }
            },
            label = "CameraScreenSlideTransition"
        ) { captured ->
            if (!captured) {
                // Camera Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Viewfinder Area (3:4 ratio standard portrait camera preview)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(0.dp))
                    ) {
                        // Camera Preview Image
                        Image(
                            painter = painterResource(R.drawable.camera_keyboard_preview),
                            contentDescription = "Camera Viewfinder",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Shutter Flash Overlay
                        if (shutterFlashAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White.copy(alpha = shutterFlashAlpha))
                            )
                        }
                    }

                    // Bottom Control Deck (Pure Black Background)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Shutter Row: Gallery Thumbnail on Left, Centered Shutter Button, Balanced Spacer on Right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Gallery Thumbnail Preview
                            Surface(
                                onClick = { /* Open Gallery */ },
                                shape = CircleShape,
                                color = Color.DarkGray,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.camera_gallery_thumb),
                                    contentDescription = "Gallery Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Center: Pixel Camera Shutter Button (Outer Ring + Solid Inner Circle)
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        scope.launch {
                                            isShutterTriggered = true
                                            delay(160)
                                            isPhotoCaptured = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Outer white circular ring
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(3.5.dp, Color.White),
                                    modifier = Modifier.fillMaxSize()
                                ) {}

                                // Inner solid shutter circle (white for photo, red for video)
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(shutterInnerColor, CircleShape)
                                )
                            }

                            // Right: Balanced invisible spacer so shutter remains strictly centered
                            Spacer(modifier = Modifier.size(54.dp))
                        }

                        // Photo / Video Switcher Pill (Centered)
                        Surface(
                            shape = CircleShape,
                            color = controlBg,
                            modifier = Modifier.height(42.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Photo tab
                                Surface(
                                    onClick = { isVideoMode = false },
                                    shape = CircleShape,
                                    color = if (!isVideoMode) pixelTeal else Color.Transparent,
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(44.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_cam_photo),
                                            contentDescription = "Photo Mode",
                                            tint = if (!isVideoMode) pixelDarkText else Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                // Video tab
                                Surface(
                                    onClick = { isVideoMode = true },
                                    shape = CircleShape,
                                    color = if (isVideoMode) pixelTeal else Color.Transparent,
                                    modifier = Modifier
                                        .height(36.dp)
                                        .width(44.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_cam_video),
                                            contentDescription = "Video Mode",
                                            tint = if (isVideoMode) pixelDarkText else Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Issue Details Screen
                var issueText by remember { mutableStateOf("") }
                var currentLocationText by remember { mutableStateOf("Detecting location...") }
                var isLocationLoading by remember { mutableStateOf(true) }
                val context = LocalContext.current

                fun fetchLocation() {
                    isLocationLoading = true
                    try {
                        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        val fineGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        val coarseGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (fineGranted || coarseGranted) {
                            val loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

                            if (loc != null) {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                try {
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        val addr = addresses[0]
                                        val feature = addr.subLocality ?: addr.featureName ?: addr.thoroughfare ?: "SVCE Campus"
                                        val locality = addr.locality ?: "Sriperumbudur"
                                        currentLocationText = "$feature, $locality"
                                    } else {
                                        currentLocationText = "SVCE Campus, Sriperumbudur (${String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)})"
                                    }
                                } catch (e: Exception) {
                                    currentLocationText = "SVCE Campus, Sriperumbudur (${String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)})"
                                }
                            } else {
                                currentLocationText = "SVCE Campus, Sriperumbudur (12.9864° N, 80.0031° E)"
                            }
                        } else {
                            currentLocationText = "SVCE Campus, Sriperumbudur (12.9864° N, 80.0031° E)"
                        }
                    } catch (e: Exception) {
                        currentLocationText = "SVCE Campus, Sriperumbudur (12.9864° N, 80.0031° E)"
                    } finally {
                        isLocationLoading = false
                    }
                }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    fetchLocation()
                }

                LaunchedEffect(Unit) {
                    val fineGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val coarseGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (fineGranted || coarseGranted) {
                        fetchLocation()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(scrollState)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { isPhotoCaptured = false },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = "Back to Camera",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                text = "New Issue",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                    val isPostEnabled = issueText.isNotBlank()

                    Button(
                        onClick = { /* Post Issue Action */ },
                        enabled = isPostEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF67B7F7),
                            contentColor = Color(0xFF0F2A30),
                            disabledContainerColor = Color(0xFF67B7F7).copy(alpha = 0.3f),
                            disabledContentColor = Color(0xFF8B949E)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Post",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Segmented 3-Item Grouped List (Like in Home Feed)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Item 1: Full Size Captured Image (Top Card)
                    Card(
                        shape = IssuegramShapeDefaults.topListItemShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.camera_keyboard_preview),
                            contentDescription = "Captured Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Item 2: Dynamic Write Box (Middle Card)
                    Card(
                        shape = IssuegramShapeDefaults.middleListItemShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            TextField(
                                value = issueText,
                                onValueChange = {
                                    if (it.length <= 500) {
                                        issueText = it
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = "Write about the issue...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 15.sp
                                    )
                                },
                                minLines = 3,
                                maxLines = 8,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "${issueText.length} / 500",
                                    color = if (issueText.length >= 450) Color(0xFFEF5350) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Item 3: Auto Location Box (Bottom Card)
                    Card(
                        shape = IssuegramShapeDefaults.bottomListItemShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_map_pin),
                                contentDescription = "Current Location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )

                            if (isLocationLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Getting current location...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    text = currentLocationText,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
            }
        }
    }
}
