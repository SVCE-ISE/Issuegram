package com.svce.issuegram

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.svce.issuegram.ui.components.IssuegramBottomBar
import com.svce.issuegram.ui.components.IssuegramTopAppBar
import com.svce.issuegram.ui.navigation.Destination
import com.svce.issuegram.ui.theme.IssuegramTheme

@Composable
fun IssuegramApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { IssuegramTopAppBar() },
        bottomBar = {
            IssuegramBottomBar(
                selected = selected,
                onSelect = { selected = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IssuegramAppPreview() {
    IssuegramTheme {
        IssuegramApp()
    }
}
