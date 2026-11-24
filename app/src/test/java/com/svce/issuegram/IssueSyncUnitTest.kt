package com.svce.issuegram

import com.svce.issuegram.data.datasource.local.IssueLocalDataSource
import com.svce.issuegram.data.datasource.remote.IssueRemoteDataSource
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.repository.IssueRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests to catch bugs and verify end-to-end synchronization between
 * Local DataSource (and UI cache) and Supabase Remote DataSource for:
 * 1. Post creation and sync
 * 2. Post editing and sync
 * 3. Post deletion (instant optimistic local removal + remote sync)
 * 4. Error tolerance (e.g. offline/network failure handling)
 */
class IssueSyncUnitTest {

    private lateinit var fakeRemoteDataSource: FakeIssueRemoteDataSource
    private lateinit var localDataSource: IssueLocalDataSource
    private lateinit var repository: IssueRepositoryImpl

    class FakeIssueRemoteDataSource : IssueRemoteDataSource {
        val remoteStorage = mutableListOf<Issue>()
        var shouldFailNetwork = false
        var callCountPost = 0
        var callCountEdit = 0
        var callCountDelete = 0

        override suspend fun fetchIssues(): List<Issue> {
            if (shouldFailNetwork) throw RuntimeException("Network unreachable")
            return remoteStorage.toList()
        }

        override suspend fun fetchIssueById(ticketId: String): Issue? {
            if (shouldFailNetwork) throw RuntimeException("Network unreachable")
            return remoteStorage.firstOrNull { it.ticketId == ticketId }
        }

        override suspend fun postIssue(issue: Issue): Issue {
            callCountPost++
            if (shouldFailNetwork) throw RuntimeException("Supabase POST error: 500")
            val remoteSynced = issue.copy(
                photoUri = issue.photoUri?.let { "https://supabase.co/storage/v1/object/public/issue-media/$it" }
                    ?: "https://supabase.co/storage/v1/object/public/issue-media/fallback.jpg"
            )
            remoteStorage.add(0, remoteSynced)
            return remoteSynced
        }

        override suspend fun toggleLike(ticketId: String) {
            val idx = remoteStorage.indexOfFirst { it.ticketId == ticketId }
            if (idx != -1) {
                remoteStorage[idx] = remoteStorage[idx].copy(likes = remoteStorage[idx].likes + 1)
            }
        }

        override suspend fun deleteIssue(ticketId: String): Result<Unit> {
            callCountDelete++
            if (shouldFailNetwork) return Result.failure(RuntimeException("Supabase DELETE error: 500"))
            val removed = remoteStorage.removeAll { it.ticketId == ticketId }
            return if (removed) Result.success(Unit) else Result.failure(IllegalArgumentException("Not found"))
        }

        override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> {
            callCountEdit++
            if (shouldFailNetwork) return Result.failure(RuntimeException("Supabase PATCH error: 500"))
            val idx = remoteStorage.indexOfFirst { it.ticketId == ticketId }
            return if (idx != -1) {
                val updated = remoteStorage[idx].copy(
                    title = title.trim().ifBlank { remoteStorage[idx].title },
                    description = description?.trim()?.ifBlank { null } ?: remoteStorage[idx].description,
                    locationName = locationName?.trim()?.ifBlank { remoteStorage[idx].locationName } ?: remoteStorage[idx].locationName
                )
                remoteStorage[idx] = updated
                Result.success(updated)
            } else {
                Result.failure(IllegalArgumentException("Issue not found in remote: $ticketId"))
            }
        }
    }

    @Before
    fun setUp() {
        fakeRemoteDataSource = FakeIssueRemoteDataSource()
        localDataSource = IssueLocalDataSource()
        repository = IssueRepositoryImpl(
            remoteDataSource = fakeRemoteDataSource,
            localDataSource = localDataSource
        )
    }

    @Test
    fun testPostIssue_createsLocallyAndSyncsToRemote() = runBlocking {
        val result = repository.createIssue(
            title = "Broken street light",
            description = "Flickering all night",
            category = IssueCategory.Streetlight,
            locationName = "MG Road, Bangalore",
            photoUri = "temp_capture.jpg",
            latitude = 12.9716,
            longitude = 77.5946,
            authorName = "Praveen Zalaki",
            authorUsername = "praveen_zalaki"
        )

        assertTrue("Issue creation should succeed", result.isSuccess)
        val created = result.getOrThrow()

        // 1. Verify remote sync
        assertEquals(1, fakeRemoteDataSource.callCountPost)
        assertEquals(1, fakeRemoteDataSource.remoteStorage.size)
        assertEquals(created.ticketId, fakeRemoteDataSource.remoteStorage[0].ticketId)
        assertTrue(created.photoUri!!.startsWith("https://supabase.co/"))

        // 2. Verify local cache flow updates immediately
        val feed = localDataSource.issuesFlow.first()
        assertTrue(feed.any { it.ticketId == created.ticketId })
        val localIssue = feed.first { it.ticketId == created.ticketId }
        assertEquals("Broken street light", localIssue.title)
        assertTrue(localIssue.isMine)
    }

