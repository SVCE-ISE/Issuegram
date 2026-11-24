package com.svce.issuegram.data.datasource.local

import com.svce.issuegram.data.model.Issue
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
        _issuesFlow.value = newIssues
    }

    suspend fun addIssue(issue: Issue) = mutex.withLock {
        val updated = mutableListOf(issue).apply { addAll(_issuesFlow.value) }
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

    suspend fun updateStatus(ticketId: String, status: com.svce.issuegram.data.model.IssueStatus) = mutex.withLock {
        val now = Instant.now()
        _issuesFlow.value = _issuesFlow.value.map { issue ->
            if (issue.ticketId.equals(ticketId, ignoreCase = true) || issue.ticketId.endsWith(ticketId, ignoreCase = true)) {
                val stepOrderThreshold = when (status) {
                    com.svce.issuegram.data.model.IssueStatus.Open -> 1
                    com.svce.issuegram.data.model.IssueStatus.Acknowledged -> 2
                    com.svce.issuegram.data.model.IssueStatus.Assigned -> 3
                    com.svce.issuegram.data.model.IssueStatus.InProgress -> 4
                    com.svce.issuegram.data.model.IssueStatus.Resolved -> 5
                    com.svce.issuegram.data.model.IssueStatus.Reopened -> 5
                }
                val updatedMilestones = issue.milestones.map { m ->
                    val shouldComplete = m.stepOrder <= stepOrderThreshold
                    if (shouldComplete) {
                        // If newly completed or matching the selected status transition, update timestamp to now
                        val timestampToUse = if (!m.isCompleted || m.stepOrder == stepOrderThreshold) now else m.timestamp
                        m.copy(isCompleted = true, timestamp = timestampToUse)
                    } else {
                        m.copy(isCompleted = false)
                    }
                }
                issue.copy(status = status, milestones = updatedMilestones)
            } else {
                issue
            }
        }
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
