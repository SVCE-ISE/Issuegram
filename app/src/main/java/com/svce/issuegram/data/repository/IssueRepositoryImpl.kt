package com.svce.issuegram.data.repository

import com.google.firebase.auth.auth
import com.svce.issuegram.data.datasource.local.IssueLocalDataSource
import com.svce.issuegram.data.datasource.remote.IssueRemoteDataSource
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.domain.repository.IssueRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class IssueRepositoryImpl(
    private val remoteDataSource: IssueRemoteDataSource,
    private val localDataSource: IssueLocalDataSource = IssueLocalDataSource(),
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : IssueRepository {

    init {
        // Real-time feed synchronization across all users + fallback initial fetch
        repositoryScope.launch {
            var receivedRealtime = false
            try {
                remoteDataSource.observeIssues().collect { remote ->
                    receivedRealtime = true
                    localDataSource.updateIssues(remote)
                }
            } catch (_: Exception) {
                // Ignore listener errors
            }
            if (!receivedRealtime) {
                try {
                    val remote = remoteDataSource.fetchIssues()
                    if (remote.isNotEmpty()) {
                        localDataSource.updateIssues(remote)
                    }
                } catch (_: Exception) {
                    // Ignore network errors on startup
                }
            }
        }
    }

    override fun getFeedIssues(): Flow<List<Issue>> = localDataSource.issuesFlow

    override fun getIssueById(ticketId: String): Flow<Issue?> = localDataSource.issuesFlow.map { list ->
        list.firstOrNull { it.ticketId.equals(ticketId, ignoreCase = true) }
            ?: list.firstOrNull { it.ticketId.endsWith(ticketId, ignoreCase = true) }
    }

    override fun getUserIssues(userId: String, authorName: String): Flow<List<Issue>> {
        return localDataSource.issuesFlow.map { list ->
            list.filter { issue ->
                if (issue.reporterId.isNotBlank() && userId.isNotBlank()) {
                    issue.reporterId == userId
                } else {
                    issue.isMine
                }
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
        authorUsername: String?
    ): Result<Issue> = try {
        val draft = Issue(
            ticketId = "IG-${UUID.randomUUID().toString().take(6).uppercase()}",
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
            longitude = longitude
        )
        // Persist to Supabase remote & local cache
        val posted = remoteDataSource.postIssue(draft)
        val finalIssue = posted.copy(
            isMine = true,
            photoUri = posted.photoUri ?: draft.photoUri,
            authorName = authorName,
            authorUsername = authorUsername ?: posted.authorUsername
        )
        localDataSource.addIssue(finalIssue)
        Result.success(finalIssue)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun toggleLike(ticketId: String): Result<Unit> {
        // Immediate optimistic local flip for instant, zero-lag UI reaction
        localDataSource.toggleLike(ticketId)
        return try {
            remoteDataSource.toggleLike(ticketId)
            Result.success(Unit)
        } catch (e: Exception) {
            // Revert on remote failure
            localDataSource.toggleLike(ticketId)
            Result.failure(e)
        }
    }

    override suspend fun deleteIssue(ticketId: String): Result<Unit> {
        localDataSource.deleteIssue(ticketId)
        return try {
            remoteDataSource.deleteIssue(ticketId)
            Result.success(Unit)
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> = try {
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
                    locationName = locationName?.trim()?.ifBlank { current.locationName } ?: current.locationName
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

    override suspend fun updateStatus(ticketId: String, status: com.svce.issuegram.data.model.IssueStatus): Result<Unit> {
        val updatedIssue = localDataSource.updateStatus(ticketId, status)
        val milestones = updatedIssue?.milestones ?: emptyList()
        return remoteDataSource.updateStatus(ticketId, status, milestones)
    }

    override suspend fun refreshIssues(): Result<Unit> = try {
        val remote = remoteDataSource.fetchIssues()
        localDataSource.updateIssues(remote)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