    @Test
    fun testEditIssue_updatesRemoteAndLocalCache() = runBlocking {
        // First create an issue
        val postResult = repository.createIssue(
            title = "Pothole on 4th Main",
            description = "Near bakery",
            category = IssueCategory.Pothole,
            locationName = "4th Main Road",
            photoUri = "pothole.jpg",
            latitude = 12.9716,
            longitude = 77.5946
        )
        val original = postResult.getOrThrow()

        // Now edit the issue
        val editResult = repository.editIssue(
            ticketId = original.ticketId,
            title = "Deep dangerous pothole on 4th Main",
            description = "Near Bangalore Bakery, urgent repair needed",
            locationName = "4th Main Road, Jayanagar"
        )

        assertTrue("Edit should succeed", editResult.isSuccess)
        val edited = editResult.getOrThrow()
        assertEquals(1, fakeRemoteDataSource.callCountEdit)

        // 1. Verify remote was updated with new values
        val remoteItem = fakeRemoteDataSource.remoteStorage.first { it.ticketId == original.ticketId }
        assertEquals("Deep dangerous pothole on 4th Main", remoteItem.title)
        assertEquals("Near Bangalore Bakery, urgent repair needed", remoteItem.description)
        assertEquals("4th Main Road, Jayanagar", remoteItem.locationName)

        // 2. Verify local cache flow reflects edit
        val localItem = localDataSource.issuesFlow.first().first { it.ticketId == original.ticketId }
        assertEquals("Deep dangerous pothole on 4th Main", localItem.title)
        assertEquals("Near Bangalore Bakery, urgent repair needed", localItem.description)
        assertEquals("4th Main Road, Jayanagar", localItem.locationName)
    }

    @Test
    fun testDeleteIssue_optimisticLocalRemovalAndRemoteSync() = runBlocking {
        val postResult = repository.createIssue(
            title = "Water leakage",
            description = "Pipe burst",
            category = IssueCategory.Water,
            locationName = "Indiranagar",
            photoUri = "leak.jpg"
        )
        val issue = postResult.getOrThrow()
        assertEquals(1, localDataSource.issuesFlow.first().size)
        assertEquals(1, fakeRemoteDataSource.remoteStorage.size)

        // Execute delete
        val deleteResult = repository.deleteIssue(issue.ticketId)
        assertTrue(deleteResult.isSuccess)
        assertEquals(1, fakeRemoteDataSource.callCountDelete)

        // 1. Local state must be immediately empty
        val localFeed = localDataSource.issuesFlow.first()
        assertFalse("Local feed should no longer contain deleted issue", localFeed.any { it.ticketId == issue.ticketId })

        // 2. Remote storage must be empty
        assertFalse(
            "Remote database should no longer contain deleted issue",
            fakeRemoteDataSource.remoteStorage.any {
                it.ticketId ==
                    issue.ticketId
            }
        )
    }

    @Test
    fun testDeleteIssue_whenRemoteFails_localRemovalPersistsForInstantUx() = runBlocking {
        val postResult = repository.createIssue(
            title = "Fallen tree branch",
            description = "Blocking pedestrian path",
            category = IssueCategory.Other,
            locationName = "Koramangala",
            photoUri = "branch.jpg"
        )
        val issue = postResult.getOrThrow()

        // Simulate network / remote failure during delete
        fakeRemoteDataSource.shouldFailNetwork = true

        val deleteResult = repository.deleteIssue(issue.ticketId)
        // Repository absorbs remote delete failure gracefully so the user UI never gets stuck
        assertTrue(deleteResult.isSuccess)

        // Local cache must still be wiped optimistically
        val localFeed = localDataSource.issuesFlow.first()
        assertFalse(
            "Locally the issue must still be removed immediately even if remote is offline",
            localFeed.any {
                it.ticketId ==
                    issue.ticketId
            }
        )
    }

    @Test
    fun testDuplicateLocationSanitization() {
        val duplicateInput = "84a, chandramouleshwara layout, Hunasamaranahalli, Karnataka 562157, India"
        val formatted = com.svce.issuegram.domain.formatLocationAddress(
            locationName = duplicateInput,
            area = duplicateInput
        )

        assertNotNull(formatted.primary)
        // Primary and secondary must never be duplicates
        assertNotEquals(formatted.primary, formatted.secondary)
        if (formatted.secondary != null) {
            assertFalse(
                "Secondary should not duplicate primary address: ${formatted.secondary}",
                formatted.primary.equals(formatted.secondary, ignoreCase = true)
            )
        }
    }

    @Test
    fun testReporterDisplayName_resolvesProperNameOverEmailHandle() {
        val resolved = com.svce.issuegram.domain.resolveReporterDisplayName(
            authorName = "praveenzalaki200",
            authorUsername = "praveenzalaki200",
            authorId = "user_123",
            currentFirebaseUser = null
        )

        assertEquals("Praveen Zalaki", resolved)
    }
}
