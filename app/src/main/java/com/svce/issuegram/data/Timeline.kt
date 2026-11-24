package com.svce.issuegram.data

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

data class TimelineEvent(val status: IssueStatus, val at: Instant)

/**
 * Derived timeline for mock data; Firestore will supply real transition events.
 * Offsets are hours after the report was created.
 */
private val progression = listOf(
    IssueStatus.Open to 0L,
    IssueStatus.Acknowledged to 8L,
    IssueStatus.Assigned to 28L,
    IssueStatus.InProgress to 34L,
    IssueStatus.Resolved to 120L,
    IssueStatus.Reopened to 144L
)

fun Issue.timeline(): List<TimelineEvent> {
    if (milestones.isNotEmpty()) {
        val completedMilestones = milestones.filter { it.isCompleted }
        val milestonesToUse = if (completedMilestones.isNotEmpty()) completedMilestones else listOf(milestones.first())
        val mappedEvents = milestonesToUse.map { m ->
            val eventStatus = when {
                m.title.contains("resolved", ignoreCase = true) -> IssueStatus.Resolved
                m.title.contains("progress", ignoreCase = true) || m.stepOrder >= 4 -> IssueStatus.InProgress
                m.title.contains("assigned", ignoreCase = true) || m.stepOrder == 3 -> IssueStatus.Assigned
                m.title.contains("acknowledged", ignoreCase = true) || m.stepOrder == 2 -> IssueStatus.Acknowledged
                else -> IssueStatus.Open
            }
            TimelineEvent(eventStatus, m.timestamp)
        }
        // If the current issue status is further advanced than the milestone titles indicate, ensure timeline reflects current status
        if (mappedEvents.none { it.status == status } && status != IssueStatus.Open) {
            val steps = if (status == IssueStatus.Reopened) {
                progression
            } else {
                progression.take((progression.indexOfFirst { it.first == status }.takeIf { it >= 0 } ?: 0) + 1)
            }
            return steps.map { (eventStatus, hours) ->
                TimelineEvent(eventStatus, createdAt.plus(hours, ChronoUnit.HOURS))
            }
        }
        return mappedEvents
    }
    val steps = if (status == IssueStatus.Reopened) {
        progression
    } else {
        progression.take((progression.indexOfFirst { it.first == status }.takeIf { it >= 0 } ?: 0) + 1)
    }
    return steps.map { (eventStatus, hours) ->
        TimelineEvent(eventStatus, createdAt.plus(hours, ChronoUnit.HOURS))
    }
}
