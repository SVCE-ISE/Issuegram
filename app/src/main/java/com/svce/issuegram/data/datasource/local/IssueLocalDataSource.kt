package com.svce.issuegram.data.datasource.local

import com.svce.issuegram.data.model.Issue
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
        _issuesFlow.value = _issuesFlow.value.map {
            if (it.ticketId.equals(ticketId, ignoreCase = true) || it.ticketId.endsWith(ticketId, ignoreCase = true)) {
                val updatedMilestones = it.milestones.map { m ->
                    val stepMatches = when (status) {
                        com.svce.issuegram.data.model.IssueStatus.Open -> m.stepOrder <= 1
                        com.svce.issuegram.data.model.IssueStatus.Acknowledged -> m.stepOrder <= 2
                        com.svce.issuegram.data.model.IssueStatus.Assigned -> m.stepOrder <= 2
                        com.svce.issuegram.data.model.IssueStatus.InProgress -> m.stepOrder <= 3
                        com.svce.issuegram.data.model.IssueStatus.Resolved -> m.stepOrder <= 4
                        com.svce.issuegram.data.model.IssueStatus.Reopened -> true
                    }
                    m.copy(isCompleted = stepMatches)
                }
                it.copy(status = status, milestones = updatedMilestones)
            } else {
                it
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
}
