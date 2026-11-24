package com.svce.issuegram.ui.navigation

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.google.android.gms.maps.model.LatLng
import com.svce.issuegram.IssuegramApplication
import com.svce.issuegram.R
import com.svce.issuegram.di.ViewModelFactory
import com.svce.issuegram.ui.admin.AdminManagementBottomSheet
import com.svce.issuegram.ui.admin.AdminViewModel
import com.svce.issuegram.ui.camera.CameraReportViewModel
import com.svce.issuegram.ui.camera.PixelCameraScreen
import com.svce.issuegram.ui.feed.FeedViewModel
import com.svce.issuegram.ui.map.CivicMapScreen
import com.svce.issuegram.ui.map.CivicMapViewModel
import com.svce.issuegram.ui.profile.ProfileScreen
import com.svce.issuegram.ui.profile.ProfileViewModel
import com.svce.issuegram.ui.screens.AlertsScreen
import com.svce.issuegram.ui.screens.AlertsViewModel
import com.svce.issuegram.ui.screens.FeedScreenV6
import com.svce.issuegram.ui.screens.IssueDetailScreen
import com.svce.issuegram.ui.screens.IssueDetailViewModel

private enum class TopLevelDestination(
    val key: NavKey,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    Feed(FeedKey, R.string.tab_feed, Icons.Outlined.Home, Icons.Rounded.Home),
    Map(MapKey, R.string.tab_map, Icons.Outlined.Map, Icons.Rounded.Map),
    Camera(CameraKey, R.string.tab_create, Icons.Outlined.PhotoCamera, Icons.Rounded.PhotoCamera)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuegramApp(
    initialTicketId: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(FeedKey)
    val currentKey = backStack.lastOrNull()
    var isV4DetailExpanded by rememberSaveable { mutableStateOf(false) }
    var showAdminBottomSheet by remember { mutableStateOf(false) }
    val adminSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as IssuegramApplication).appContainer
    }
    val viewModelFactory = remember(appContainer) {
        ViewModelFactory(appContainer)
    }
    val currentUserDomain by appContainer.observeCurrentUserUseCase().collectAsState(initial = null)
    val isAdmin = currentUserDomain?.isAdmin == true

    fun navigateToTab(key: NavKey) {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        if (key != FeedKey && backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    fun navigateToIssue(ticketId: String) {
        val cleanId = ticketId.removePrefix("#").trim()
        if (cleanId.isNotBlank()) {
            backStack.add(IssueDetailKey(cleanId))
        }
    }

    LaunchedEffect(initialTicketId) {
        val cleanId = initialTicketId?.removePrefix("#")?.takeIf { it.isNotBlank() }
        if (cleanId != null) {
            backStack.add(IssueDetailKey(cleanId))
            onDeepLinkConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            val isDetailRoute = currentKey is IssueDetailKey
            if (!isV4DetailExpanded && !isDetailRoute) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentKey == destination.key
                        val isFeedTab = destination == TopLevelDestination.Feed

                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(destination.key) },
                            icon = {
                                val iconModifier = if (isFeedTab) {
                                    Modifier.pointerInput(isAdmin) {
                                        detectTapGestures(
                                            onTap = { navigateToTab(destination.key) },
                                            onLongPress = {
                                                if (isAdmin) {
                                                    showAdminBottomSheet = true
                                                    Toast.makeText(context, "Admin Control Panel unlocked", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = null,
                                    modifier = iconModifier
                                )
                            },
                            label = {
                                val labelModifier = if (isFeedTab) {
                                    Modifier.pointerInput(isAdmin) {
                                        detectTapGestures(
                                            onTap = { navigateToTab(destination.key) },
                                            onLongPress = {
                                                if (isAdmin) {
                                                    showAdminBottomSheet = true
                                                    Toast.makeText(context, "Admin Control Panel unlocked", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                                Text(
                                    text = stringResource(destination.labelRes),
                                    modifier = labelModifier
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        var mapInitialCenter by remember { mutableStateOf<LatLng?>(null) }

        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            modifier = Modifier.padding(PaddingValues(0.dp)),
            entryProvider = entryProvider {
                entry<FeedKey> {
                    val feedViewModel: FeedViewModel = viewModel(factory = viewModelFactory)
                    FeedScreenV6(
                        viewModel = feedViewModel,
                        onIssueClick = { ticketId -> navigateToIssue(ticketId) },
                        onAlertsClick = { navigateToTab(NotificationsKey) },
                        onProfileClick = { navigateToTab(ProfileKey) },
                        onOpenMapLocation = { lat, lon ->
                            mapInitialCenter = LatLng(lat, lon)
                            navigateToTab(MapKey)
                        },
                        bottomBarPadding = innerPadding.calculateBottomPadding(),
                        isDetailExpanded = isV4DetailExpanded,
                        onDetailExpandedChange = { isV4DetailExpanded = it }
                    )
                }
                entry<MapKey> {
                    val mapViewModel: CivicMapViewModel = viewModel(factory = viewModelFactory)
                    val civicIssues by mapViewModel.issues.collectAsState()
                    CivicMapScreen(
                        issues = civicIssues,
                        initialCenter = mapInitialCenter,
                        bottomBarPadding = innerPadding.calculateBottomPadding(),
                        onOpenIssue = { issue -> navigateToIssue(issue.ticketCode) }
                    )
                }
                entry<CameraKey> {
                    val cameraViewModel: CameraReportViewModel = viewModel(factory = viewModelFactory)
                    val isPosting by cameraViewModel.isPosting.collectAsState()
                    PixelCameraScreen(
                        bottomBarPadding = innerPadding.calculateBottomPadding(),
                        isPosting = isPosting,
                        onRequestPickLocationOnMap = { navigateToTab(MapKey) },
                        onPostIssue = { title, description, category, locationName, _, lat, lon, capturedFile, pickedUri ->
                            val photoUri = pickedUri?.toString() ?: capturedFile?.let { Uri.fromFile(it).toString() }
                            cameraViewModel.submitIssue(
                                title = title,
                                description = description,
                                categoryName = category,
                                locationName = locationName,
                                photoUri = photoUri,
                                latitude = lat,
                                longitude = lon,
                                onSuccess = { navigateToTab(FeedKey) },
                                onError = { message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
                entry<NotificationsKey> {
                    val alertsViewModel: AlertsViewModel = viewModel(factory = viewModelFactory)
                    AlertsScreen(
                        viewModel = alertsViewModel,
                        bottomBarPadding = innerPadding.calculateBottomPadding(),
                        onBack = {
                            if (backStack.size > 1) backStack.removeLastOrNull()
                        },
                        onIssueClick = { ticketId -> navigateToIssue(ticketId) }
                    )
                }
                entry<ProfileKey> {
                    val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onOpenIssue = { issue -> navigateToIssue(issue.ticketCode) },
                        onSignOut = { profileViewModel.signOut() }
                    )
                }
                entry<IssueDetailKey> { key ->
                    val detailViewModel: IssueDetailViewModel = viewModel(
                        key = key.ticketId,
                        factory = viewModelFactory
                    )
                    IssueDetailScreen(
                        ticketId = key.ticketId,
                        viewModel = detailViewModel,
                        onBack = {
                            if (backStack.size > 1) backStack.removeLastOrNull()
                        },
                        onOpenFullMap = { location ->
                            mapInitialCenter = location
                            navigateToTab(MapKey)
                        }
                    )
                }
            }
        )
    }

    if (showAdminBottomSheet) {
        val adminViewModel: AdminViewModel = viewModel(factory = viewModelFactory)
        AdminManagementBottomSheet(
            viewModel = adminViewModel,
            onDismissRequest = { showAdminBottomSheet = false },
            sheetState = adminSheetState
        )
    }
}
