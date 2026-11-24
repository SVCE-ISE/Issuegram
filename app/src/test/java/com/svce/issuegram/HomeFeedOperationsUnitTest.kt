package com.svce.issuegram

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.domain.model.AlertKind
import com.svce.issuegram.domain.model.Notification
import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.repository.AuthRepository
import com.svce.issuegram.domain.repository.IssueRepository
import com.svce.issuegram.domain.repository.NotificationRepository
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.issue.GetFeedIssuesUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import com.svce.issuegram.domain.usecase.notification.GetNotificationsUseCase
import com.svce.issuegram.ui.feed.FeedViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive unit tests covering Home Feed operations and functions:
 * 1. Initial State & Flow subscription
 * 2. Community Feed Population & Real-time Updates
 * 3. Pull-to-refresh execution & Error resilience
 * 4. Like / Upvote toggling and state synchronization
 * 5. Unread notification badge synchronization with Top App Bar
 * 6. User profile / avatar data propagation
 * 7. Empty State transitions
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedOperationsUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeIssueRepository: FakeHomeFeedIssueRepository
    private lateinit var fakeAuthRepository: FakeHomeFeedAuthRepository
    private lateinit var fakeNotificationRepository: FakeHomeFeedNotificationRepository

    private lateinit var getFeedIssuesUseCase: GetFeedIssuesUseCase
    private lateinit var observeCurrentUserUseCase: ObserveCurrentUserUseCase
    private lateinit var getNotificationsUseCase: GetNotificationsUseCase
    private lateinit var toggleLikeUseCase: ToggleLikeUseCase

    private lateinit var viewModel: FeedViewModel

    class FakeHomeFeedIssueRepository : IssueRepository {
        val issuesFlowInternal = MutableStateFlow<List<Issue>>(emptyList())
        var refreshCalled = false
        var refreshShouldFail = false
        var toggleLikeCount = 0
        var lastToggledTicketId: String? = null

        override fun getFeedIssues(): Flow<List<Issue>> = issuesFlowInternal

        override fun getIssueById(ticketId: String): Flow<Issue?> = flowOf(issuesFlowInternal.value.find { it.ticketId == ticketId })

        override fun getUserIssues(userId: String, authorName: String): Flow<List<Issue>> = flowOf(
            issuesFlowInternal.value.filter {
                it.isMine
            }
        )

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
        ): Result<Issue> {
            val newIssue = Issue(
                ticketId = "IG-${(1000..9999).random()}",
                title = title,
                description = description,
                category = category,
                status = IssueStatus.Open,
                area = locationName,
                locationName = locationName,
                authorName = authorName,
                createdAt = Instant.now(),
                likes = 0,
                photoUri = photoUri,
                isMine = true,
                latitude = latitude,
                longitude = longitude,
                authorUsername = authorUsername
            )
            issuesFlowInternal.value = listOf(newIssue) + issuesFlowInternal.value
            return Result.success(newIssue)
        }

        override suspend fun toggleLike(ticketId: String): Result<Unit> {
            toggleLikeCount++
            lastToggledTicketId = ticketId
            val currentList = issuesFlowInternal.value.toMutableList()
            val index = currentList.indexOfFirst { it.ticketId == ticketId }
            if (index != -1) {
                val old = currentList[index]
                currentList[index] = old.copy(likes = old.likes + 1)
                issuesFlowInternal.value = currentList
            }
            return Result.success(Unit)
        }

        override suspend fun deleteIssue(ticketId: String): Result<Unit> {
            issuesFlowInternal.value = issuesFlowInternal.value.filterNot { it.ticketId == ticketId }
            return Result.success(Unit)
        }

        override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> {
            val currentList = issuesFlowInternal.value.toMutableList()
            val index = currentList.indexOfFirst { it.ticketId == ticketId }
            if (index != -1) {
                val updated = currentList[index].copy(title = title)
                currentList[index] = updated
                issuesFlowInternal.value = currentList
                return Result.success(updated)
            }
            return Result.failure(IllegalArgumentException())
        }

        override suspend fun refreshIssues(): Result<Unit> {
            refreshCalled = true
            return if (refreshShouldFail) {
                Result.failure(RuntimeException("Remote fetch error"))
            } else {
                Result.success(Unit)
            }
        }
    }

    class FakeHomeFeedAuthRepository : AuthRepository {
        val userFlow = MutableStateFlow<User?>(null)
        override fun observeCurrentUser(): Flow<User?> = userFlow
        override fun getCurrentUser(): User? = userFlow.value
        override suspend fun updateDisplayName(name: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateAvatarUri(uri: String?): Result<Unit> = Result.success(Unit)
        override fun signOut() {}
    }

    class FakeHomeFeedNotificationRepository : NotificationRepository {
        val notifsFlow = MutableStateFlow<List<Notification>>(emptyList())
        override fun getNotifications(): Flow<List<Notification>> = notifsFlow
        override suspend fun markAllAsRead(): Result<Unit> {
            notifsFlow.value = notifsFlow.value.map { it.copy(isUnread = false) }
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeIssueRepository = FakeHomeFeedIssueRepository()
        fakeAuthRepository = FakeHomeFeedAuthRepository()
        fakeNotificationRepository = FakeHomeFeedNotificationRepository()

        getFeedIssuesUseCase = GetFeedIssuesUseCase(fakeIssueRepository)
        observeCurrentUserUseCase = ObserveCurrentUserUseCase(fakeAuthRepository)
        getNotificationsUseCase = GetNotificationsUseCase(fakeNotificationRepository)
        toggleLikeUseCase = ToggleLikeUseCase(fakeIssueRepository)

        viewModel = FeedViewModel(
            getFeedIssuesUseCase = getFeedIssuesUseCase,
            observeCurrentUserUseCase = observeCurrentUserUseCase,
            getNotificationsUseCase = getNotificationsUseCase,
            toggleLikeUseCase = toggleLikeUseCase,
            issueRepository = fakeIssueRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState_emitsEmptyListAndNotLoadingOnceSubscribed() = runTest {
        // Collect in a background coroutine so WhileSubscribed activates
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(0, state.issues.size)
        assertFalse(state.isRefreshing)
        collectJob.cancel()
    }

    @Test
    fun testFeedPopulation_whenRepositoryEmitsIssues_uiStateUpdatesInstantly() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val issue1 = createMockIssue("IG-101", "Pothole on Main St", "Roads & Transit")
        val issue2 = createMockIssue("IG-102", "Streetlight broken", "Power & Lighting")

        fakeIssueRepository.issuesFlowInternal.value = listOf(issue1, issue2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.issues.size)
        assertEquals("IG-101", state.issues[0].ticketId)
        assertEquals("Pothole on Main St", state.issues[0].title)
        assertEquals("IG-102", state.issues[1].ticketId)
        collectJob.cancel()
    }

    @Test
    fun testPullToRefresh_triggersRepositoryRefreshAndCompletes() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.refresh()
        advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertTrue(fakeIssueRepository.refreshCalled)

        advanceTimeBy(600)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
        collectJob.cancel()
    }

    @Test
    fun testPullToRefresh_whenNetworkFails_recoversGracefullyWithoutCrashing() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        fakeIssueRepository.refreshShouldFail = true

        viewModel.refresh()
        advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.isRefreshing)

        advanceTimeBy(600)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertTrue(fakeIssueRepository.refreshCalled)
        collectJob.cancel()
    }

    @Test
    fun testToggleLike_incrementsLikeCountAndNotifiesRepository() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val issue = createMockIssue("IG-201", "Water leakage", "Water")
        fakeIssueRepository.issuesFlowInternal.value = listOf(issue)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.issues.first().likes)

        viewModel.toggleLike("IG-201")
        advanceUntilIdle()

        assertEquals(1, fakeIssueRepository.toggleLikeCount)
        assertEquals("IG-201", fakeIssueRepository.lastToggledTicketId)
        assertEquals(1, viewModel.uiState.value.issues.first().likes)
        collectJob.cancel()
    }

    @Test
    fun testTopBarUnreadNotificationBadge_updatesWhenNotificationsArrive() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnreadNotifications)

        // Emit an unread notification
        val unreadNotification = Notification(
            id = "notif-1",
            ticketId = "IG-101",
            issueTitle = "Issue Title",
            kind = AlertKind.Status,
            status = IssueStatus.InProgress,
            message = "Issue is in progress",
            createdAt = Instant.now(),
            isUnread = true
        )
        fakeNotificationRepository.notifsFlow.value = listOf(unreadNotification)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasUnreadNotifications)

        // Mark as read
        fakeNotificationRepository.markAllAsRead()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnreadNotifications)
        collectJob.cancel()
    }

    @Test
    fun testCurrentUserProfileState_propagatesToHomeFeedTopBar() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentUser)

        val testUser = User(
            id = "user_42",
            displayName = "Alex Citizen",
            email = "alex@issuegram.org",
            avatarUrl = "https://issuegram.org/avatar.jpg"
        )
        fakeAuthRepository.userFlow.value = testUser
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.currentUser)
        assertEquals("Alex Citizen", state.currentUser?.displayName)
        assertEquals("https://issuegram.org/avatar.jpg", state.currentUser?.avatarUrl)
        collectJob.cancel()
    }

    @Test
    fun testEmptyFeedState_whenIssuesCleared_emitsEmptyListForEmptyStateIllustration() = runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val issue = createMockIssue("IG-300", "Garbage overflow", "Garbage")
        fakeIssueRepository.issuesFlowInternal.value = listOf(issue)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.issues.size)

        // Clear issues (simulating all issues deleted)
        fakeIssueRepository.issuesFlowInternal.value = emptyList()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.issues.isEmpty())
        collectJob.cancel()
    }

    private fun createMockIssue(ticketId: String, title: String, categoryName: String): Issue = Issue(
        ticketId = ticketId,
        title = title,
        description = "Detailed report for $title",
        category = IssueCategory.Other,
        status = IssueStatus.Open,
        area = "Civic Center",
        locationName = "123 Main Road",
        authorName = "Reporter One",
        createdAt = Instant.now(),
        likes = 0,
        photoUri = null,
        isMine = false
    )
}
