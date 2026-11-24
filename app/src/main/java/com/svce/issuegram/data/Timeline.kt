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
    // Collect completed milestones with their recorded timestamps
    val completedMilestonesMap = mutableMapOf<IssueStatus, Instant>()
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
                completedMilestonesMap[eventStatus] = m.timestamp
            }
        }
    }

    val activeIndex = when (status) {
        IssueStatus.Reopened -> 1
        else -> canonicalStatuses.indexOf(status).coerceAtLeast(0)
    }

    return canonicalStatuses.mapIndexed { index, stepStatus ->
        val eventTime = when {
            // Step 1 is always Open: show createdAt
            index == 0 -> {
                completedMilestonesMap[IssueStatus.Open] ?: createdAt
            }
            // Step is explicitly marked as completed with a recorded timestamp
            completedMilestonesMap.containsKey(stepStatus) -> {
                completedMilestonesMap[stepStatus]
            }
            // Step is at or prior to active status but lacked milestone map entry: fallback to createdAt
            index <= activeIndex && index != 0 -> {
                createdAt
            }
            // Future uncompleted step: strictly null (display empty)
            else -> {
                null
            }
        }
        TimelineEvent(stepStatus, eventTime)
    }
}
