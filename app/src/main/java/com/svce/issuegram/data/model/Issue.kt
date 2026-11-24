package com.svce.issuegram.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.svce.issuegram.R
import java.time.Instant

enum class IssueCategory(@StringRes val labelRes: Int) {
    Pothole(R.string.category_pothole),
    Streetlight(R.string.category_streetlight),
    Garbage(R.string.category_garbage),
    Water(R.string.category_water),
    Drainage(R.string.category_drainage),
    Road(R.string.category_road),
    Safety(R.string.category_safety),
    Other(R.string.category_other)
}

enum class IssueStatus(@StringRes val labelRes: Int) {
    Open(R.string.status_open),
    Acknowledged(R.string.status_acknowledged),
    Assigned(R.string.status_assigned),
    InProgress(R.string.status_in_progress),
    Resolved(R.string.status_resolved),
    Reopened(R.string.status_reopened)
}

@Immutable
data class IssueMilestone(
    val id: Long,
    val stepOrder: Int,
    val title: String,
    val actor: String,
    val timestamp: Instant,
    val isCompleted: Boolean
)

@Immutable
data class Issue(
    /** Human-readable ticket ID, e.g. "IG-2481". */
    val ticketId: String,
    val title: String,
    val description: String?,
    val category: IssueCategory,
    val status: IssueStatus,
    val area: String,
    val locationName: String,
    val authorName: String,
    val createdAt: Instant,
    val likes: Int,
    val photoUri: String? = null,
    val isMine: Boolean = false,
    val isLikedByMe: Boolean = false,
    val latitude: Double = 12.9716,
    val longitude: Double = 77.5946,
    val authorId: String? = null,
    val authorUsername: String? = null,
    val milestones: List<IssueMilestone> = emptyList()
)
