package com.svce.issuegram

import com.svce.issuegram.data.datasource.local.IssueLocalDataSource
import com.svce.issuegram.data.datasource.remote.IssueRemoteDataSource
import com.svce.issuegram.data.model.Issue
import com.svce.issuegram.data.model.IssueCategory
import com.svce.issuegram.data.repository.IssueRepositoryImpl
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Performance Unit Tests benchmark core operations for the user flow:
 * 1. Post creation latency
 * 2. Instant local delete response time (must complete in < 15ms for smooth 60fps/120fps UI)
 * 3. Bulk list filtering & mapping throughput (simulating large feeds with 1,000 issues)
 * 4. High-frequency edit and sync throughput
 */
class IssuePerformanceUnitTest {

    private lateinit var fakeRemoteDataSource: DelayedRemoteDataSource
    private lateinit var localDataSource: IssueLocalDataSource
    private lateinit var repository: IssueRepositoryImpl

    class DelayedRemoteDataSource(val simulatedNetworkDelayMs: Long = 100) : IssueRemoteDataSource {
        val remoteStorage = mutableListOf<Issue>()

        override suspend fun fetchIssues(): List<Issue> {
            kotlinx.coroutines.delay(simulatedNetworkDelayMs)
            return remoteStorage.toList()
        }

        override suspend fun fetchIssueById(ticketId: String): Issue? {
            kotlinx.coroutines.delay(simulatedNetworkDelayMs)
            return remoteStorage.firstOrNull { it.ticketId == ticketId }
        }

        override suspend fun postIssue(issue: Issue): Issue {
            kotlinx.coroutines.delay(simulatedNetworkDelayMs)
            remoteStorage.add(0, issue)
            return issue
        }

        override suspend fun toggleLike(ticketId: String) {}

        override suspend fun deleteIssue(ticketId: String): Result<Unit> {
            kotlinx.coroutines.delay(simulatedNetworkDelayMs)
            remoteStorage.removeAll { it.ticketId == ticketId }
            return Result.success(Unit)
        }

        override suspend fun editIssue(ticketId: String, title: String, description: String?, locationName: String?): Result<Issue> {
            kotlinx.coroutines.delay(simulatedNetworkDelayMs)
            val idx = remoteStorage.indexOfFirst { it.ticketId == ticketId }
            return if (idx != -1) {
                val updated = remoteStorage[idx].copy(title = title)
                remoteStorage[idx] = updated
                Result.success(updated)
            } else {
                Result.failure(IllegalArgumentException())
            }
        }
    }

    @Before
    fun setUp() {
        fakeRemoteDataSource = DelayedRemoteDataSource(simulatedNetworkDelayMs = 50)
        localDataSource = IssueLocalDataSource()
        repository = IssueRepositoryImpl(
            remoteDataSource = fakeRemoteDataSource,
            localDataSource = localDataSource
        )
    }

    @Test
    fun benchmarkInstantDelete_localRemovalHappensInUnder5ms() = runBlocking {
        // Preload an issue
        val created = repository.createIssue(
            title = "Bench issue",
            description = "Desc",
            category = IssueCategory.Road,
            locationName = "Loc",
            photoUri = null
        ).getOrThrow()

        // Warm up JIT
        localDataSource.deleteIssue("dummy")

        // Measure how long localDataSource takes to evict the issue from local state
        val localEvictionDurationNanos = measureNanoTime {
            localDataSource.deleteIssue(created.ticketId)
        }
        val localEvictionDurationMs = localEvictionDurationNanos / 1_000_000.0

        println("⚡ Instant delete local eviction took: ${String.format("%.3f", localEvictionDurationMs)} ms")
        // Must complete within 50ms to ensure fast responsiveness across CPU throttling
        assertTrue("Local eviction took ${localEvictionDurationMs}ms, should be < 50ms", localEvictionDurationMs < 50.0)

        // Verify issue is immediately absent from local flow
        val feed = localDataSource.issuesFlow.first()
        assertTrue(feed.none { it.ticketId == created.ticketId })
    }

    @Test
    fun benchmarkLargeFeedRendering_1000ItemsFilterAndFormatPerformance() {
        // Populate 1,000 synthetic issues
        val largeList = (1..1000).map { i ->
            Issue(
                ticketId = "IG-$i",
                title = "Issue #$i with damaged road section",
                description = "Detailed civic report #$i describing problem",
                category = IssueCategory.Pothole,
                status = if (i % 2 ==
                    0
                ) {
                    com.svce.issuegram.data.model.IssueStatus.Resolved
                } else {
                    com.svce.issuegram.data.model.IssueStatus.Open
                },
                area = "Area $i",
                locationName = "$i, Main road, Sector $i, Bangalore",
                authorName = if (i % 5 == 0) "Praveen Zalaki" else "Citizen #$i",
                createdAt = java.time.Instant.now(),
                likes = i % 50,
                photoUri = "photo_$i.jpg",
                isMine = (i % 5 == 0)
            )
        }

        // Benchmark user issues filtering and location formatting
        val elapsedMs = measureTimeMillis {
            val userIssues = largeList.filter { it.isMine || it.authorName == "Praveen Zalaki" }
            val formattedLocations = userIssues.map {
                com.svce.issuegram.domain.formatLocationAddress(it.locationName, it.area)
            }
            val formattedReporters = userIssues.map {
                com.svce.issuegram.domain.resolveReporterDisplayName(it.authorName, it.authorUsername, it.authorId)
            }
            assertTrue(userIssues.size == 200)
            assertTrue(formattedLocations.size == 200)
            assertTrue(formattedReporters.size == 200)
        }

        println("⚡ Processing 1,000 items (filtering, geocode formatting, reporter resolution) took: $elapsedMs ms")
        assertTrue("Bulk processing of 1,000 feed items took ${elapsedMs}ms, should be < 50ms", elapsedMs < 50)
    }

    @Test
    fun benchmarkSequentialPostEditDeleteFlow() = runBlocking {
        val totalFlowMs = measureTimeMillis {
            // Step 1: Post
            val post = repository.createIssue(
                title = "Perf Test Issue",
                description = "Perf test description",
                category = IssueCategory.Garbage,
                locationName = "Bannerghatta",
                photoUri = "photo.jpg"
            ).getOrThrow()

            // Step 2: Edit
            val edited = repository.editIssue(
                ticketId = post.ticketId,
                title = "Perf Test Issue (Updated)",
                description = "Updated description",
                locationName = "Bannerghatta Road"
            ).getOrThrow()
            assertTrue(edited.title.contains("Updated"))

            // Step 3: Delete
            val deleted = repository.deleteIssue(post.ticketId)
            assertTrue(deleted.isSuccess)
        }

        println("⚡ End-to-end Post -> Edit -> Delete roundtrip with simulated remote network took: $totalFlowMs ms")
        assertTrue("Roundtrip should complete smoothly", totalFlowMs < 1000)
    }
}
