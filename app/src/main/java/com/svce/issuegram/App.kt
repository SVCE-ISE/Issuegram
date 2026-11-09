package com.svce.issuegram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.svce.issuegram.ui.camera.PixelCameraScreen
import com.svce.issuegram.ui.components.IssuegramBottomBar
import com.svce.issuegram.ui.components.IssuegramTopAppBar
import com.svce.issuegram.ui.home.HomeFeedScreen
import com.svce.issuegram.ui.map.CampusMapScreen
import com.svce.issuegram.ui.messages.MessagesScreen
import com.svce.issuegram.ui.navigation.Destination
import com.svce.issuegram.ui.profile.ProfileScreen
import com.svce.issuegram.ui.theme.IssuegramTheme

@Composable
fun IssuegramApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (selected != Destination.Create && selected != Destination.Messages) {
                IssuegramTopAppBar(
                    onMessagesClick = { selected = Destination.Messages },
                    onProfileClick = { selected = Destination.Profile }
                )
            }
        },
        bottomBar = {
            IssuegramBottomBar(
                selected = selected,
                onSelect = { selected = it }
            )
        }
    ) { innerPadding ->
        when (selected) {
            Destination.Home -> HomeFeedScreen(
                modifier = Modifier.padding(innerPadding)
            )
            Destination.Create -> PixelCameraScreen(
                modifier = Modifier.padding(innerPadding)
            )
            Destination.Map -> CampusMapScreen(
                modifier = Modifier.padding(innerPadding)
            )
            Destination.Profile -> ProfileScreen(
                modifier = Modifier.padding(innerPadding)
            )
            Destination.Messages -> MessagesScreen(
                modifier = Modifier.padding(innerPadding),
                onBackToHome = { selected = Destination.Home }
            )
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IssuegramAppPreview() {
    IssuegramTheme {
        IssuegramApp()
    }
}
