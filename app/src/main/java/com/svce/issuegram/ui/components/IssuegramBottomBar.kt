package com.svce.issuegram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.svce.issuegram.ui.navigation.Destination

@Composable
fun IssuegramBottomBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
) {
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Background covers the bar plus the gesture region so both read as one surface,
    // while the items stay centred inside the 52dp bar itself.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        NavigationBar(
            modifier = Modifier.height(52.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            windowInsets = WindowInsets(0),
        ) {
            Destination.entries.forEach { destination ->
                NavigationBarItem(
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            painter = painterResource(destination.iconRes),
                            contentDescription = stringResource(destination.labelRes),
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = null,
                    colors = NavigationBarItemDefaults.colors()
                )
            }
        }
        if (navigationBarInset > 0.dp) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navigationBarInset)
            )
        }
    }
}
