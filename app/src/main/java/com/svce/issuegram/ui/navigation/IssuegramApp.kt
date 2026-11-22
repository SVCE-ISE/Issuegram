package com.svce.issuegram.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import com.svce.issuegram.data.IssueStore
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.R
import com.svce.issuegram.ui.camera.PixelCameraScreen
import com.svce.issuegram.ui.map.CivicMapScreen
import com.svce.issuegram.ui.screens.AlertsScreen
import com.svce.issuegram.ui.screens.FeedScreenV6
import com.svce.issuegram.ui.screens.IssueDetailScreen
import com.svce.issuegram.ui.screens.MyIssuesScreen
import com.svce.issuegram.ui.profile.ProfileScreen

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Feed("feed", R.string.tab_feed, Icons.Outlined.Home, Icons.Rounded.Home),
    Map("map", R.string.tab_map, Icons.Outlined.Map, Icons.Rounded.Map),
    Camera("camera", R.string.tab_create, Icons.Outlined.PhotoCamera, Icons.Rounded.PhotoCamera),
    MyIssues("my_issues", R.string.tab_my_issues, Icons.AutoMirrored.Outlined.Assignment, Icons.AutoMirrored.Rounded.Assignment),
    Notifications("notifications", R.string.tab_notifications, Icons.Outlined.NotificationsNone, Icons.Rounded.Notifications),
    Profile("profile", R.string.tab_profile, Icons.Outlined.Person, Icons.Rounded.Person),
}

@Composable
fun IssuegramApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var isV4DetailExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            val isDetailRoute = currentRoute?.startsWith("issue") == true
            if (!isV4DetailExpanded && !isDetailRoute) {
                val destinations = listOf(
                    TopLevelDestination.Feed,
                    TopLevelDestination.Map,
                    TopLevelDestination.Camera,
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
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(0.dp)
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Feed.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(TopLevelDestination.Feed.route) {
                val onIssueClick: (String) -> Unit = { ticketId ->
                    navController.navigate("issue/$ticketId")
                }
                FeedScreenV6(
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
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    isDetailExpanded = isV4DetailExpanded,
                    onDetailExpandedChange = { isV4DetailExpanded = it },
                )
            }
            composable(TopLevelDestination.MyIssues.route) {
                MyIssuesScreen(
                    onIssueClick = { ticketId ->
                        navController.navigate("issue/$ticketId")
                    },
                )
            }
            composable(TopLevelDestination.Map.route) {
                CivicMapScreen(
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    onOpenIssue = { issue ->
                        navController.navigate("issue/IG-${issue.id}")
                    },
                )
            }
            composable(TopLevelDestination.Camera.route) {
                PixelCameraScreen(
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    onRequestPickLocationOnMap = {
                        navController.navigate(TopLevelDestination.Map.route)
                    },
                    onPostIssue = { title, description, category, locationName, _, _, _, capturedFile, pickedUri ->
                        val photoUri = pickedUri?.toString() ?: capturedFile?.let { Uri.fromFile(it).toString() }
                        val cat = when (category.lowercase()) {
                            "pothole" -> IssueCategory.Pothole
                            "streetlight" -> IssueCategory.Streetlight
                            "garbage" -> IssueCategory.Garbage
                            "water" -> IssueCategory.Water
                            "drainage" -> IssueCategory.Drainage
                            "road" -> IssueCategory.Road
                            "safety" -> IssueCategory.Safety
                            else -> IssueCategory.Other
                        }
                        IssueStore.add(
                            title = title,
                            description = description,
                            category = cat,
                            locationName = locationName,
                            photoUri = photoUri,
                        )
                        navController.navigate(TopLevelDestination.Feed.route) {
                            popUpTo(TopLevelDestination.Feed.route) { inclusive = false }
                        }
                    },
                )
            }
            composable(TopLevelDestination.Notifications.route) {
                AlertsScreen(
                    bottomBarPadding = innerPadding.calculateBottomPadding(),
                    onBack = { navController.popBackStack() },
                    onIssueClick = { ticketId ->
                        navController.navigate("issue/$ticketId")
                    },
                )
            }
            composable(TopLevelDestination.Profile.route) {
                ProfileScreen(
                    onOpenIssue = { issue ->
                        navController.navigate("issue/${issue.ticketCode}")
                    },
                )
            }
            composable(
                route = "issue/{ticketId}",
                arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
            ) { backStackEntry ->
                IssueDetailScreen(
                    ticketId = backStackEntry.arguments?.getString("ticketId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
