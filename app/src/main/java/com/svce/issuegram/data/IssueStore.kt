package com.svce.issuegram.data

import androidx.compose.runtime.mutableStateListOf
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant

/** In-memory issue store until Firestore is wired in. Posts appear at the top of the feed. */
object IssueStore {

    private var nextTicketNumber = 2482

    val issues = mutableStateListOf<Issue>()

    fun add(
        title: String,
        description: String?,
        category: IssueCategory,
        locationName: String,
        photoUri: String?,
        latitude: Double = 12.9716,
        longitude: Double = 77.5946,
        authorName: String = "You",
        authorUsername: String? = null,
    ): Issue {
        val issue = Issue(
            ticketId = "IG-${nextTicketNumber++}",
            title = title.trim(),
            description = description?.trim()?.ifBlank { null },
            category = category,
            status = IssueStatus.Open,
            area = if (locationName.contains(",")) locationName.substringAfter(",").trim() else locationName.trim(),
            locationName = locationName.trim(),
            authorName = authorName,
            authorUsername = authorUsername,
            latitude = latitude,
            longitude = longitude,
            createdAt = Instant.now(),
            likes = 0,
            photoUri = photoUri,
            isMine = true,
        )
        issues.add(0, issue)
        return issue
    }
}
