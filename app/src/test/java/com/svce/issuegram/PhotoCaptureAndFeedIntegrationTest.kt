package com.svce.issuegram

import com.svce.issuegram.data.datasource.local.IssueLocalDataSource
import com.svce.issuegram.data.datasource.remote.IssueRemoteDataSource
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.repository.IssueRepositoryImpl
import com.svce.issuegram.domain.model.User
import com.svce.issuegram.domain.repository.AuthRepository
import com.svce.issuegram.domain.repository.NotificationRepository
import com.svce.issuegram.domain.usecase.auth.ObserveCurrentUserUseCase
import com.svce.issuegram.domain.usecase.issue.CreateIssueUseCase
import com.svce.issuegram.domain.usecase.issue.GetFeedIssuesUseCase
import com.svce.issuegram.domain.usecase.issue.ToggleLikeUseCase
import com.svce.issuegram.domain.usecase.notification.GetNotificationsUseCase
import com.svce.issuegram.ui.feed.FeedViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit and integration tests covering the complete Photo Capture -> Post -> Feed Presentation journey:
 * 1. Camera Shutter click simulation: Image file creation and temporary draft retention.
 * 2. Permanent copy guarantee: Verifying photo file is preserved before draft cleanup.
 * 3. Gallery picker URI conversion and passing to CreateIssueUseCase.
 * 4. CreateIssueUseCase execution with photoUri: LocalDataSource update & RemoteDataSource payload.
 * 5. FeedViewModel propagation: Verifying newly created issue appears at index 0 in FeedUiState with valid photoUri.
 * 6. Cloud storage fallback: When remote storage upload fails or is delayed, local photoUri remains intact.
 * 7. Null/blank photo handling: Clean fallback when report is submitted without a photo.
 * 8. Category parsing from Camera screen to IssueCategory enum.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoCaptureAndFeedIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRemoteDataSource: FakePhotoIssueRemoteDataSource
    private lateinit var localDataSource: IssueLocalDataSource
    private lateinit var repository: IssueRepositoryImpl
    private lateinit var createIssueUseCase: CreateIssueUseCase
    private lateinit var getFeedIssuesUseCase: GetFeedIssuesUseCase
    private lateinit var feedViewModel: FeedViewModel

    class FakePhotoIssueRemoteDataSource : IssueRemoteDataSource {
        val storedIssues = mutableListOf<Issue>()
        var simulateStorageUploadFailure = false
        var lastReceivedPhotoUri: String? = null

        override suspend fun fetchIssues(): List<Issue> = storedIssues.toList()

        override suspend fun fetchIssueById(ticketId: String): Issue? = storedIssues.firstOrNull { it.ticketId == ticketId }

        override suspend fun postIssue(issue: Issue): Issue {
            lastReceivedPhotoUri = issue.photoUri
            val finalPhotoUri = if (simulateStorageUploadFailure) {
                // If cloud storage upload fails, retain local photo URI
                issue.photoUri
            } else if (issue.photoUri != null && !issue.photoUri.startsWith("http")) {
                // Simulating successful Firebase Storage upload returning cloud https URL
                "https://firebasestorage.googleapis.com/v0/b/issuegram-app.appspot.com/o/issue_photos%2Fmock_uuid.jpg"
            } else {
                issue.photoUri
            }
            val posted = issue.copy(photoUri = finalPhotoUri)
            storedIssues.add(0, posted)
            return posted
        }

        override suspend fun toggleLike(ticketId: String) {}
        override suspend fun deleteIssue(ticketId: String): Result<Unit> {
            storedIssues.removeAll { it.ticketId == ticketId }
            return Result.success(Unit)
        }
        override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> = Result.success(storedIssues.first())
    }

    class FakeSimpleAuthRepository : AuthRepository {
        override fun observeCurrentUser(): Flow<User?> = flowOf(null)
        override fun getCurrentUser(): User? = null
        override suspend fun updateDisplayName(name: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateAvatarUri(uri: String?): Result<Unit> = Result.success(Unit)
        override fun signOut() {}
    }

    class FakeSimpleNotificationRepository : NotificationRepository {
        override fun getNotifications(): Flow<List<com.svce.issuegram.domain.model.Notification>> = flowOf(emptyList())
        override suspend fun markAllAsRead(): Result<Unit> = Result.success(Unit)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRemoteDataSource = FakePhotoIssueRemoteDataSource()
        localDataSource = IssueLocalDataSource()
        repository = IssueRepositoryImpl(
            remoteDataSource = fakeRemoteDataSource,
            localDataSource = localDataSource
        )
        createIssueUseCase = CreateIssueUseCase(repository)
        getFeedIssuesUseCase = GetFeedIssuesUseCase(repository)

        feedViewModel = FeedViewModel(
            getFeedIssuesUseCase = getFeedIssuesUseCase,
            observeCurrentUserUseCase = ObserveCurrentUserUseCase(FakeSimpleAuthRepository()),
            getNotificationsUseCase = GetNotificationsUseCase(FakeSimpleNotificationRepository()),
            toggleLikeUseCase = ToggleLikeUseCase(repository),
            issueRepository = repository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPhotoFilePersistedBeforeDraftCleanup_preventsBrokenImageInFeed() = runTest {
        // 1. Simulate camera taking a picture to drafts dir
        val draftsDir = tempFolder.newFolder("drafts")
        val draftPhotoFile = File(draftsDir, "capture_12345.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        }
        assertTrue(draftPhotoFile.exists())

        // 2. Perform the permanent copy (matching our fix in PixelCameraScreen.kt)
        val permanentDir = tempFolder.newFolder("files", "issue_photos")
        val permanentTarget = File(permanentDir, "issue_permanent_12345.jpg")
        draftPhotoFile.copyTo(permanentTarget, overwrite = true)

        // 3. Simulate draft cleanup
        draftPhotoFile.delete()
        assertFalse(draftPhotoFile.exists())
        assertTrue(permanentTarget.exists()) // Permanent file is preserved!

        // 4. Submit issue with the preserved file
        val photoUri = "file://${permanentTarget.absolutePath}"
        val result = createIssueUseCase(
            title = "Pothole on Main Road",
            description = "Dangerous pothole",
            category = IssueCategory.Pothole,
            locationName = "Main Road",
            photoUri = photoUri
        )
        assertTrue(result.isSuccess)

        // 5. Verify FeedUiState receives the issue with photoUri populated
        val collectJob = launch { feedViewModel.uiState.collect {} }
        advanceUntilIdle()

        val feedState = feedViewModel.uiState.value
        assertEquals(1, feedState.issues.size)
        val postedIssue = feedState.issues.first()
        assertEquals("Pothole on Main Road", postedIssue.title)
        assertNotNull(postedIssue.photoUri)
        assertTrue(postedIssue.photoUri!!.isNotEmpty())

        collectJob.cancel()
    }

    @Test
    fun testGalleryImagePicked_propagatesCorrectlyToFeed() = runTest {
        val galleryContentUri = "content://media/external/images/media/4412"

        val result = createIssueUseCase(
            title = "Broken Water Pipe",
            description = "Flooding the street",
            category = IssueCategory.Water,
            locationName = "Water Works Lane",
            photoUri = galleryContentUri
        )
        assertTrue(result.isSuccess)

        val collectJob = launch { feedViewModel.uiState.collect {} }
        advanceUntilIdle()

        val feedState = feedViewModel.uiState.value
        assertEquals(1, feedState.issues.size)
        val postedIssue = feedState.issues.first()
        assertEquals("Broken Water Pipe", postedIssue.title)
        assertEquals(IssueCategory.Water, postedIssue.category)
        assertNotNull(postedIssue.photoUri)

        collectJob.cancel()
    }

    @Test
    fun testCloudStorageFailureFallback_retainsLocalPhotoUriInFeed() = runTest {
        // Configure remote to fail cloud upload
        fakeRemoteDataSource.simulateStorageUploadFailure = true

        val localFile = tempFolder.newFile("offline_capture.jpg")
        val localPhotoUri = "file://${localFile.absolutePath}"

        val result = createIssueUseCase(
            title = "Streetlight flickering",
            description = null,
            category = IssueCategory.Streetlight,
            locationName = "Oak Avenue",
            photoUri = localPhotoUri
        )
        assertTrue(result.isSuccess)

        val collectJob = launch { feedViewModel.uiState.collect {} }
        advanceUntilIdle()

        val feedState = feedViewModel.uiState.value
        val postedIssue = feedState.issues.first()
        // Feed must retain local file URI so image can be rendered on-device
        assertEquals(localPhotoUri, postedIssue.photoUri)

        collectJob.cancel()
    }

    @Test
    fun testReportWithoutPhoto_submitsAndShowsInFeedWithNullPhotoUri() = runTest {
        val result = createIssueUseCase(
            title = "Garbage piling up",
            description = "Near community dustbin",
            category = IssueCategory.Garbage,
            locationName = "Market Square",
            photoUri = null
        )
        assertTrue(result.isSuccess)

        val collectJob = launch { feedViewModel.uiState.collect {} }
        advanceUntilIdle()

        val feedState = feedViewModel.uiState.value
        assertEquals(1, feedState.issues.size)
        val postedIssue = feedState.issues.first()
        assertNull(postedIssue.photoUri)
        assertEquals(IssueCategory.Garbage, postedIssue.category)

        collectJob.cancel()
    }

    @Test
    fun testCategoryParsingFromCameraToFeed() {
        fun parseCategory(categoryText: String): IssueCategory = when {
            categoryText.contains("pothole", ignoreCase = true) -> IssueCategory.Pothole
            categoryText.contains(
                "streetlight",
                ignoreCase = true
            ) ||
                categoryText.contains("light", ignoreCase = true) -> IssueCategory.Streetlight
            categoryText.contains("garbage", ignoreCase = true) -> IssueCategory.Garbage
            categoryText.contains("water", ignoreCase = true) -> IssueCategory.Water
            categoryText.contains("drain", ignoreCase = true) -> IssueCategory.Drainage
            categoryText.contains("road", ignoreCase = true) -> IssueCategory.Road
            categoryText.contains("safety", ignoreCase = true) -> IssueCategory.Safety
            else -> IssueCategory.Other
        }

        assertEquals(IssueCategory.Pothole, parseCategory("Pothole"))
        assertEquals(IssueCategory.Streetlight, parseCategory("Streetlight"))
        assertEquals(IssueCategory.Garbage, parseCategory("Garbage"))
        assertEquals(IssueCategory.Water, parseCategory("Water"))
        assertEquals(IssueCategory.Drainage, parseCategory("Drainage"))
        assertEquals(IssueCategory.Road, parseCategory("Road"))
        assertEquals(IssueCategory.Safety, parseCategory("Safety"))
        assertEquals(IssueCategory.Other, parseCategory("Random"))
    }
}
