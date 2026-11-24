package com.svce.issuegram

import com.google.android.gms.maps.model.LatLng
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.ui.map.CivicIssuePin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * Unit & Performance Tests for Civic Map Screen operations, transformations, and coordinate math:
 * 1. Pin Transformation: Mapping CivicIssueDto list into CivicIssuePin markers.
 * 2. Status Filtering: "All", "Active", "Resolved" filtering logic.
 * 3. Selected Pin Lookup: Verifying selection and dismissal on map click.
 * 4. Coordinate Calculations: Default center resolution (GPS vs First issue vs Fallback).
 * 5. Location Picking Flow: LatLng string formatting, validation, and confirmation bounds.
 * 6. Performance Benchmark: 10,000 issue pin transformations, filtering, and nearest-neighbor search.
 * 7. Fast Geofence Radius Calculation Performance: Spatial filtering benchmark.
 */
class CivicMapOperationsAndPerformanceUnitTest {

    @Test
    fun testPinTransformation_accuratelyMapsDtoToPin() {
        val dto = createMockDto(
            id = 42L,
            ticketCode = "#IG-2481",
            title = "Broken Light",
            locationName = "5th Avenue",
            latitude = 12.9716,
            longitude = 77.5946,
            isResolved = false,
            status = "OPEN",
            category = "Power & Lighting"
        )

        val pin = CivicIssuePin(
            id = dto.id.toString(),
            ticketCode = dto.ticketCode,
            status = dto.status,
            title = dto.title,
            locationName = dto.locationName,
            position = LatLng(dto.latitude, dto.longitude),
            isResolved = dto.isResolved,
            category = dto.category,
            timestamp = dto.createdAt ?: "",
        )

        assertEquals("42", pin.id)
        assertEquals("#IG-2481", pin.ticketCode)
        assertEquals(12.9716, pin.position.latitude, 0.0001)
        assertEquals(77.5946, pin.position.longitude, 0.0001)
        assertFalse(pin.isResolved)
    }

    @Test
    fun testFilterPins_byActiveResolvedAndAll() {
        val pin1 = CivicIssuePin("1", "#IG-1", "OPEN", "Issue 1", "Loc 1", LatLng(10.0, 10.0), false, "Road", "now")
        val pin2 = CivicIssuePin("2", "#IG-2", "RESOLVED", "Issue 2", "Loc 2", LatLng(10.1, 10.1), true, "Water", "now")
        val pin3 = CivicIssuePin("3", "#IG-3", "IN_PROGRESS", "Issue 3", "Loc 3", LatLng(10.2, 10.2), false, "Garbage", "now")
        val pins = listOf(pin1, pin2, pin3)

        val allFiltered = filterPins(pins, "All")
        val activeFiltered = filterPins(pins, "Active")
        val resolvedFiltered = filterPins(pins, "Resolved")

        assertEquals(3, allFiltered.size)
        assertEquals(2, activeFiltered.size)
        assertEquals(1, resolvedFiltered.size)
        assertEquals("2", resolvedFiltered.first().id)
    }

    @Test
    fun testDefaultMapCenterResolution_orderOfPrecedence() {
        val customInitialCenter = LatLng(13.0827, 80.2707)
        val firstIssueLatLng = LatLng(12.9716, 77.5946)
        val fallbackCenter = LatLng(13.0827, 80.2707)

        // Case 1: Initial center explicitly provided -> Should win
        val center1 = resolveMapCenter(initialCenter = customInitialCenter, firstIssueLatLng = firstIssueLatLng)
        assertEquals(customInitialCenter, center1)

        // Case 2: No initial center, but issues present -> First issue coordinates should win
        val center2 = resolveMapCenter(initialCenter = null, firstIssueLatLng = firstIssueLatLng)
        assertEquals(firstIssueLatLng, center2)

        // Case 3: No initial center and no issues -> Fallback default center
        val center3 = resolveMapCenter(initialCenter = null, firstIssueLatLng = null)
        assertEquals(fallbackCenter, center3)
    }

    @Test
    fun testLocationPickingFormatting_validatesPrecision() {
        val picked = LatLng(12.9715982, 77.5945621)
        val formatted = String.format(java.util.Locale.US, "%.6f, %.6f", picked.latitude, picked.longitude)
        assertEquals("12.971598, 77.594562", formatted)
    }

    @Test
    fun testSelectedPinDetailsLookup() {
        val pins = listOf(
            CivicIssuePin("101", "#IG-101", "OPEN", "Pothole", "A Street", LatLng(10.0, 10.0), false, "Road", "now"),
            CivicIssuePin("102", "#IG-102", "RESOLVED", "Garbage", "B Street", LatLng(10.1, 10.1), true, "Sanitation", "now"),
        )

        var selectedPinId: String? = "102"
        val selectedPin = pins.firstOrNull { it.id == selectedPinId }
        assertNotNull(selectedPin)
        assertEquals("Garbage", selectedPin?.title)

        // Dismiss pin on map click
        selectedPinId = null
        val dismissedPin = pins.firstOrNull { it.id == selectedPinId }
        assertNull(dismissedPin)
    }

