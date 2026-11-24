package com.svce.issuegram

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityRulesContractTest {

    private fun resolveProjectFile(name: String): File {
        val direct = File(name)
        if (direct.exists()) return direct
        val parent = File("..", name)
        if (parent.exists()) return parent
        error("Could not locate $name from ${File(".").absolutePath}")
    }

    @Test
    fun firestoreRules_enforceAdminAndOwnershipChecks() {
        val rules = resolveProjectFile("firestore.rules").readText()

        assertTrue("Missing isAdmin() helper", rules.contains("function isAdmin()"))
        assertTrue(
            "Users must not be able to self-escalate isAdmin",
            rules.contains("!request.resource.data.diff(resource.data).affectedKeys().hasAny(['isAdmin'])")
        )
        assertTrue(
            "Issues create must bind reporterId to request.auth.uid",
            rules.contains("request.resource.data.reporterId == request.auth.uid")
        )
        assertTrue(
            "Notifications create must be restricted to admins",
            rules.contains("allow create: if isAdmin();")
        )
        assertFalse(
            "Overly permissive user write rule must not exist",
            rules.contains("match /users/{userId} {\n      allow read: if true;\n      allow write: if request.auth != null;")
        )
    }

    @Test
    fun storageRules_enforceImageContentTypeAndSizeLimit() {
        val rules = resolveProjectFile("storage.rules").readText()

        assertTrue(
            "Storage rules must enforce max file size",
            rules.contains("request.resource.size <")
        )
        assertTrue(
            "Storage rules must enforce image/* contentType",
            rules.contains("request.resource.contentType.matches('image/.*')")
        )
    }
}
