package com.svce.issuegram.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.svce.issuegram.IssuegramApplication
import com.svce.issuegram.R
import com.svce.issuegram.data.mapper.IssueMapper
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.di.ViewModelFactory
import com.svce.issuegram.ui.camera.PixelCameraScreen
import com.svce.issuegram.ui.feed.FeedViewModel
import com.svce.issuegram.ui.map.CivicMapScreen
import com.svce.issuegram.ui.profile.ProfileScreen
import com.svce.issuegram.ui.profile.ProfileViewModel
import com.svce.issuegram.ui.screens.AlertsScreen
import com.svce.issuegram.ui.screens.AlertsViewModel
import com.svce.issuegram.ui.screens.FeedScreenV6
import com.svce.issuegram.ui.screens.IssueDetailScreen
import com.svce.issuegram.ui.screens.IssueDetailViewModel
import com.svce.issuegram.ui.screens.MyIssuesScreen
import kotlinx.coroutines.launch

private enum class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Feed("feed", R.string.tab_feed, Icons.Outlined.Home, Icons.Rounded.Home),
    Map("map", R.string.tab_map, Icons.Outlined.Map, Icons.Rounded.Map),
    Camera("camera", R.string.tab_create, Icons.Outlined.PhotoCamera, Icons.Rounded.PhotoCamera),
    MyIssues("my_issues", R.string.tab_my_issues, Icons.AutoMirrored.Outlined.Assignment, Icons.AutoMirrored.Rounded.Assignment),
    Notifications("notifications", R.string.tab_notifications, Icons.Outlined.NotificationsNone, Icons.Rounded.Notifications),
    Profile("profile", R.string.tab_profile, Icons.Outlined.Person, Icons.Rounded.Person)
}

@Composable
fun IssuegramApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var isV4DetailExpanded by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val appContainer = remember(context) {
        (context.applicationContext as IssuegramApplication).appContainer
    }
    val viewModelFactory = remember(appContainer) {
        ViewModelFactory(appContainer)
    }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            val isDetailRoute = currentRoute?.startsWith("issue") == true
            if (!isV4DetailExpanded && !isDetailRoute) {
                val destinations = listOf(
                    TopLevelDestination.Feed,
                    TopLevelDestination.Map,
                    TopLevelDestination.Camera
                )
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    destinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentPadding = PaddingValues(0.dp)
        var mapInitialCenter by remember { mutableStateOf<LatLng?>(null) }
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Feed.route,
            modifier = Modifier.padding(contentPadding)
        ) {
            composable(TopLevelDestination.Feed.route) {
                val feedViewModel: FeedViewModel = viewModel(factory = viewModelFactory)
                val onIssueClick: (String) -> Unit = { ticketId ->
                    navController.navigate("issue/$ticketId")
                }
                FeedScreenV6(
                    viewModel = feedViewModel,
                    onIssueClick = onIssueClick,
                    onAlertsClick = {
                        navController.navigate(TopLevelDestination.Notifications.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onProfileClick = {
                        navController.navigate(TopLevelDestination.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenMapLocation = { lat, lon ->
                        mapInitialCenter = LatLng(lat, lon)
                        navController.navigate(TopLevelDestination.Map.route)
                    },
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    isDetailExpanded = isV4DetailExpanded,
                    onDetailExpandedChange = { isV4DetailExpanded = it }
                )
            }
            composable(TopLevelDestination.MyIssues.route) {
                val feedIssues by appContainer.getFeedIssuesUseCase().collectAsState(initial = emptyList())
                MyIssuesScreen(
                    issues = feedIssues,
                    onIssueClick = { ticketId ->
                        navController.navigate("issue/$ticketId")
                    }
                )
            }
            composable(TopLevelDestination.Map.route) {
                val feedIssues by appContainer.getFeedIssuesUseCase().collectAsState(initial = emptyList())
                val civicIssues = remember(feedIssues) {
                    feedIssues.map { IssueMapper.toCivicDto(it) }
                }
                CivicMapScreen(
                    issues = civicIssues,
                    initialCenter = mapInitialCenter,
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    onOpenIssue = { issue ->
                        navController.navigate("issue/${issue.ticketCode.removePrefix("#")}")
                    }
                )
            }
            composable(TopLevelDestination.Camera.route) {
                PixelCameraScreen(
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    onRequestPickLocationOnMap = {
                        navController.navigate(TopLevelDestination.Map.route)
                    },
                    onPostIssue = { title, description, category, locationName, subLocation, lat, lon, capturedFile, pickedUri ->
                        val photoUri = pickedUri?.toString() ?: capturedFile?.let { Uri.fromFile(it).toString() }
                        val cat = when {
                            category.contains("pothole", ignoreCase = true) -> IssueCategory.Pothole
                            category.contains(
                                "streetlight",
                                ignoreCase = true
                            ) ||
                                category.contains("light", ignoreCase = true) -> IssueCategory.Streetlight
                            category.contains("garbage", ignoreCase = true) -> IssueCategory.Garbage
                            category.contains("water", ignoreCase = true) -> IssueCategory.Water
                            category.contains("drain", ignoreCase = true) -> IssueCategory.Drainage
                            category.contains("road", ignoreCase = true) -> IssueCategory.Road
                            category.contains("safety", ignoreCase = true) -> IssueCategory.Safety
                            else -> IssueCategory.Other
                        }
                        val currentUser = Firebase.auth.currentUser
                        val authorName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Praveen Zalaki"
                        val authorUsername = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: authorName

                        coroutineScope.launch {
                            appContainer.createIssueUseCase(
                                title = title,
                                description = description,
                                category = cat,
                                locationName = locationName,
                                photoUri = photoUri,
                                latitude = lat,
                                longitude = lon,
                                authorName = authorName,
                                authorUsername = authorUsername
                            )
                        }
                        navController.navigate(TopLevelDestination.Feed.route) {
                            popUpTo(TopLevelDestination.Feed.route) { inclusive = false }
                        }
                    }
                )
            }
            composable(TopLevelDestination.Notifications.route) {
                val alertsViewModel: AlertsViewModel = viewModel(factory = viewModelFactory)
                AlertsScreen(
                    viewModel = alertsViewModel,
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    onBack = { navController.popBackStack() },
                    onIssueClick = { ticketId ->
                        navController.navigate("issue/$ticketId")
                    }
                )
            }
            composable(TopLevelDestination.Profile.route) {
                val profileViewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
                ProfileScreen(
                    viewModel = profileViewModel,
                    onOpenIssue = { issue ->
                        navController.navigate("issue/${issue.ticketCode}")
                    },
                    onSignOut = {
                        profileViewModel.signOut()
                    }
                )
            }
            composable(
                route = "issue/{ticketId}",
                arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
            ) { backStackEntry ->
                val ticketId = backStackEntry.arguments?.getString("ticketId").orEmpty()
                val detailViewModel: IssueDetailViewModel = viewModel(
                    key = ticketId,
                    factory = viewModelFactory
                )
                IssueDetailScreen(
                    ticketId = ticketId,
                    viewModel = detailViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenFullMap = { location ->
                        mapInitialCenter = location
                        navController.navigate(TopLevelDestination.Map.route)
                    }
                )
            }
        }
    }
}
