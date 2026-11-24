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
    if (milestones.isNotEmpty()) {
        val completedMilestones = milestones.filter { it.isCompleted }
        if (completedMilestones.isNotEmpty()) {
            val mappedEvents = completedMilestones.map { m ->
                val eventStatus = when {
                    m.title.contains("resolved", ignoreCase = true) || m.stepOrder >= 5 -> IssueStatus.Resolved
                    m.title.contains("progress", ignoreCase = true) || m.stepOrder == 4 -> IssueStatus.InProgress
                    m.title.contains("assigned", ignoreCase = true) || m.stepOrder == 3 -> IssueStatus.Assigned
                    m.title.contains("acknowledged", ignoreCase = true) || m.stepOrder == 2 -> IssueStatus.Acknowledged
                    else -> IssueStatus.Open
                }
                TimelineEvent(eventStatus, m.timestamp)
            }

            // If the current issue status is at or beyond a step not yet covered in mapped events, extend to match current status
            val currentStatusIndex = progression.indexOfFirst { it.first == status }
            val highestMappedIndex = mappedEvents.maxOfOrNull { event -> progression.indexOfFirst { it.first == event.status } } ?: -1
            if (currentStatusIndex > highestMappedIndex) {
                val steps = progression.take(currentStatusIndex + 1)
                return steps.map { (eventStatus, hours) ->
                    mappedEvents.firstOrNull { it.status == eventStatus }
                        ?: TimelineEvent(eventStatus, createdAt.plus(hours, ChronoUnit.HOURS))
                }
            }
            return mappedEvents
        }
    }

    val targetIndex = progression.indexOfFirst { it.first == status }.takeIf { it >= 0 } ?: 0
    val steps = progression.take(targetIndex + 1)
    return steps.map { (eventStatus, hours) ->
        TimelineEvent(eventStatus, createdAt.plus(hours, ChronoUnit.HOURS))
    }
}
