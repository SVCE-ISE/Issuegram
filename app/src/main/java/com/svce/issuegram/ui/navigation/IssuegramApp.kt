package com.svce.issuegram.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
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
import com.svce.issuegram.R
import com.svce.issuegram.ui.screens.AlertsScreen
import com.svce.issuegram.ui.screens.FeedScreen
import com.svce.issuegram.ui.screens.FeedScreenV4
import com.svce.issuegram.ui.screens.FeedScreenV5
import com.svce.issuegram.ui.screens.FeedScreenV6
import com.svce.issuegram.ui.screens.IssueDetailScreen
import com.svce.issuegram.ui.screens.MyIssuesScreen
import com.svce.issuegram.ui.screens.ProfileScreen

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Feed("feed", R.string.tab_feed, Icons.Outlined.Home, Icons.Rounded.Home),
    MyIssues("my_issues", R.string.tab_my_issues, Icons.Outlined.Assignment, Icons.Rounded.Assignment),
    Notifications("notifications", R.string.tab_notifications, Icons.Outlined.NotificationsNone, Icons.Rounded.Notifications),
    Profile("profile", R.string.tab_profile, Icons.Outlined.Person, Icons.Rounded.Person),
}

enum class FeedVersion(val labelRes: Int, val supportRes: Int) {
    Cards(R.string.feed_version_cards, R.string.feed_version_cards_support),
    Sections(R.string.feed_version_sections, R.string.feed_version_sections_support),
    Editorial(R.string.feed_version_editorial, R.string.feed_version_editorial_support),
    Version4(R.string.feed_version_v4, R.string.feed_version_v4_support),
}

@Composable
fun IssuegramApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var feedVersion by rememberSaveable { mutableStateOf(FeedVersion.Version4) }
    var isV4DetailExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (!isV4DetailExpanded) {
                val destinations = if (feedVersion == FeedVersion.Version4) {
                    listOf(TopLevelDestination.Feed, TopLevelDestination.MyIssues)
                } else {
                    TopLevelDestination.entries
                }
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
        val contentPadding = if (feedVersion == FeedVersion.Version4) {
            PaddingValues(0.dp)
        } else {
            innerPadding
        }
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Feed.route,
            modifier = Modifier.padding(contentPadding),
        ) {
            composable(TopLevelDestination.Feed.route) {
                val onIssueClick: (String) -> Unit = { ticketId ->
                    navController.navigate("issue/$ticketId")
                }
                when (feedVersion) {
                    FeedVersion.Cards -> FeedScreen(onIssueClick = onIssueClick)
                    FeedVersion.Sections -> FeedScreenV4(onIssueClick = onIssueClick)
                    FeedVersion.Editorial -> FeedScreenV5(onIssueClick = onIssueClick)
                    FeedVersion.Version4 -> FeedScreenV6(
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
            }
            composable(TopLevelDestination.MyIssues.route) {
                MyIssuesScreen(
                    onIssueClick = { ticketId ->
                        navController.navigate("issue/$ticketId")
                    },
                )
            }
            composable(TopLevelDestination.Notifications.route) {
                AlertsScreen(
                    onIssueClick = { ticketId ->
                        navController.navigate("issue/$ticketId")
                    },
                )
            }
            composable(TopLevelDestination.Profile.route) {
                ProfileScreen(
                    feedVersion = feedVersion,
                    onFeedVersionChange = { feedVersion = it },
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
