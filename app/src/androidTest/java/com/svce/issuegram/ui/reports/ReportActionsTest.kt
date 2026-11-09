package com.svce.issuegram.ui.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.svce.issuegram.data.model.CivicIssueDto
import com.svce.issuegram.data.model.EditIssueRequest
import com.svce.issuegram.ui.theme.IssuegramTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReportActionsTest {
    @get:Rule val compose = createComposeRule()
    private val issue = CivicIssueDto(id = 17, ticketCode = "CIV-17", authorId = "owner", authorUsername = "citizen_a",
        title = "Broken road", caption = "Broken road near library", locationName = "Library", latitude = 0.0, longitude = 0.0)

    private fun show(userId: String) {
        compose.setContent { IssuegramTheme {
            ReportActions(issue, userId, busy = false, onOpen = {}, onContact = {}, onEdit = { _, _, _ -> },
                onDelete = { _, _ -> }, onFlag = { _, _, _ -> }, onDeleted = {}, errorMessage = null, onClearError = {})
        } }
        compose.onNodeWithContentDescription("Report actions").performClick()
    }

    @Test fun ownerHasEditDeleteAndDeleteRequiresConfirmation() {
        show("owner")
        compose.onNodeWithText("Edit report").assertIsDisplayed()
        compose.onNodeWithText("Report a problem with this post").assertDoesNotExist()
        compose.onNodeWithText("Delete report").performClick()
        compose.onNodeWithText("Delete this report?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete this report?").assertDoesNotExist()
    }

    @Test fun otherCitizenHasModerationActionAndNoOwnerActions() {
        show("another-user")
        compose.onNodeWithText("Edit report").assertDoesNotExist()
        compose.onNodeWithText("Delete report").assertDoesNotExist()
        compose.onNodeWithText("Report a problem with this post").performClick()
        compose.onNodeWithText("Choose a reason for the moderation team.").assertIsDisplayed()
        compose.onNodeWithText("Send for review").assertIsDisplayed()
    }

    @Test fun newerRevisionKeepsTypedCaptionAndAdoptsUntouchedLocationAfterReview() {
        val current = mutableStateOf(issue.copy(updatedAt = "revision-1"))
        var saved: EditIssueRequest? = null
        var savedRevision: String? = null
        compose.setContent { IssuegramTheme {
            ReportEditorDialog(current.value, busy = false, errorMessage = null, onClearError = {}, onDismiss = {}, onSave = { base, edit ->
                saved = edit; savedRevision = base.updatedAt
            })
        } }
        compose.onNodeWithText("Describe the issue").performTextReplacement("My corrected road description")
        compose.runOnIdle { current.value = issue.copy(caption = "Another saved description", locationName = "Updated library entrance", updatedAt = "revision-2") }
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onNodeWithText("Use latest version and keep my changes").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("My corrected road description", saved!!.caption)
            assertEquals("Updated library entrance", saved!!.locationName)
            assertEquals("revision-2", savedRevision)
        }
    }
}
