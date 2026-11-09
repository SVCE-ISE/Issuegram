package com.svce.issuegram.data.local

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Run against a dedicated debug install: connectedDebugAndroidTest. No hosted service is used. */
class LocalStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: LocalStore
    @Before fun setup() {
        context.deleteDatabase("issuegram-v2.db")
        store = LocalStore(context)
    }
    @After fun teardown() { store.close(); context.deleteDatabase("issuegram-v2.db") }

    @Test fun pendingUploadAndCacheSurviveReopeningDatabase() {
        val entry = OutboxEntry("request-a", "user-a", "issue", "{\"caption\":\"Broken road\"}", "/saved/photo.jpg")
        store.enqueue(entry); store.cache("user-a:issues", "cached issues")
        store.close(); store = LocalStore(context)
        assertEquals(listOf(entry), store.entries("user-a"))
        assertEquals("cached issues", store.cached("user-a:issues"))
    }

    @Test fun failedEntriesRemainDurableAndRetryOnlyAffectsOwner() {
        store.enqueue(OutboxEntry("request-a", "user-a", "message", "message a"))
        store.enqueue(OutboxEntry("request-b", "user-b", "message", "message b"))
        store.failed("request-a", "Permission denied"); store.failed("request-b", "Permission denied")
        store.close(); store = LocalStore(context)
        assertTrue(store.entries("user-a").single().failed)
        store.retry("user-a")
        assertFalse(store.entries("user-a").single().failed)
        assertTrue(store.entries("user-b").single().failed)
        store.completed("request-a")
        assertTrue(store.entries("user-a").isEmpty())
        assertEquals("request-b", store.entries("user-b").single().id)
    }

    @Test fun duplicateQueueIdCannotReplaceUnsentPayload() {
        val original = OutboxEntry("request-a", "user-a", "message", "original text")
        store.enqueue(original)
        try { store.enqueue(original.copy(payload = "replacement")); fail("Expected duplicate rejection") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(original, store.entries("user-a").single())
    }
}
