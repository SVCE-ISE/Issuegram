package com.svce.issuegram.ui.camera

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.svce.issuegram.R
import com.svce.issuegram.data.local.ReportDraft
import com.svce.issuegram.data.local.ReportDraftStore
import com.svce.issuegram.data.location.currentDeviceLocation
import com.svce.issuegram.data.location.reverseGeocode
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.ui.components.StatusBadge
import com.svce.issuegram.ui.theme.ExpressiveTextStyles
import com.svce.issuegram.ui.theme.GoogleSans
import com.svce.issuegram.ui.theme.Spacing
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelCameraScreen(
    modifier: Modifier = Modifier,
    isPosting: Boolean = false,
    pickedLatitude: Double? = null,
    pickedLongitude: Double? = null,
    onRequestPickLocationOnMap: () -> Unit = {},
    bottomBarPadding: Dp = 0.dp,
    onPostIssue: (
        title: String,
        description: String,
        category: String,
        locationName: String,
        subLocation: String,
        latitude: Double,
        longitude: Double,
        capturedFile: File?,
        pickedUri: Uri?
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> }
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
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    DisposableEffect(Unit) {
        onDispose {
            cameraMounted = false
            cameraProvider?.unbindAll()
        }
    }

    var isShutterTriggered by remember { mutableStateOf(false) }
    var isPhotoCaptured by rememberSaveable { mutableStateOf(restoredDraft.photoPath != null || restoredDraft.pickedUri != null) }
    var capturedPhotoPath by rememberSaveable { mutableStateOf(restoredDraft.photoPath) }
    val capturedPhotoFile = capturedPhotoPath?.let(::File)
    var pickedGalleryPath by rememberSaveable { mutableStateOf(restoredDraft.pickedUri) }
    val pickedGalleryUri = pickedGalleryPath?.let(Uri::parse)

    var latestGalleryUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(isPhotoCaptured, pickedGalleryPath, capturedPhotoPath) {
        latestGalleryUri = runCatching {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    val galleryThumbnailUri = pickedGalleryUri
        ?: capturedPhotoFile?.takeIf { it.exists() }?.let { Uri.fromFile(it) }
        ?: latestGalleryUri

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

    var issueTitle by rememberSaveable { mutableStateOf(restoredDraft.title) }
    var issueText by rememberSaveable { mutableStateOf(restoredDraft.caption) }
    var selectedCategory by rememberSaveable { mutableStateOf(restoredDraft.category) }
    var currentLocationText by rememberSaveable { mutableStateOf(restoredDraft.locationName) }
    var subLocationText by rememberSaveable { mutableStateOf(restoredDraft.detail) }
    var detectedLat by rememberSaveable { mutableStateOf(restoredDraft.latitude) }
    var detectedLon by rememberSaveable { mutableStateOf(restoredDraft.longitude) }
    LaunchedEffect(pickedLatitude, pickedLongitude) {
        if (pickedLatitude != null && pickedLongitude != null) {
            detectedLat = pickedLatitude
            detectedLon = pickedLongitude
            val coordinates = String.format(Locale.US, "%.5f, %.5f", pickedLatitude, pickedLongitude)
            currentLocationText = coordinates
            subLocationText = "Location chosen on map"
            currentLocationText = reverseGeocode(context, pickedLatitude, pickedLongitude) ?: coordinates
        }
    }
    LaunchedEffect(
        issueTitle,
        issueText,
        selectedCategory,
        currentLocationText,
        subLocationText,
        detectedLat,
        detectedLon,
        capturedPhotoPath,
        pickedGalleryPath
    ) {
        if (capturedPhotoPath != null || pickedGalleryPath != null || issueTitle.isNotBlank() || issueText.isNotBlank()) {
            draftStore.save(
                ReportDraft(
                    issueTitle,
                    issueText,
                    selectedCategory,
                    currentLocationText,
                    subLocationText,
                    detectedLat,
                    detectedLon,
                    capturedPhotoPath,
                    pickedGalleryPath
                )
            )
        } else {
            draftStore.clear()
        }
    }
    fun resetCameraScreenState() {
        capturedPhotoFile?.takeIf { it.parentFile?.name == "drafts" }?.delete()
        capturedPhotoPath = null
        pickedGalleryPath = null
        issueTitle = ""
        issueText = ""
        selectedCategory = "Road"
        currentLocationText = ""
        subLocationText = ""
        detectedLat = null
        detectedLon = null
        draftStore.clear()
        isPhotoCaptured = false
    }
    androidx.activity.compose.BackHandler(isPhotoCaptured && !isPosting) { isPhotoCaptured = false }
    LaunchedEffect(isPhotoCaptured) { if (isPhotoCaptured) cameraProvider?.unbindAll() }
    if (confirmingDraftDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingDraftDiscard = false },
            title = { Text("Discard this draft?", fontFamily = GoogleSans, fontWeight = FontWeight.Bold) },
            text = { Text("Remove this report's saved photo, title, description and location from this device?", fontFamily = GoogleSans) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    resetCameraScreenState()
                    confirmingDraftDiscard = false
                }) { Text("Discard draft", fontFamily = GoogleSans) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmingDraftDiscard = false }) { Text("Keep draft", fontFamily = GoogleSans) } }
        )
    }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Viewfinder Area (Takes remaining space with elegant rounded corners)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        // Fallback background while CameraX initialises
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF16181A))
                        )

                        if (hasCameraPermission) {
                            AndroidView(
                                factory = { ctx ->
                                    PreviewView(ctx).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            if (cameraMounted && !isPhotoCaptured) {
                                                runCatching {
                                                    val provider = cameraProviderFuture.get()
                                                    cameraProvider = provider
                                                    val preview = Preview.Builder().build().also {
                                                        it.surfaceProvider = surfaceProvider
                                                    }
                                                    provider.unbindAll()
                                                    provider.bindToLifecycle(
                                                        lifecycleOwner,
                                                        lensFacing,
                                                        preview,
                                                        imageCapture
                                                    )
                                                }.onFailure { cameraError = "The camera couldn't start. Try again or choose a photo." }
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                    }
                                },
                                update = { view ->
                                    if (cameraMounted && !isPhotoCaptured && cameraProvider != null) {
                                        runCatching {
                                            val provider = cameraProvider ?: return@runCatching
                                            provider.unbindAll()
                                            val preview = Preview.Builder().build().also {
                                                it.surfaceProvider = view.surfaceProvider
                                            }
                                            provider.bindToLifecycle(
                                                lifecycleOwner,
                                                lensFacing,
                                                preview,
                                                imageCapture
                                            )
                                        }
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
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = bottomBarPadding + 14.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        cameraError?.let { Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp), fontSize = 12.sp) }

                        // Mode Switcher Pill (Centered ABOVE Shutter Row)
                        Surface(
                            shape = CircleShape,
                            color = controlBg,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = pixelTeal,
                                    modifier = Modifier
                                        .height(32.dp)
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Shutter Row: Gallery Thumbnail on Left, Centered Shutter Button, Switch Camera on Right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
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
                                color = Color(0xFF2C2C2E),
                                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                if (galleryThumbnailUri != null) {
                                    AsyncImage(
                                        model = galleryThumbnailUri,
                                        contentDescription = "Gallery Thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoLibrary,
                                            contentDescription = "Open Gallery",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            // Center: Pixel Camera Shutter Button (Outer Ring + Solid Inner Circle)
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
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

                                // Inner solid shutter circle
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(shutterInnerColor, CircleShape)
                                )
                            }

                            // Right: Switch / Flip Camera Button
                            Surface(
                                onClick = {
                                    lensFacing = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    }
                                },
                                shape = CircleShape,
                                color = Color(0xFF2C2C2E),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cameraswitch,
                                        contentDescription = "Switch Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Issue Details Screen
                val categories = listOf("Pothole", "Streetlight", "Garbage", "Water", "Drainage", "Road", "Safety", "Other")

                var isLocationLoading by remember { mutableStateOf(false) }
                var locationError by remember { mutableStateOf<String?>(null) }
                var locationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                fun fetchLocation() {
                    if (isLocationLoading) return
                    locationJob?.cancel()
                    locationJob = scope.launch {
                        isLocationLoading = true
                        locationError = null
                        try {
                            val loc = currentDeviceLocation(context)
                            detectedLat = loc.latitude
                            detectedLon = loc.longitude
                            val coordinates = String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
                            currentLocationText = reverseGeocode(context, loc.latitude, loc.longitude) ?: coordinates
                            subLocationText = ""
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            locationError = "Location lookup timed out. Choose it on the map or retry."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            locationError = "Couldn't get your location. Choose it on the map or retry."
                        } finally {
                            isLocationLoading = false
                        }
                    }
                }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions.values.any { it }) {
                        fetchLocation()
                    } else {
                        locationError = "Location permission denied. Choose it on the map or retry."
                    }
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

                val effectiveDescription = issueText.trim().ifBlank { issueTitle.trim() }
                val isPostEnabled = issueTitle.trim().length in 3..100 &&
                    effectiveDescription.length in 5..500 &&
                    !isPosting &&
                    (capturedPhotoFile?.exists() == true || pickedGalleryUri != null)

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "New Issue Report",
                                    fontFamily = GoogleSans,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (!isPosting) {
                                        isPhotoCaptured = false
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back to camera",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            actions = {
                                if (issueTitle.isNotBlank() || issueText.isNotBlank() || capturedPhotoPath != null || pickedGalleryPath != null) {
                                    TextButton(onClick = { confirmingDraftDiscard = true }) {
                                        Text(
                                            text = "Discard",
                                            fontFamily = GoogleSans,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = Spacing.space4,
                                        end = Spacing.space4,
                                        top = Spacing.space3,
                                        bottom = Spacing.space3 + bottomBarPadding
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.space3)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isPosting) {
                                            capturedPhotoFile?.takeIf { it.parentFile?.name == "drafts" }?.delete()
                                            capturedPhotoPath = null
                                            pickedGalleryPath = null
                                            isPhotoCaptured = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "Retake",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Button(
                                    onClick = {
                                        val title = issueTitle.trim()
                                        val desc = effectiveDescription
                                        val cat = selectedCategory
                                        val locName = currentLocationText.ifBlank { "Location on map" }
                                        val subLoc = subLocationText
                                        val lat = detectedLat ?: 12.9716
                                        val lon = detectedLon ?: 77.5946
                                        val photoFile = capturedPhotoFile
                                        val galleryUri = pickedGalleryUri

                                        // Persist photo to permanent app storage before clearing draft state so it is not deleted
                                        val permanentPhotoFile = if (photoFile != null && photoFile.exists()) {
                                            runCatching {
                                                val permanentDir = File(context.filesDir, "issue_photos").apply { mkdirs() }
                                                val target = File(permanentDir, "issue_${System.currentTimeMillis()}.jpg")
                                                photoFile.copyTo(target, overwrite = true)
                                                target
                                            }.getOrNull() ?: photoFile
                                        } else {
                                            null
                                        }

                                        resetCameraScreenState()

                                        onPostIssue(
                                            title,
                                            desc,
                                            cat,
                                            locName,
                                            subLoc,
                                            lat,
                                            lon,
                                            permanentPhotoFile ?: photoFile,
                                            galleryUri
                                        )
                                    },
                                    enabled = isPostEnabled,
                                    modifier = Modifier.weight(2f)
                                ) {
                                    if (isPosting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(Spacing.space2))
                                        Text(
                                            text = "Submitting...",
                                            fontFamily = GoogleSans,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "Submit Report",
                                            fontFamily = GoogleSans,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.space4)
                    ) {
                        Spacer(Modifier.height(Spacing.space1))

                        // 1. Photo: Hero Photo Box (16:10 aspect ratio matching IssueDetailScreen)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 10f)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                capturedPhotoFile != null -> {
                                    AsyncImage(
                                        model = capturedPhotoFile,
                                        contentDescription = "Captured Issue Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                pickedGalleryUri != null -> {
                                    AsyncImage(
                                        model = pickedGalleryUri,
                                        contentDescription = "Selected Issue Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(Spacing.space3))

                        // Status Badge Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(status = IssueStatus.Open)
                            Text(
                                text = "DRAFT REPORT",
                                fontFamily = GoogleSans,
                                style = ExpressiveTextStyles.dataSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(Spacing.space4))

                        // 2. Title
                        OutlinedTextField(
                            value = issueTitle,
                            onValueChange = { if (it.length <= 100) issueTitle = it },
                            placeholder = {
                                Text(
                                    text = "Title",
                                    fontFamily = GoogleSans,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = GoogleSans),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            supportingText = {
                                Text(
                                    text = "${issueTitle.length} / 100",
                                    modifier = Modifier.fillMaxWidth(),
                                    fontFamily = GoogleSans,
                                    style = ExpressiveTextStyles.dataSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        Spacer(Modifier.height(Spacing.space3))

                        // 3. Description
                        OutlinedTextField(
                            value = issueText,
                            onValueChange = { if (it.length <= 500) issueText = it },
                            placeholder = {
                                Text(
                                    text = "Description",
                                    fontFamily = GoogleSans,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            minLines = 4,
                            maxLines = 8,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = GoogleSans),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            supportingText = {
                                Text(
                                    text = "${issueText.length} / 500",
                                    modifier = Modifier.fillMaxWidth(),
                                    fontFamily = GoogleSans,
                                    style = ExpressiveTextStyles.dataSmall,
                                    color = if (issueText.length >= 450) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        Spacer(Modifier.height(Spacing.space3))

                        // 4. Category
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.space2)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = cat.equals(selectedCategory, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = {
                                        Text(
                                            text = cat,
                                            fontFamily = GoogleSans,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(Spacing.space3))

                        // 5. Location Minimap
                        val pinLatLng = remember(detectedLat, detectedLon) {
                            LatLng(detectedLat ?: 12.9716, detectedLon ?: 77.5946)
                        }
                        val minimapCameraState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(pinLatLng, 15f)
                        }
                        LaunchedEffect(pinLatLng) {
                            minimapCameraState.position = CameraPosition.fromLatLngZoom(pinLatLng, 15f)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { onRequestPickLocationOnMap() }
                        ) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = minimapCameraState,
                                uiSettings = MapUiSettings(
                                    zoomControlsEnabled = false,
                                    scrollGesturesEnabled = false,
                                    zoomGesturesEnabled = false,
                                    tiltGesturesEnabled = false,
                                    rotationGesturesEnabled = false,
                                    mapToolbarEnabled = false,
                                    myLocationButtonEnabled = false
                                ),
                                onMapClick = { onRequestPickLocationOnMap() }
                            ) {
                                Marker(
                                    state = rememberUpdatedMarkerState(position = pinLatLng),
                                    title = "Report Location"
                                )
                            }

                            // Tap to pick on map overlay button
                            Surface(
                                onClick = { onRequestPickLocationOnMap() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(Spacing.space2)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.space3, vertical = Spacing.space1),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.space1)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Map,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Pick on map",
                                        fontFamily = GoogleSans,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(Spacing.space3))

                        // 6. Address
                        OutlinedTextField(
                            value = currentLocationText,
                            onValueChange = { currentLocationText = it },
                            placeholder = {
                                Text(
                                    text = "Address",
                                    fontFamily = GoogleSans,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            singleLine = false,
                            maxLines = 3,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = if (isLocationLoading) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSans),
                            supportingText = {
                                val helper = subLocationText.ifBlank {
                                    if (detectedLat != null && detectedLon != null) {
                                        String.format(Locale.US, "GPS: %.5f, %.5f", detectedLat, detectedLon)
                                    } else {
                                        "Tap map above to adjust location"
                                    }
                                }
                                Text(
                                    text = helper,
                                    fontFamily = GoogleSans,
                                    style = ExpressiveTextStyles.dataSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        Spacer(Modifier.height(Spacing.space6))
                    }
                }
            }
        }
    }
}
