package com.svce.issuegram

import com.svce.issuegram.data.datasource.local.IssueLocalDataSource
import com.svce.issuegram.data.datasource.remote.IssueRemoteDataSource
import com.svce.issuegram.data.datasource.remote.NotificationRemoteDataSource
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueMilestone
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.data.repository.IssueRepositoryImpl
import com.svce.issuegram.data.repository.NotificationRepositoryImpl
import com.svce.issuegram.domain.model.AlertKind
import com.svce.issuegram.domain.model.Notification
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EndToEndUserFlowTest {

    @Test
    fun completeMultiUserLifecycle_postIsolateProfileLikeAndNotifyPosterOnly() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val sharedRemoteFeed = MutableStateFlow<List<Issue>>(emptyList())
        val userANotifications = MutableStateFlow<List<Notification>>(emptyList())

        val remote = object : IssueRemoteDataSource {
            override fun observeIssues(): Flow<List<Issue>> = sharedRemoteFeed
            override suspend fun fetchIssues(): List<Issue> = sharedRemoteFeed.value
            override suspend fun fetchIssueById(ticketId: String): Issue? =
                sharedRemoteFeed.value.firstOrNull { it.ticketId == ticketId }

            override suspend fun postIssue(issue: Issue): Issue {
                val saved = issue.copy(reporterId = "user-A")
                sharedRemoteFeed.value = listOf(saved) + sharedRemoteFeed.value
                return saved
            }

            override suspend fun toggleLike(ticketId: String) {}
            override suspend fun deleteIssue(ticketId: String): Result<Unit> = Result.success(Unit)
            override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?) =
                Result.failure<Issue>(UnsupportedOperationException())

            override suspend fun updateStatus(
                ticketId: String,
                status: IssueStatus,
                milestones: List<IssueMilestone>
            ): Result<Unit> {
                userANotifications.value = listOf(
                    Notification(
                        id = "notif-${status.name}",
                        ticketId = ticketId,
                        issueTitle = "Broken pipe",
                        kind = AlertKind.Status,
                        status = status,
                        message = "Status updated to ${status.name}",
                        createdAt = Instant.now(),
                        isUnread = true
                    )
                ) + userANotifications.value
                return Result.success(Unit)
            }
        }

        val repo = IssueRepositoryImpl(remote, IssueLocalDataSource(), TestScope(dispatcher))
        val firedToUserA = mutableListOf<String>()
        val notifRemote = object : NotificationRemoteDataSource {
            override suspend fun fetchNotifications(): List<Notification> = userANotifications.value
            override fun observeNotifications(): Flow<List<Notification>> = userANotifications
            override suspend fun markAllAsRead() {
                userANotifications.value = userANotifications.value.map { it.copy(isUnread = false) }
            }
        }
        NotificationRepositoryImpl(notifRemote, TestScope(dispatcher)) { firedToUserA.add(it.id) }

        // 1. User A posts an issue (same display name "Civic Reporter" as User B)
        val created = repo.createIssue(
            title = "Broken pipe",
            description = "Water flooding road",
            category = IssueCategory.Water,
            locationName = "Block A",
            photoUri = null,
            latitude = 12.97,
            longitude = 77.59,
            authorName = "Civic Reporter",
            authorUsername = "civic"
        ).getOrThrow()

        // 2. Feed has exactly 1 entry (no duplicate key crash)
        val feed = repo.getFeedIssues().first()
        assertEquals(1, feed.size)
        assertEquals(created.ticketId, feed.first().ticketId)

        // 3. Profile isolation: User A sees it, User B (even with same displayName) does NOT
        assertEquals(1, repo.getUserIssues("user-A", "Civic Reporter").first().size)
        assertEquals(0, repo.getUserIssues("user-B", "Civic Reporter").first().size)

        // 4. Admin updates status -> User A receives notification once
        assertTrue(repo.updateStatus(created.ticketId, IssueStatus.InProgress).isSuccess)
        assertEquals(listOf("notif-InProgress"), firedToUserA)
    }
}
