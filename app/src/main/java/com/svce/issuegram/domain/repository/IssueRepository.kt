package com.svce.issuegram.domain.repository

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import kotlinx.coroutines.flow.Flow

interface IssueRepository {
    fun getFeedIssues(): Flow<List<Issue>>
    fun getIssueById(ticketId: String): Flow<Issue?>
    fun getUserIssues(userId: String, authorName: String): Flow<List<Issue>>
    suspend fun createIssue(
        title: String,
        description: String?,
        category: IssueCategory,
        locationName: String,
        photoUri: String?,
        latitude: Double = 12.9716,
        longitude: Double = 77.5946,
        authorName: String = "Praveen Zalaki",
        authorUsername: String? = null,
    ): Result<Issue>
    suspend fun toggleLike(ticketId: String): Result<Unit>
    suspend fun deleteIssue(ticketId: String): Result<Unit>
    suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue>
    suspend fun refreshIssues(): Result<Unit>
}
