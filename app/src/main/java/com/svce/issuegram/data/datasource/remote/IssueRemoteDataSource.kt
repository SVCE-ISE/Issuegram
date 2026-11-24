package com.svce.issuegram.data.datasource.remote

import com.svce.issuegram.data.model.Issue

interface IssueRemoteDataSource {
    suspend fun fetchIssues(): List<Issue>
    suspend fun fetchIssueById(ticketId: String): Issue?
    suspend fun postIssue(issue: Issue): Issue
    suspend fun toggleLike(ticketId: String)
    suspend fun deleteIssue(ticketId: String): Result<Unit>
    suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue>
}
