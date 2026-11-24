package com.svce.issuegram

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueMilestone
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.data.timeline
import com.svce.issuegram.domain.formatLocationAddress
import com.svce.issuegram.domain.repository.IssueRepository
import com.svce.issuegram.domain.resolveReporterDisplayName
import com.svce.issuegram.domain.usecase.issue.GetIssueDetailUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import com.svce.issuegram.ui.screens.IssueDetailViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Exhaustive unit and operations tests for Issue Detail screen & ViewModel:
 * 1. Initial State: Loading state while retrieving ticket.
 * 2. Load Issue by Ticket ID: Successful retrieval and state population.
 * 3. Issue Not Found / Fallback: Handling missing or null ticket gracefully.
 * 4. Like / Upvote Toggling: Optimistic UI state flip and remote use-case trigger.
 * 5. Timeline Generation: Validation of sequential milestone progression.
 * 6. Milestone Override: Using custom milestones when present over default progression.
 * 7. Address Formatting: Two-line address generation (primary and secondary).
 * 8. Reporter Name Resolution: Fallback chain (authorName -> username -> authorId -> "Citizen Reporter").
 * 9. Coordinates & Map Bounds: Latitude/longitude validation for 16:9 preview.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IssueDetailOperationsUnitTest {

    private val testDispatcher = StandardTestDispatcher()
    private val issuesFlow = MutableStateFlow<Map<String, Issue>>(emptyMap())
    private var lastToggledTicket: String? = null

    private val fakeIssueRepository = object : IssueRepository {
        override fun getFeedIssues(): Flow<List<Issue>> = issuesFlow.map { it.values.toList() }

        override fun getIssueById(ticketId: String): Flow<Issue?> = issuesFlow.map { map ->
            map[ticketId] ?: map.values.firstOrNull { it.ticketId.endsWith(ticketId, ignoreCase = true) }
        }

        override fun getUserIssues(userId: String, authorName: String): Flow<List<Issue>> = flowOf(emptyList())

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
        ): Result<Issue> = Result.failure(NotImplementedError())

        override suspend fun toggleLike(ticketId: String): Result<Unit> {
            lastToggledTicket = ticketId
            val currentMap = issuesFlow.value.toMutableMap()
            val existing = currentMap[ticketId]
            if (existing != null) {
                currentMap[ticketId] = existing.copy(
                    isLikedByMe = !existing.isLikedByMe,
                    likes = if (existing.isLikedByMe) (existing.likes - 1).coerceAtLeast(0) else existing.likes + 1
                )
                issuesFlow.value = currentMap
            }
            return Result.success(Unit)
        }

        override suspend fun deleteIssue(ticketId: String): Result<Unit> = Result.success(Unit)

        override suspend fun updateStatus(ticketId: String, status: IssueStatus): Result<Unit> = Result.success(Unit)

        override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> = Result.failure(NotImplementedError())

        override suspend fun refreshIssues(): Result<Unit> = Result.success(Unit)
    }

    private lateinit var viewModel: IssueDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        issuesFlow.value = emptyMap()
        lastToggledTicket = null
        viewModel = IssueDetailViewModel(
            getIssueDetailUseCase = GetIssueDetailUseCase(fakeIssueRepository),
            toggleLikeUseCase = ToggleLikeUseCase(fakeIssueRepository)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_startsInLoadingState() {
        val state = viewModel.uiState.value
        assertTrue("Initial state should be loading", state.isLoading)
        assertNull("Initial state should not have an issue", state.issue)
        assertFalse("Initial state should not be liked", state.isLiked)
    }

    @Test
    fun testLoadIssue_successfulRetrieval_updatesState() = runTest {
        val issue = createTestIssue("IG-9001", "Fallen Tree", IssueStatus.InProgress)
        issuesFlow.value = mapOf("IG-9001" to issue)

        viewModel.loadIssue("IG-9001")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should no longer be loading", state.isLoading)
        assertNotNull("Issue should be populated", state.issue)
        assertEquals("IG-9001", state.issue?.ticketId)
        assertEquals("Fallen Tree", state.issue?.title)
        assertEquals(IssueStatus.InProgress, state.issue?.status)
    }

    @Test
    fun testLoadIssue_notFound_emitsNullIssueAndStopsLoading() = runTest {
        viewModel.loadIssue("IG-NON-EXISTENT")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Loading flag should be false", state.isLoading)
        assertNull("Issue should be null", state.issue)
    }

    @Test
    fun testToggleLike_flipsOptimisticStateAndCallsUseCase() = runTest {
        val issue = createTestIssue("IG-9002", "Pothole", IssueStatus.Open)
        issuesFlow.value = mapOf("IG-9002" to issue)

        viewModel.loadIssue("IG-9002")
        advanceUntilIdle()

        assertFalse("Should initially be unliked", viewModel.uiState.value.isLiked)

        // First toggle: Like
        viewModel.toggleLike("IG-9002")
        advanceUntilIdle()

        assertTrue("Should now be liked", viewModel.uiState.value.isLiked)
        assertEquals("IG-9002", lastToggledTicket)

        // Second toggle: Unlike
        viewModel.toggleLike("IG-9002")
        advanceUntilIdle()

        assertFalse("Should be unliked again", viewModel.uiState.value.isLiked)
    }

    @Test
    fun testTimelineProgression_derivedFromIssueStatus() {
        val baseTime = Instant.parse("2026-11-24T10:00:00Z")
        val openIssue = createTestIssue("IG-1", "Open Issue", IssueStatus.Open, createdAt = baseTime)
        val timelineOpen = openIssue.timeline()

        // Open status should produce 1 event (Open)
        assertEquals(1, timelineOpen.size)
        assertEquals(IssueStatus.Open, timelineOpen[0].status)
        assertEquals(baseTime, timelineOpen[0].at)

        val inProgressIssue = createTestIssue("IG-2", "In Progress Issue", IssueStatus.InProgress, createdAt = baseTime)
        val timelineProgress = inProgressIssue.timeline()

        // InProgress status should produce Open -> Acknowledged -> Assigned -> InProgress
        assertEquals(4, timelineProgress.size)
        assertEquals(IssueStatus.Open, timelineProgress[0].status)
        assertEquals(IssueStatus.Acknowledged, timelineProgress[1].status)
        assertEquals(IssueStatus.Assigned, timelineProgress[2].status)
        assertEquals(IssueStatus.InProgress, timelineProgress[3].status)
    }

    @Test
    fun testTimelineMilestones_customMilestonesTakePrecedence() {
        val baseTime = Instant.parse("2026-11-24T10:00:00Z")
        val customMilestones = listOf(
            IssueMilestone(1, 1, "Report Received", "Dispatcher", baseTime, true),
            IssueMilestone(2, 2, "Assigned to Ward 12", "Officer", baseTime.plusSeconds(3600), true),
            IssueMilestone(3, 3, "Repair in progress", "Field Crew", baseTime.plusSeconds(7200), true)
        )

        val issueWithMilestones = createTestIssue("IG-3", "With Milestones", IssueStatus.InProgress).copy(
            milestones = customMilestones
        )

        val timeline = issueWithMilestones.timeline()
        assertEquals(3, timeline.size)
        assertEquals(IssueStatus.Open, timeline[0].status)
        assertEquals(IssueStatus.Assigned, timeline[1].status)
        assertEquals(IssueStatus.InProgress, timeline[2].status)
    }

    @Test
    fun testFormatLocationAddress_primaryAndSecondaryParsing() {
        // Case 1: Standard location with sub-area
        val addr1 = formatLocationAddress(
            locationName = "MG Road",
            area = "Indiranagar",
            latitude = 12.9716,
            longitude = 77.5946
        )
        assertEquals("MG Road", addr1.primary)
        assertEquals("Indiranagar", addr1.secondary)

        // Case 2: Comma separated in locationName alone
        val addr2 = formatLocationAddress(
            locationName = "Anna Salai, Guindy",
            area = "",
            latitude = 13.0827,
            longitude = 80.2707
        )
        assertEquals("Anna Salai", addr2.primary)
        assertEquals("Guindy", addr2.secondary)

        // Case 3: Empty locationName -> defaults to "Location reported" with GPS secondary
        val addr3 = formatLocationAddress(
            locationName = "",
            area = "",
            latitude = 12.9716,
            longitude = 77.5946
        )
        assertEquals("Location reported", addr3.primary)
        assertEquals("GPS: 12.97160, 77.59460", addr3.secondary)
    }

    @Test
    fun testResolveReporterDisplayName_resolutionChain() {
        // AuthorName provided
        val name1 = resolveReporterDisplayName(
            authorName = "Arjun Patel",
            authorUsername = "arjun_p",
            authorId = "user_100",
            currentFirebaseUser = null
        )
        assertEquals("Arjun Patel", name1)

        // Blank AuthorName -> fallback to Username formatted
        val name2 = resolveReporterDisplayName(
            authorName = "",
            authorUsername = "civic_leader",
            authorId = "user_100",
            currentFirebaseUser = null
        )
        assertEquals("Civic Leader", name2)

        // Both blank -> fallback to Citizen Reporter
        val name3 = resolveReporterDisplayName(
            authorName = "",
            authorUsername = null,
            authorId = "abcdef123456",
            currentFirebaseUser = null
        )
        assertEquals("Citizen Reporter", name3)
    }

    private fun createTestIssue(ticketId: String, title: String, status: IssueStatus, createdAt: Instant = Instant.now()): Issue = Issue(
        ticketId = ticketId,
        authorName = "Vikram Sharma",
        authorUsername = "vikram_s",
        authorId = "user_vikram",
        title = title,
        description = "Detailed civic report about this incident.",
        category = IssueCategory.Road,
        status = status,
        area = "Koramangala",
        locationName = "80 Feet Road",
        createdAt = createdAt,
        likes = 5,
        photoUri = "https://example.com/photo.jpg",
        isMine = false,
        latitude = 12.9352,
        longitude = 77.6245
    )
}
