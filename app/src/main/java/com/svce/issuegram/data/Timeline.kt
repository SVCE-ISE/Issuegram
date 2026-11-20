package com.svce.issuegram.data

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

data class TimelineEvent(
    val status: IssueStatus,
    val at: Instant,
)

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
    IssueStatus.Reopened to 144L,
)

fun Issue.timeline(): List<TimelineEvent> {
    val steps = if (status == IssueStatus.Reopened) {
        progression
    } else {
        progression.take(progression.indexOfFirst { it.first == status } + 1)
    }
    return steps.map { (eventStatus, hours) ->
        TimelineEvent(eventStatus, createdAt.plus(hours, ChronoUnit.HOURS))
    }
}
