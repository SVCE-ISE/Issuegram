package com.svce.issuegram.domain

import org.junit.Assert.*
import org.junit.Test

class ReportRulesTest {
    @Test fun allowsRealCoordinatesIncludingZeroAndBoundaryValues() {
        listOf(0.0 to 0.0, -90.0 to -180.0, 90.0 to 180.0).forEach { (lat, lon) ->
            ReportRules.validate("Broken pavement", "Public Works", "Park", lat, lon)
        }
    }
    @Test fun rejectsMissingDescriptionAndInvalidLocations() {
        val invalid = listOf(
            { ReportRules.validate("    ", "Public Works", "Park", 0.0, 0.0) },
            { ReportRules.validate("a".repeat(501), "Public Works", "Park", 0.0, 0.0) },
            { ReportRules.validate("Broken pavement", "Unknown", "Park", 0.0, 0.0) },
            { ReportRules.validate("Broken pavement", "Public Works", " ", 0.0, 0.0) },
            { ReportRules.validate("Broken pavement", "Public Works", "Park", Double.NaN, 0.0) },
            { ReportRules.validate("Broken pavement", "Public Works", "Park", 91.0, 0.0) },
            { ReportRules.validate("Broken pavement", "Public Works", "Park", 0.0, Double.POSITIVE_INFINITY) },
            { ReportRules.validate("Broken pavement", "Public Works", "Park", 0.0, -181.0) },
        )
        invalid.forEach { validate -> try { validate(); fail("Invalid report accepted") } catch (_: IllegalArgumentException) { } }
    }
    @Test fun generatedHandleIsIndependentAndValidForOnboarding() {
        val handles = List(1000) { ReportRules.randomUsername() }
        assertEquals(handles.size, handles.toSet().size)
        assertTrue(handles.all { it.matches(Regex("citizen_[a-f0-9]{12}")) })
    }
}
