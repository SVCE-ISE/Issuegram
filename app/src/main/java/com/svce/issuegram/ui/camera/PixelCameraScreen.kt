package com.svce.issuegram.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.svce.issuegram.R
import com.svce.issuegram.ui.theme.IssuegramShapeDefaults
import com.svce.issuegram.data.local.ReportDraft
import com.svce.issuegram.data.local.ReportDraftStore
import com.svce.issuegram.data.location.currentDeviceLocation
import com.svce.issuegram.domain.ReportRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun PixelCameraScreen(
    modifier: Modifier = Modifier,
    isPosting: Boolean = false,
    onPostIssue: (
        caption: String,
        category: String,
        locationName: String,
        subLocation: String,
        latitude: Double,
        longitude: Double,
        capturedFile: File?,
        pickedUri: Uri?,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val draftStore = remember(context) { ReportDraftStore(context) }
    val restoredDraft = remember { draftStore.read() }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraMounted by remember { mutableStateOf(true) }
    var takingPhoto by remember { mutableStateOf(false) }
    var confirmingDraftDiscard by remember { mutableStateOf(false) }
    var choosingLocation by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) { onDispose { cameraMounted = false; cameraProvider?.unbindAll() } }

    var isShutterTriggered by remember { mutableStateOf(false) }
    var isPhotoCaptured by rememberSaveable { mutableStateOf(restoredDraft.photoPath != null || restoredDraft.pickedUri != null) }
    var capturedPhotoPath by rememberSaveable { mutableStateOf(restoredDraft.photoPath) }
    val capturedPhotoFile = capturedPhotoPath?.let(::File)
    var pickedGalleryPath by rememberSaveable { mutableStateOf(restoredDraft.pickedUri) }
    val pickedGalleryUri = pickedGalleryPath?.let(Uri::parse)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!isPhotoCaptured && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pickedGalleryPath = uri.toString()
            capturedPhotoPath = null
            isPhotoCaptured = true
        }
    }

    var issueText by rememberSaveable { mutableStateOf(restoredDraft.caption) }
    var selectedCategory by rememberSaveable { mutableStateOf(restoredDraft.category) }
    var currentLocationText by rememberSaveable { mutableStateOf(restoredDraft.locationName) }
    var subLocationText by rememberSaveable { mutableStateOf(restoredDraft.detail) }
    var detectedLat by rememberSaveable { mutableStateOf(restoredDraft.latitude) }
    var detectedLon by rememberSaveable { mutableStateOf(restoredDraft.longitude) }
    LaunchedEffect(issueText, selectedCategory, currentLocationText, subLocationText, detectedLat, detectedLon, capturedPhotoPath, pickedGalleryPath) {
        draftStore.save(ReportDraft(issueText, selectedCategory, currentLocationText, subLocationText,
            detectedLat, detectedLon, capturedPhotoPath, pickedGalleryPath))
    }
    androidx.activity.compose.BackHandler(isPhotoCaptured && !isPosting) { isPhotoCaptured = false }
    LaunchedEffect(isPhotoCaptured) { if (isPhotoCaptured) cameraProvider?.unbindAll() }
    if (confirmingDraftDiscard) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmingDraftDiscard = false },
        title = { Text("Discard this draft?") },
        text = { Text("Remove this report's saved photo, description and location from this device?") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            capturedPhotoFile?.takeIf { it.parentFile?.name == "drafts" }?.delete()
            capturedPhotoPath = null; pickedGalleryPath = null; issueText = ""; selectedCategory = "Public Works"
            currentLocationText = ""; subLocationText = ""; detectedLat = null; detectedLon = null
            draftStore.clear(); isPhotoCaptured = false; confirmingDraftDiscard = false
        }) { Text("Discard draft") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmingDraftDiscard = false }) { Text("Keep draft") } },
    )
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

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
        targetValue = Color.White,
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
                    modifier = Modifier.fillMaxSize(),
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
                        // Fallback static preview underneath live CameraX
                        Image(
                            painter = painterResource(R.drawable.image_placeholder),
                            contentDescription = "Camera Viewfinder",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            if (cameraMounted && !isPhotoCaptured) runCatching {
                                                val provider = cameraProviderFuture.get()
                                                cameraProvider = provider
                                                val preview = Preview.Builder().build().also {
                                                    it.surfaceProvider = surfaceProvider
                                                }
                                                provider.unbindAll()
                                                provider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                                    preview,
                                                    imageCapture
                                                )
                                            }.onFailure { cameraError = "The camera couldn't start. Try again or choose a photo." }
                                        }, ContextCompat.getMainExecutor(ctx))
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

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
                        cameraError?.let { Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp), fontSize = 12.sp) }
                        Spacer(modifier = Modifier.height(4.dp))

                        // Shutter Row: Gallery Thumbnail on Left, Centered Shutter Button, Balanced Spacer on Right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Gallery Thumbnail Preview (opens Photo Picker)
                            Surface(
                                onClick = {
                                    galleryPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = CircleShape,
                                color = Color.DarkGray,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.image_placeholder),
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
                                        if (takingPhoto) return@clickable
                                        isShutterTriggered = true
                                        val photoFile = File(
                                            File(context.noBackupFilesDir, "drafts").apply { mkdirs() },
                                            "capture_${System.currentTimeMillis()}.jpg"
                                        )
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                        if (hasCameraPermission) {
                                            takingPhoto = true
                                            imageCapture.takePicture(
                                                outputOptions,
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageSavedCallback {
                                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                        takingPhoto = false
                                                        capturedPhotoFile?.takeIf { it.parentFile?.name == "drafts" }?.delete()
                                                        capturedPhotoPath = photoFile.path
                                                        pickedGalleryPath = null
                                                        isPhotoCaptured = true
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        takingPhoto = false
                                                        photoFile.delete()
                                                        cameraError = "Couldn't take the photo. Please try again or choose a photo."
                                                    }
                                                }
                                            )
                                        } else {
                                            cameraError = "Allow camera access or choose a photo from your gallery."
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                                    shape = CircleShape,
                                    color = pixelTeal,
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
                                            tint = pixelDarkText,
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
                val categories = com.svce.issuegram.domain.ReportRules.categories

                var isLocationLoading by remember { mutableStateOf(false) }
                var locationError by remember { mutableStateOf<String?>(null) }
                var locationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                fun fetchLocation() {
                    if (isLocationLoading) return
                    locationJob = scope.launch {
                        isLocationLoading = true
                        locationError = null
                        try {
                            val loc = currentDeviceLocation(context)
                            detectedLat = loc.latitude
                            detectedLon = loc.longitude
                            currentLocationText = String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
                            subLocationText = "Device location · accuracy ${loc.accuracy.toInt()} m"
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { locationError = "Location lookup timed out. Choose it on the map or retry." }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { locationError = "Couldn't get your location. Choose it on the map or retry." }
                        finally { isLocationLoading = false }
                    }
                }

                if (choosingLocation) {
                    LocationPickerDialog(
                        initialLatitude = detectedLat, initialLongitude = detectedLon,
                        onDismiss = { choosingLocation = false },
                        onSelected = { lat, lon ->
                            locationJob?.cancel()
                            detectedLat = lat; detectedLon = lon
                            currentLocationText = String.format(Locale.US, "%.5f, %.5f", lat, lon)
                            subLocationText = "Location chosen on map"
                            locationError = null; choosingLocation = false
                        }
                    )
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

                    if (detectedLat != null && detectedLon != null) return@LaunchedEffect
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
                                onClick = {
                                    if (!isPosting) {
                                        isPhotoCaptured = false
                                    }
                                },
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

                        val isPostEnabled = issueText.trim().length in 5..500 && !isPosting &&
                            detectedLat != null && detectedLon != null && (capturedPhotoFile?.exists() == true || pickedGalleryUri != null)

                        Button(
                            onClick = {
                                onPostIssue(
                                    issueText,
                                    selectedCategory,
                                    currentLocationText,
                                    subLocationText,
                                    detectedLat ?: return@Button,
                                    detectedLon ?: return@Button,
                                    capturedPhotoFile,
                                    pickedGalleryUri
                                )
                            },
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
                            if (isPosting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    color = Color(0xFF0F2A30),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Posting...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            } else {
                                Text(
                                    text = "Post",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
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
                            when {
                                capturedPhotoFile != null -> {
                                    AsyncImage(
                                        model = capturedPhotoFile,
                                        contentDescription = "Captured Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                pickedGalleryUri != null -> {
                                    AsyncImage(
                                        model = pickedGalleryUri,
                                        contentDescription = "Selected Gallery Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    Image(
                                        painter = painterResource(R.drawable.image_placeholder),
                                        contentDescription = "Captured Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Item 2: Dynamic Write Box + Category Selector (Middle Card)
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
                                // Civic Category Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    categories.forEach { cat ->
                                        val selected = cat == selectedCategory
                                        FilterChip(
                                            selected = selected,
                                            onClick = { selectedCategory = cat },
                                            label = {
                                                Text(
                                                    text = cat,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                selectedContainerColor = Color(0xFF67B7F7),
                                                selectedLabelColor = Color(0xFF0F2A30)
                                            ),
                                            border = null
                                        )
                                    }
                                }

                                TextField(
                                    value = issueText,
                                    onValueChange = {
                                        if (it.length <= 500) {
                                            issueText = it
                                        }
                                    },
                                    placeholder = {
                                        Text(
                                            text = "Write about the civic issue...",
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
                                        text = currentLocationText.ifBlank { "Choose the issue location" },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Text("Your report, photo and location will be public.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.TextButton(onClick = { confirmingDraftDiscard = true }, enabled = !isPosting) { Text("Discard draft") }
                    locationError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        androidx.compose.material3.TextButton(onClick = { choosingLocation = true }) { Text("Choose on map") }
                        androidx.compose.material3.TextButton(onClick = { fetchLocation() }, enabled = !isLocationLoading) { Text("Use my location") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
