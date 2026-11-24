package com.svce.issuegram.data.datasource.local

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueMilestone
import com.svce.issuegram.data.model.IssueStatus
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IssueLocalDataSource {
    private val mutex = Mutex()
    private val _issuesFlow = MutableStateFlow<List<Issue>>(emptyList())
    val issuesFlow: StateFlow<List<Issue>> = _issuesFlow.asStateFlow()

    suspend fun updateIssues(newIssues: List<Issue>) = mutex.withLock {
        _issuesFlow.value = newIssues.distinctBy { it.ticketId.uppercase() }
    }

    suspend fun addIssue(issue: Issue) = mutex.withLock {
        val updated = listOf(issue) + _issuesFlow.value.filterNot {
            it.ticketId.equals(issue.ticketId, ignoreCase = true)
        }
        _issuesFlow.value = updated
    }

    suspend fun toggleLike(ticketId: String) = mutex.withLock {
        val updated = _issuesFlow.value.map {
            if (it.ticketId.equals(ticketId, ignoreCase = true) || it.ticketId.endsWith(ticketId, ignoreCase = true)) {
                val newLiked = !it.isLikedByMe
                val newLikes = if (newLiked) it.likes + 1 else (it.likes - 1).coerceAtLeast(0)
                it.copy(isLikedByMe = newLiked, likes = newLikes)
            } else {
                it
            }
        }
        _issuesFlow.value = updated
    }

    suspend fun deleteIssue(ticketId: String) = mutex.withLock {
        _issuesFlow.value = _issuesFlow.value.filter {
            !it.ticketId.equals(ticketId, ignoreCase = true) &&
                !it.ticketId.endsWith(ticketId, ignoreCase = true)
        }
    }

    /**
     * Updates an issue's status and milestones:
     * - Only the newly transitioned step is stamped with Instant.now().
     * - All previously completed steps keep their original timestamp.
     * - Future steps have isCompleted = false and timestamp = null.
     */
    suspend fun updateStatus(
        ticketId: String,
        status: IssueStatus
    ): Issue? = mutex.withLock {
        val now = Instant.now()
        var updatedIssueResult: Issue? = null

        _issuesFlow.value = _issuesFlow.value.map { issue ->
            if (issue.ticketId.equals(ticketId, ignoreCase = true) || issue.ticketId.endsWith(ticketId, ignoreCase = true)) {
                val targetStep = when (status) {
                    IssueStatus.Open -> 1
                    IssueStatus.Acknowledged -> 2
                    IssueStatus.Assigned -> 3
                    IssueStatus.InProgress -> 4
                    IssueStatus.Resolved -> 5
                    IssueStatus.Reopened -> 5
                }

                // If milestones are empty or incomplete, create standard 5-step milestone template
                val existingMilestones = if (issue.milestones.size >= 5) {
                    issue.milestones
                } else {
                    listOf(
                        IssueMilestone(1, 1, "Issue Reported", issue.authorName, issue.createdAt, true),
                        IssueMilestone(2, 2, "Acknowledged by Authority", "Civic Control", issue.createdAt, false),
                        IssueMilestone(3, 3, "Assigned to Department", "Public Works", issue.createdAt, false),
                        IssueMilestone(4, 4, "Work In Progress", "Field Operations", issue.createdAt, false),
                        IssueMilestone(5, 5, "Issue Resolved", "Civic Authority", issue.createdAt, false)
                    )
                }

                val updatedMilestones = existingMilestones.map { m ->
                    when {
                        // Step 1: Open always stays completed with original issue creation time
                        m.stepOrder == 1 -> {
                            m.copy(isCompleted = true, timestamp = issue.createdAt)
                        }
                        // Earlier completed steps: keep existing timestamp and completed state
                        m.stepOrder < targetStep -> {
                            val time = if (m.isCompleted) m.timestamp else now
                            m.copy(isCompleted = true, timestamp = time)
                        }
                        // Target step being activated: stamp with current time
                        m.stepOrder == targetStep -> {
                            m.copy(isCompleted = true, timestamp = now)
                        }
                        // Future steps: not completed, no timestamp
                        else -> {
                            m.copy(isCompleted = false)
                        }
                    }
                }

                val updated = issue.copy(status = status, milestones = updatedMilestones)
                updatedIssueResult = updated
                updated
            } else {
                issue
            }
        }
        updatedIssueResult
    }

    suspend fun updateIssue(updatedIssue: Issue) = mutex.withLock {
        _issuesFlow.value = _issuesFlow.value.map {
            if (it.ticketId.equals(updatedIssue.ticketId, ignoreCase = true) ||
                it.ticketId.endsWith(updatedIssue.ticketId, ignoreCase = true)
            ) {
                updatedIssue
            } else {
                it
            }
        }
    }

    suspend fun getIssue(ticketId: String): Issue? = mutex.withLock {
        _issuesFlow.value.find {
            it.ticketId.equals(ticketId, ignoreCase = true) || it.ticketId.endsWith(ticketId, ignoreCase = true)
        }
    }
}
