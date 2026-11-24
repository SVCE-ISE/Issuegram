package com.svce.issuegram.data

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant

data class TimelineEvent(val status: IssueStatus, val at: Instant?)

/**
 * The 5 canonical sequential lifecycle steps requested for the civic issue timeline:
 * 1. Open (Issue Reported)
 * 2. Acknowledged (Reviewed by Civic Authority)
 * 3. Assigned (Dispatched to Department)
 * 4. InProgress (Field Operations Underway)
 * 5. Resolved (Action Completed & Verified)
 */
private val canonicalStatuses = listOf(
    IssueStatus.Open,
    IssueStatus.Acknowledged,
    IssueStatus.Assigned,
    IssueStatus.InProgress,
    IssueStatus.Resolved
)

fun Issue.timeline(): List<TimelineEvent> {
    // Map completed milestone timestamps if present
    val milestoneTimeByStatus = mutableMapOf<IssueStatus, Instant>()
    if (milestones.isNotEmpty()) {
        for (m in milestones) {
            val eventStatus = when {
                m.title.contains("resolved", ignoreCase = true) || m.stepOrder >= 5 -> IssueStatus.Resolved
                m.title.contains("progress", ignoreCase = true) || m.stepOrder == 4 -> IssueStatus.InProgress
                m.title.contains("assigned", ignoreCase = true) || m.stepOrder == 3 -> IssueStatus.Assigned
                m.title.contains("acknowledged", ignoreCase = true) || m.stepOrder == 2 -> IssueStatus.Acknowledged
                else -> IssueStatus.Open
            }
            if (m.isCompleted) {
                milestoneTimeByStatus[eventStatus] = m.timestamp
            }
        }
    }

    val activeIndex = canonicalStatuses.indexOf(status).coerceAtLeast(0)

    return canonicalStatuses.mapIndexed { index, stepStatus ->
        val eventTime = when {
            // Milestone timestamp explicitly recorded
            milestoneTimeByStatus.containsKey(stepStatus) -> {
                milestoneTimeByStatus[stepStatus]
            }
            // Step 0 is Open: always has at least createdAt
            index == 0 -> {
                createdAt
            }
            // Reached step but no custom milestone time recorded
            index <= activeIndex -> {
                createdAt
            }
            // Future unreached step: no dummy fallback, show empty
            else -> {
                null
            }
        }
        TimelineEvent(stepStatus, eventTime)
    }
}
