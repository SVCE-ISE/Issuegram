package com.svce.issuegram.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.svce.issuegram.data.datasource.local.IssueLocalDataSource
import com.svce.issuegram.data.datasource.remote.IssueRemoteDataSource
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.domain.repository.IssueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

class IssueRepositoryImpl(
    private val remoteDataSource: IssueRemoteDataSource,
    private val localDataSource: IssueLocalDataSource = IssueLocalDataSource(),
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : IssueRepository {

    private var nextTicketNumber = 2482

    init {
        // Initial fetch and cache synchronization
        repositoryScope.launch {
            try {
                val remote = remoteDataSource.fetchIssues()
                if (remote.isNotEmpty()) {
                    localDataSource.updateIssues(remote)
                }
            } catch (e: Exception) {
                // Ignore network errors on startup
            }
        }
    }

    override fun getFeedIssues(): Flow<List<Issue>> {
        return localDataSource.issuesFlow
    }

    override fun getIssueById(ticketId: String): Flow<Issue?> {
        return localDataSource.issuesFlow.map { list ->
            list.firstOrNull { it.ticketId.equals(ticketId, ignoreCase = true) }
                ?: list.firstOrNull { it.ticketId.endsWith(ticketId, ignoreCase = true) }
        }
    }

    override fun getUserIssues(userId: String, authorName: String): Flow<List<Issue>> {
        val currentFirebaseUser = com.google.firebase.Firebase.auth.currentUser
        val firebaseName = currentFirebaseUser?.displayName
        val emailPrefix = currentFirebaseUser?.email?.substringBefore('@')
        return localDataSource.issuesFlow.map { list ->
            list.filter { issue ->
                issue.isMine ||
                issue.authorName.equals(authorName, ignoreCase = true) ||
                (firebaseName != null && issue.authorName.equals(firebaseName, ignoreCase = true)) ||
                (emailPrefix != null && (
                    issue.authorUsername?.equals(emailPrefix, ignoreCase = true) == true ||
                    issue.authorName.equals(emailPrefix, ignoreCase = true)
                ))
            }
        }
    }

    override suspend fun createIssue(
        title: String,
        description: String?,
        category: IssueCategory,
        locationName: String,
        photoUri: String?,
        latitude: Double,
        longitude: Double,
        authorName: String,
        authorUsername: String?,
    ): Result<Issue> {
        return try {
            val draft = Issue(
                ticketId = "IG-${nextTicketNumber++}",
                title = title.trim(),
                description = description?.trim()?.ifBlank { null },
                category = category,
                status = IssueStatus.Open,
                area = locationName.trim(),
                locationName = locationName.trim(),
                authorName = authorName,
                authorUsername = authorUsername,
                createdAt = Instant.now(),
                likes = 0,
                photoUri = photoUri,
                isMine = true,
                latitude = latitude,
                longitude = longitude,
            )
            // Persist to Supabase remote & local cache
            val posted = remoteDataSource.postIssue(draft)
            val finalIssue = posted.copy(
                isMine = true,
                photoUri = posted.photoUri ?: draft.photoUri,
                authorName = authorName,
                authorUsername = authorUsername ?: posted.authorUsername,
            )
            localDataSource.addIssue(finalIssue)
            Result.success(finalIssue)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleLike(ticketId: String): Result<Unit> {
        return try {
            remoteDataSource.toggleLike(ticketId)
            localDataSource.toggleLike(ticketId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteIssue(ticketId: String): Result<Unit> {
        // Immediate optimistic local deletion
        localDataSource.deleteIssue(ticketId)
        return try {
            remoteDataSource.deleteIssue(ticketId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    override suspend fun editIssue(
        ticketId: String,
        title: String,
        description: String?,
        locationName: String?,
    ): Result<Issue> {
        return try {
            val result = remoteDataSource.editIssue(ticketId, title, description, locationName)
            val updated = result.getOrNull()
            if (updated != null) {
                localDataSource.updateIssue(updated)
                Result.success(updated)
            } else {
                val current = localDataSource.issuesFlow.first().firstOrNull {
                    it.ticketId.equals(ticketId, ignoreCase = true) || it.ticketId.endsWith(ticketId, ignoreCase = true)
                }
                if (current != null) {
                    val locallyUpdated = current.copy(
                        title = title.trim().ifBlank { current.title },
                        description = description?.trim()?.ifBlank { null } ?: current.description,
                        locationName = locationName?.trim()?.ifBlank { current.locationName } ?: current.locationName,
                    )
                    localDataSource.updateIssue(locallyUpdated)
                    Result.success(locallyUpdated)
                } else {
                    result
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun refreshIssues(): Result<Unit> {
        return try {
            val remote = remoteDataSource.fetchIssues()
            localDataSource.updateIssues(remote)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
