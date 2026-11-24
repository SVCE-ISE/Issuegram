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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HardcoreRepositoryAndNotificationTest {

    private class FakeIssueRemoteDataSource(
        var initialIssues: List<Issue> = emptyList(),
        var shouldFailFetch: Boolean = false,
        var shouldFailToggleLike: Boolean = false,
        var shouldFailUpdateStatus: Boolean = false
    ) : IssueRemoteDataSource {
        override suspend fun fetchIssues(): List<Issue> {
            if (shouldFailFetch) throw RuntimeException("Network error")
            return initialIssues
        }

        override suspend fun fetchIssueById(ticketId: String): Issue? =
            initialIssues.firstOrNull { it.ticketId.equals(ticketId, ignoreCase = true) }

        override suspend fun postIssue(issue: Issue): Issue = issue

        override suspend fun toggleLike(ticketId: String) {
            if (shouldFailToggleLike) throw RuntimeException("Like failed")
        }

        override suspend fun deleteIssue(ticketId: String): Result<Unit> = Result.success(Unit)

        override suspend fun editIssue(
            ticketId: String,
            title: String,
            description: String?,
            locationName: String?
        ): Result<Issue> = Result.failure(UnsupportedOperationException())

        override suspend fun updateStatus(
            ticketId: String,
            status: IssueStatus,
            milestones: List<IssueMilestone>
        ): Result<Unit> = if (shouldFailUpdateStatus) {
            Result.failure(RuntimeException("Status update failed"))
        } else {
            Result.success(Unit)
        }
    }

    private fun sampleIssue(ticketId: String = "IG-1001", likes: Int = 0, isLiked: Boolean = false) = Issue(
        ticketId = ticketId,
        title = "Broken streetlight",
        description = "Dark corner near gate",
        category = IssueCategory.Streetlight,
        status = IssueStatus.Open,
        area = "Campus",
        locationName = "Main Gate",
        authorName = "Alice",
        createdAt = Instant.now(),
        likes = likes,
        isLikedByMe = isLiked
    )

    @Test
    fun updateStatus_propagatesRemoteFailure_andUpdatesLocalCache() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val local = IssueLocalDataSource().apply { updateIssues(listOf(sampleIssue("IG-500"))) }
        val remote = FakeIssueRemoteDataSource(shouldFailUpdateStatus = true)
        val repo = IssueRepositoryImpl(remote, local, TestScope(dispatcher))

        val result = repo.updateStatus("IG-500", IssueStatus.InProgress)

        assertTrue("Expected remote failure to propagate", result.isFailure)
        val updated = repo.getIssueById("IG-500").first()
        assertEquals(IssueStatus.InProgress, updated?.status)
    }

    @Test
    fun notificationRepository_doesNotFireOnInitialLoad_firesOnlyForNewUnread_andDeduplicates() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val flow = MutableStateFlow<List<Notification>>(
            listOf(
                Notification("n1", "IG-1", "Pothole", AlertKind.Status, IssueStatus.Open, "Reported", Instant.now(), isUnread = true)
            )
        )
        val remote = object : NotificationRemoteDataSource {
            override suspend fun fetchNotifications(): List<Notification> = flow.value
            override fun observeNotifications(): Flow<List<Notification>> = flow
            override suspend fun markAllAsRead() {
                flow.value = flow.value.map { it.copy(isUnread = false) }
            }
        }
        val fired = mutableListOf<String>()
        val repo = NotificationRepositoryImpl(remote, TestScope(dispatcher)) { fired.add(it.id) }

        // Initial load should NOT fire native notifications
        assertEquals(emptyList<String>(), fired)

        // New unread notification arrives -> should fire once
        val n2 = Notification("n2", "IG-1", "Pothole", AlertKind.Status, IssueStatus.InProgress, "Work started", Instant.now(), isUnread = true)
        val n3Read = Notification("n3", "IG-1", "Pothole", AlertKind.Status, IssueStatus.Resolved, "Done", Instant.now(), isUnread = false)
        flow.value = listOf(n2, n3Read, flow.value.first())
        assertEquals(listOf("n2"), fired)

        // Re-emitting n2 should NOT fire again (deduplicated)
        flow.value = listOf(n2, n3Read)
        assertEquals(listOf("n2"), fired)
        assertEquals(2, repo.getNotifications().first().size)
    }

    @Test
    fun notificationRepository_markAllAsRead_clearsUnreadFlag() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val flow = MutableStateFlow(
            listOf(
                Notification("n1", "IG-1", "Water leak", AlertKind.Status, IssueStatus.Open, "Reported", Instant.now(), isUnread = true)
            )
        )
        val remote = object : NotificationRemoteDataSource {
            override suspend fun fetchNotifications(): List<Notification> = flow.value
            override fun observeNotifications(): Flow<List<Notification>> = flow
            override suspend fun markAllAsRead() {
                flow.value = flow.value.map { it.copy(isUnread = false) }
            }
        }
        val repo = NotificationRepositoryImpl(remote, TestScope(dispatcher)) {}

        repo.markAllAsRead()
        assertTrue(repo.getNotifications().first().none { it.isUnread })
    }

    @Test
    fun createIssue_100Issues_generates100UniqueTicketIds() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = IssueRepositoryImpl(FakeIssueRemoteDataSource(), IssueLocalDataSource(), TestScope(dispatcher))

        val ids = (1..100).map { i ->
            repo.createIssue(
                title = "Issue #$i",
                description = null,
                category = IssueCategory.Road,
                locationName = "Block $i",
                photoUri = null,
                latitude = 12.97,
                longitude = 77.59,
                authorName = "Tester",
                authorUsername = "tester"
            ).getOrThrow().ticketId
        }.toSet()

        assertEquals(100, ids.size)
    }

    @Test
    fun toggleLike_rollsBackOptimisticUpdate_whenRemoteThrows() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val local = IssueLocalDataSource().apply { updateIssues(listOf(sampleIssue("IG-777", likes = 5, isLiked = false))) }
        val remote = FakeIssueRemoteDataSource(shouldFailToggleLike = true)
        val repo = IssueRepositoryImpl(remote, local, TestScope(dispatcher))

        val result = repo.toggleLike("IG-777")
        assertTrue(result.isFailure)

        val after = repo.getIssueById("IG-777").first()!!
        assertEquals(5, after.likes)
        assertFalse(after.isLikedByMe)
    }

    @Test
    fun refreshIssues_preservesLocalCache_whenRemoteThrows() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val local = IssueLocalDataSource().apply { updateIssues(listOf(sampleIssue("IG-888"))) }
        val remote = FakeIssueRemoteDataSource(shouldFailFetch = true)
        val repo = IssueRepositoryImpl(remote, local, TestScope(dispatcher))

        val result = repo.refreshIssues()
        assertTrue(result.isFailure)
        assertEquals(1, repo.getFeedIssues().first().size)
    }

    @Test
    fun concurrentRepositoryMutations_maintainConsistentState() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = IssueRepositoryImpl(FakeIssueRemoteDataSource(), IssueLocalDataSource(), TestScope(dispatcher))

        val created = (1..25).map { idx ->
            async {
                repo.createIssue(
                    title = "Concurrent issue $idx",
                    description = null,
                    category = IssueCategory.Garbage,
                    locationName = "Zone $idx",
                    photoUri = null,
                    latitude = 12.9,
                    longitude = 77.6,
                    authorName = "User",
                    authorUsername = "user"
                ).getOrThrow()
            }
        }.awaitAll()

        assertEquals(25, created.map { it.ticketId }.toSet().size)
        assertEquals(25, repo.getFeedIssues().first().size)
    }

    @Test
    fun observeIssues_realTimeUpdatesFromOtherUsers_propagateAutomatically() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val liveFeed = MutableStateFlow<List<Issue>>(emptyList())
        val remote = object : IssueRemoteDataSource {
            override fun observeIssues(): Flow<List<Issue>> = liveFeed
            override suspend fun fetchIssues(): List<Issue> = liveFeed.value
            override suspend fun fetchIssueById(ticketId: String): Issue? = null
            override suspend fun postIssue(issue: Issue): Issue = issue
            override suspend fun toggleLike(ticketId: String) {}
            override suspend fun deleteIssue(ticketId: String): Result<Unit> = Result.success(Unit)
            override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?) =
                Result.failure<Issue>(UnsupportedOperationException())
            override suspend fun updateStatus(ticketId: String, status: IssueStatus, milestones: List<IssueMilestone>) =
                Result.success(Unit)
        }
        val repo = IssueRepositoryImpl(remote, IssueLocalDataSource(), TestScope(dispatcher))
        assertEquals(0, repo.getFeedIssues().first().size)

        // Another user posts an issue remotely -> should appear in feed immediately
        liveFeed.value = listOf(sampleIssue("IG-REMOTE-1"))
        assertEquals(listOf("IG-REMOTE-1"), repo.getFeedIssues().first().map { it.ticketId })
    }

    @Test
    fun localDataSource_deduplicatesAddAndUpdateByTicketId() = runTest {
        val local = IssueLocalDataSource()
        val issue = sampleIssue("IG-DUP-1")
        local.updateIssues(listOf(issue, issue.copy(ticketId = "ig-dup-1")))
        assertEquals(1, local.issuesFlow.first().size)

        local.addIssue(issue)
        assertEquals(1, local.issuesFlow.first().size)
    }
}
