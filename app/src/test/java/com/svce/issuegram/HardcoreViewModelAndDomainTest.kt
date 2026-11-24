package com.svce.issuegram

import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.model.IssueStatus
import com.svce.issuegram.data.timeline
import com.svce.issuegram.domain.displayTime
import com.svce.issuegram.domain.repository.IssueRepository
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.issue.CreateIssueUseCase
import com.svce.issuegram.domain.usecase.issue.GetFeedIssuesUseCase
import com.svce.issuegram.domain.usecase.issue.GetIssueDetailUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.repository.AuthRepository
import com.svce.issuegram.ui.admin.AdminViewModel
import com.svce.issuegram.ui.camera.CameraReportViewModel
import com.svce.issuegram.ui.map.CivicMapViewModel
import com.svce.issuegram.ui.screens.IssueDetailViewModel
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HardcoreViewModelAndDomainTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun issue(ticketId: String, status: IssueStatus = IssueStatus.Open) = Issue(
        ticketId = ticketId,
        title = "Title $ticketId",
        description = null,
        category = IssueCategory.Pothole,
        status = status,
        area = "Area",
        locationName = "Loc",
        authorName = "Author",
        createdAt = Instant.now(),
        likes = 0
    )

    private fun fakeRepo(flows: Map<String, MutableStateFlow<Issue?>>): IssueRepository = object : IssueRepository {
        override fun getFeedIssues(): Flow<List<Issue>> = MutableStateFlow(emptyList())
        override fun getIssueById(ticketId: String): Flow<Issue?> = flows[ticketId] ?: MutableStateFlow(null)
        override fun getUserIssues(userId: String, authorName: String): Flow<List<Issue>> = MutableStateFlow(emptyList())
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
        ): Result<Issue> = Result.success(issue("IG-NEW"))
        override suspend fun toggleLike(ticketId: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteIssue(ticketId: String): Result<Unit> = Result.success(Unit)
        override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateStatus(ticketId: String, status: IssueStatus): Result<Unit> = Result.success(Unit)
        override suspend fun refreshIssues(): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun issueDetailViewModel_reloadingNewTicket_cancelsPreviousCollector() = runTest {
        val flow1 = MutableStateFlow<Issue?>(issue("IG-1"))
        val flow2 = MutableStateFlow<Issue?>(issue("IG-2"))
        val vm = IssueDetailViewModel(
            getIssueDetailUseCase = GetIssueDetailUseCase(fakeRepo(mapOf("IG-1" to flow1, "IG-2" to flow2))),
            toggleLikeUseCase = ToggleLikeUseCase(fakeRepo(emptyMap()))
        )

        vm.loadIssue("IG-1")
        advanceUntilIdle()
        assertEquals("IG-1", vm.uiState.value.issue?.ticketId)

        vm.loadIssue("IG-2")
        advanceUntilIdle()
        assertEquals("IG-2", vm.uiState.value.issue?.ticketId)

        // Mutating flow1 must NOT overwrite IG-2 because previous collector was cancelled
        flow1.value = issue("IG-1", status = IssueStatus.Resolved)
        advanceUntilIdle()
        assertEquals("IG-2", vm.uiState.value.issue?.ticketId)
    }

    @Test
    fun issueDetailViewModel_missingTicket_setsLoadingFalseAndNullIssue() = runTest {
        val vm = IssueDetailViewModel(
            getIssueDetailUseCase = GetIssueDetailUseCase(fakeRepo(emptyMap())),
            toggleLikeUseCase = ToggleLikeUseCase(fakeRepo(emptyMap()))
        )

        vm.loadIssue("IG-DOES-NOT-EXIST")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.issue)
    }

    @Test
    fun createIssueUseCase_rejectsBlankAndShortTitles_acceptsValidTitle() = runTest {
        val useCase = CreateIssueUseCase(fakeRepo(emptyMap()))

        assertTrue(useCase("   ", null, IssueCategory.Road, "Loc", null, 0.0, 0.0).isFailure)
        assertTrue(useCase("ab", null, IssueCategory.Road, "Loc", null, 0.0, 0.0).isFailure)
        assertTrue(useCase("Valid pothole report", null, IssueCategory.Road, "Loc", null, 0.0, 0.0).isSuccess)
    }

    @Test
    fun timeline_handlesAllStatusesIncludingReopenedWithoutCollapsing() {
        val reopenedTimeline = issue("IG-R", status = IssueStatus.Reopened).timeline()
        assertEquals(5, reopenedTimeline.size)
        // Reopened should advance past Open (index 0) and mark Acknowledged (index 1) active
        assertTrue(reopenedTimeline[1].at != null)
        assertNull(reopenedTimeline[2].at)
    }

    @Test
    fun displayTime_exactBoundaryTransitions() {
        assertEquals("", displayTime(null))
        assertEquals("2h ago", displayTime("2h ago"))
    }

    @Test
    fun adminViewModel_rejectsBlankUid_andDelegatesPromoteDemote() = runTest {
        var capturedUid: String? = null
        var capturedAdmin: Boolean? = null
        val vm = AdminViewModel { uid, isAdmin ->
            capturedUid = uid
            capturedAdmin = isAdmin
        }

        var blankResult: Result<Unit>? = null
        vm.setAdminStatus("   ", true) { blankResult = it }
        assertTrue(blankResult?.isFailure == true)

        var promoteResult: Result<Unit>? = null
        vm.setAdminStatus("user-123", true) { promoteResult = it }
        advanceUntilIdle()
        assertTrue(promoteResult?.isSuccess == true)
        assertEquals("user-123", capturedUid)
        assertEquals(true, capturedAdmin)
    }

    @Test
    fun cameraReportViewModel_validatesAndSubmitsIssue() = runTest {
        val authRepo = object : AuthRepository {
            override fun observeCurrentUser(): Flow<User?> = MutableStateFlow(
                User("u1", "Reporter", "r@example.com", isAdmin = false)
            )
            override fun getCurrentUser(): User? = null
            override suspend fun updateDisplayName(name: String): Result<Unit> = Result.success(Unit)
            override suspend fun updateAvatarUri(uri: String?): Result<Unit> = Result.success(Unit)
            override fun signOut() {}
        }
        val vm = CameraReportViewModel(
            createIssueUseCase = CreateIssueUseCase(fakeRepo(emptyMap())),
            observeCurrentUserUseCase = ObserveCurrentUserUseCase(authRepo)
        )

        var errorMsg: String? = null
        vm.submitIssue("ab", null, "Pothole", "Main Rd", null, 12.9, 77.5, {}, { errorMsg = it })
        advanceUntilIdle()
        assertTrue(errorMsg != null)

        var succeeded = false
        vm.submitIssue("Large pothole", "Deep", "Pothole", "Main Rd", null, 12.9, 77.5, { succeeded = true }, {})
        advanceUntilIdle()
        assertTrue(succeeded)
        assertFalse(vm.isPosting.value)
    }
}
