package com.svce.issuegram.data.datasource.remote

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueMilestone

interface IssueRemoteDataSource {
    suspend fun fetchIssues(): List<Issue>
    suspend fun fetchIssueById(ticketId: String): Issue?
    suspend fun postIssue(issue: Issue): Issue
    suspend fun toggleLike(ticketId: String)
    suspend fun deleteIssue(ticketId: String): Result<Unit>
    suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue>
    suspend fun updateStatus(
        ticketId: String,
        status: com.svce.issuegram.data.model.IssueStatus,
        milestones: List<IssueMilestone> = emptyList()
    ): Result<Unit>
}
