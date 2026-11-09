package com.svce.issuegram.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.svce.issuegram.R
import com.svce.issuegram.ui.theme.CustomColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuegramTopAppBar(
    onMessagesClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onMessagesClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_message_circle),
                    contentDescription = stringResource(R.string.action_messages),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Image(
                painter = painterResource(R.drawable.ic_issuegram_wordmark),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .width(120.dp)
                    .height(29.dp)
            )
        },
        actions = {
            IconButton(onClick = onProfileClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_profile),
                    contentDescription = stringResource(R.string.action_profile),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = CustomColors.topBarColors
    )
}
