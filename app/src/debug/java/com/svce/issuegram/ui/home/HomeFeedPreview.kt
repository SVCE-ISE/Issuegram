package com.svce.issuegram.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.svce.issuegram.R
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.ui.components.LocalIssuegramPreviewMedia
import com.svce.issuegram.ui.theme.IssuegramTheme

/** Design fixtures belong to debug builds; production starts with real server data. */
@Preview(showBackground = true)
@Composable
private fun HomeFeedPreview() {
    IssuegramTheme {
        CompositionLocalProvider(LocalIssuegramPreviewMedia provides mapOf(
            "drawable://feed_1" to R.drawable.feed_1,
            "drawable://feed1_img2" to R.drawable.feed1_img2,
        )) {
            HomeFeedScreen(issues = listOf(CivicIssueDto(
                id = 1,
                ticketCode = "CIV-PREVIEW",
                authorId = "11111111-1111-4111-8111-111111111111",
                authorUsername = "citizen_preview",
                title = "Damaged pavement near the park",
                caption = "Several paving stones are loose near the park entrance.",
                category = "Public Works",
                locationName = "Park entrance",
                latitude = 0.0,
                longitude = 0.0,
                mediaUrls = listOf("drawable://feed_1", "drawable://feed1_img2"),
            )))
        }
    }
}