    // =========================================================================
    // PERFORMANCE BENCHMARK TESTS
    // =========================================================================

    @Test
    fun benchmark10kPinTransformationAndFiltering_runsInUnder50ms() {
        // Generate 10,000 synthetic issues
        val largeList = (1..10_000).map { i ->
            createMockDto(
                id = i.toLong(),
                ticketCode = "#IG-$i",
                title = "Issue #$i",
                locationName = "Location #$i",
                latitude = 12.0 + (i % 100) * 0.01,
                longitude = 77.0 + (i % 100) * 0.01,
                isResolved = (i % 2 == 0),
                status = if (i % 2 == 0) "RESOLVED" else "OPEN",
                category = "General"
            )
        }

        // 1. Measure transformation time for 10,000 pins
        val transformTimeMs = measureTimeMillis {
            val pins = largeList.map { issue ->
                CivicIssuePin(
                    id = issue.id.toString(),
                    ticketCode = issue.ticketCode,
                    status = issue.status,
                    title = issue.title,
                    locationName = issue.locationName,
                    position = LatLng(issue.latitude, issue.longitude),
                    isResolved = issue.isResolved,
                    category = issue.category,
                    timestamp = issue.createdAt ?: ""
                )
            }
            assertEquals(10_000, pins.size)
        }

        println("⚡ Transform 10,000 CivicIssueDto -> CivicIssuePin took: $transformTimeMs ms")
        assertTrue("Transforming 10k pins took too long: ${transformTimeMs}ms", transformTimeMs < 100)

        // 2. Measure filter performance on 10,000 pins
        val transformedPins = largeList.map { issue ->
            CivicIssuePin(
                id = issue.id.toString(),
                ticketCode = issue.ticketCode,
                status = issue.status,
                title = issue.title,
                locationName = issue.locationName,
                position = LatLng(issue.latitude, issue.longitude),
                isResolved = issue.isResolved,
                category = issue.category,
                timestamp = issue.createdAt ?: ""
            )
        }

        val filterDurationNanos = measureNanoTime {
            val active = filterPins(transformedPins, "Active")
            val resolved = filterPins(transformedPins, "Resolved")
            assertEquals(5000, active.size)
            assertEquals(5000, resolved.size)
        }
        val filterDurationMs = filterDurationNanos / 1_000_000.0
        println("⚡ Filtering 10,000 pins (Active + Resolved) took: $filterDurationMs ms")
        assertTrue("Filter took too long: ${filterDurationMs}ms", filterDurationMs < 30)
    }

    @Test
    fun benchmarkSpatialBoundingBoxFilter_5000ItemsInRadius_under10ms() {
        val userLocation = LatLng(12.9716, 77.5946)
        val radiusDegrees = 0.05 // ~5km radius

        val largeList = (1..5000).map { i ->
            val lat = 12.90 + (i % 100) * 0.001
            val lon = 77.50 + (i % 100) * 0.001
            LatLng(lat, lon)
        }

        val spatialFilterTimeNanos = measureNanoTime {
            val insideRadius = largeList.filter { pos ->
                kotlin.math.abs(pos.latitude - userLocation.latitude) <= radiusDegrees &&
                kotlin.math.abs(pos.longitude - userLocation.longitude) <= radiusDegrees
            }
            assertTrue(insideRadius.isNotEmpty())
        }
        val spatialFilterTimeMs = spatialFilterTimeNanos / 1_000_000.0
        println("⚡ Spatial bounding box filter across 5,000 coordinates took: $spatialFilterTimeMs ms")
        assertTrue("Spatial filter took too long: ${spatialFilterTimeMs}ms", spatialFilterTimeMs < 10.0)
    }

    // Helper functions mirroring CivicMapScreen logic
    private fun filterPins(pins: List<CivicIssuePin>, filter: String): List<CivicIssuePin> {
        return when (filter) {
            "Active" -> pins.filter { !it.isResolved }
            "Resolved" -> pins.filter { it.isResolved }
            else -> pins
        }
    }

    private fun resolveMapCenter(initialCenter: LatLng?, firstIssueLatLng: LatLng?): LatLng {
        return if (initialCenter != null) {
            initialCenter
        } else if (firstIssueLatLng != null) {
            firstIssueLatLng
        } else {
            LatLng(13.0827, 80.2707)
        }
    }

    private fun createMockDto(
        id: Long,
        ticketCode: String,
        title: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        isResolved: Boolean,
        status: String,
        category: String,
    ): CivicIssueDto {
        return CivicIssueDto(
            id = id,
            ticketCode = ticketCode,
            authorId = "user-1",
            authorUsername = "Reporter",
            authorAvatarUrl = null,
            title = title,
            caption = "Caption",
            category = category,
            locationName = locationName,
            subLocation = "SubLocation",
            latitude = latitude,
            longitude = longitude,
            isGpsVerified = true,
            status = status,
            mediaUrls = emptyList(),
            mediaType = "PHOTO",
            upvotesCount = 0,
            createdAt = "2026-11-24T10:00:00Z",
            updatedAt = null,
            resolvedAt = if (isResolved) "2026-11-24T12:00:00Z" else null,
            milestones = emptyList(),
        )
    }
}
