package com.svce.issuegram.data

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

data class TimelineEvent(val status: IssueStatus, val at: Instant)

/**
 * The 5 canonical sequential lifecycle steps requested for the civic issue timeline:
 * 1. Open (Issue Reported)
 * 2. Acknowledged (Reviewed by Civic Authority)
 * 3. Assigned (Dispatched to Department)
 * 4. InProgress (Field Operations Underway)
 * 5. Resolved (Action Completed & Verified)
 *
 * Offsets represent realistic progression hours after creation time.
 */
private val progression = listOf(
    IssueStatus.Open to 0L,
    IssueStatus.Acknowledged to 8L,
    IssueStatus.Assigned to 24L,
    IssueStatus.InProgress to 48L,
    IssueStatus.Resolved to 96L
)

fun Issue.timeline(): List<TimelineEvent> {
    // Map existing completed milestone timestamps if present
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

    // Always output all 5 progression steps: Open > Acknowledged > Assigned > InProgress > Resolved
    return progression.map { (eventStatus, hours) ->
        val time = milestoneTimeByStatus[eventStatus] ?: createdAt.plus(hours, ChronoUnit.HOURS)
        TimelineEvent(eventStatus, time)
    }
}
