package com.svce.issuegram.domain

import com.svce.issuegram.data.model.CivicIssueDto
import org.junit.Assert.*
import org.junit.Test

class CivicActionsTest {
    private val issue = CivicIssueDto(id = 17, ticketCode = "CIV-17", authorId = "owner", authorUsername = "citizen_a", title = "Broken road",
        caption = "Pavement broken by the library", category = "Public Works", locationName = "Main Street", subLocation = "North gate", latitude = 0.0, longitude = 0.0)

    @Test fun searchMatchesTicketDescriptionAndLocationWithoutChangingStatusMeaning() {
        listOf("civ-17", "LIBRARY", "main", "north gate").forEach { assertTrue(CivicActions.matches(issue, it, "All", "Active")) }
        assertFalse(CivicActions.matches(issue, "", "Roads & Transit", "All"))
        assertFalse(CivicActions.matches(issue, "", "All", "Resolved"))
        assertFalse(CivicActions.matches(issue.copy(status = "RESOLVED"), "", "All", "Active"))
        assertTrue(CivicActions.matches(issue.copy(status = "RESOLVED"), "", "All", "Resolved"))
    }

    @Test fun editNormalizesContentAndUsesSameValidationAsNewReport() {
        val edit = CivicActions.edit("  Broken road\nPlease repair  ", "Public Works", "  Main Street  ", " North gate ", 0.0, 0.0)
        assertEquals("Broken road", edit.title)
        assertEquals("Broken road\nPlease repair", edit.caption)
        assertEquals("Main Street", edit.locationName)
        assertEquals("North gate", edit.subLocation)
        try { CivicActions.edit("bad", "Public Works", "Park", "", 0.0, 0.0); fail("Expected short caption rejection") } catch (_: IllegalArgumentException) { }
        try { CivicActions.edit("Broken road", "Public Works", "Park", "", Double.NaN, 0.0); fail("Expected invalid location rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun onlyExactNonblankOwnerMayManageReport() {
        CivicActions.requireOwner(issue, "owner")
        listOf("other", "").forEach { id ->
            try { CivicActions.requireOwner(issue, id); fail("Non-owner accepted") } catch (_: IllegalStateException) { }
        }
    }

    @Test fun sharingHasUsefulEvidenceAndRealLocationWithoutInventingAppLinks() {
        val text = CivicActions.shareText(issue)
        assertTrue(text.contains("CIV-17")); assertTrue(text.contains(issue.caption))
        assertTrue(text.contains("https://www.google.com/maps/search/?api=1&query=0.0,0.0"))
        assertTrue(text.contains("REPORTED")); assertFalse(text.contains("issuegram.com"))
    }
}
