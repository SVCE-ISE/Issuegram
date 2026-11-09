package com.svce.issuegram.data.remote

import com.svce.issuegram.data.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SupabaseApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SupabaseApiClient
    private lateinit var store: MemorySessionStore
    private val userId = "11111111-1111-4111-8111-111111111111"
    private val requestId = "22222222-2222-4222-8222-222222222222"
    private val threadId = "33333333-3333-4333-8333-333333333333"
    private val issue = """{"id":17,"author_id":"$userId","author_username":"citizen_a","title":"Broken pavement","caption":"Broken pavement by the park","location_name":"Park","latitude":0.0,"longitude":0.0}"""
    private val message = """{"id":8,"channel_id":"roads","sender_id":"$userId","sender_username":"citizen_a","message":"Any update?","timestamp_label":"2026-11-09T12:00:00Z","request_id":"$requestId"}"""

    @Before fun setup() {
        server = MockWebServer(); server.start()
        store = MemorySessionStore(AuthSession("user-jwt", "saved-refresh", expiresAt = Long.MAX_VALUE, user = AuthUser(userId)))
        api = SupabaseApiClient(server.url("/").toString().trimEnd('/'), "public-app-key")
        api.auth = AuthManager(store, api)
    }
    @After fun teardown() { server.shutdown() }
    private fun respond(body: String, code: Int = 200) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)) }

    @Test fun profileIsLookedUpByAuthenticatedUuidWithUserBearer() = runBlocking {
        respond("[]")
        assertNull(api.fetchOwnProfile())
        val request = server.takeRequest()
        assertEquals("public-app-key", request.getHeader("apikey"))
        assertEquals("Bearer user-jwt", request.getHeader("Authorization"))
        assertEquals("eq.$userId", request.requestUrl!!.queryParameter("id"))
        assertNull(request.requestUrl!!.queryParameter("username"))
    }

    @Test fun cannotCreateProfileForAnotherIdentity() = runBlocking {
        try { api.createProfile("another-user", "citizen_a", "A"); fail("Expected ownership check") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun anonymousSignupUsesEmptyBodyAndNoUserBearer() = runBlocking {
        respond("""{"access_token":"new-token","refresh_token":"new-refresh","expires_in":3600,"user":{"id":"$userId"}}""")
        val result = api.createAnonymousSession()
        val request = server.takeRequest()
        assertEquals("/auth/v1/signup", request.path)
        assertEquals("{}", request.body.readUtf8())
        assertNull(request.getHeader("Authorization"))
        assertEquals(userId, result.user.id)
    }

    @Test fun unauthorizedRequestRefreshesTokenAndRetriesWithSameIdentity() = runBlocking {
        respond("""{"code":"PGRST301"}""", 401)
        respond("""{"access_token":"renewed-jwt","refresh_token":"renewed-refresh","expires_in":3600,"user":{"id":"$userId"}}""")
        respond("[]")
        api.fetchOwnProfile()
        assertEquals("Bearer user-jwt", server.takeRequest().getHeader("Authorization"))
        val refresh = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", refresh.path)
        assertEquals("saved-refresh", api.json.parseToJsonElement(refresh.body.readUtf8()).jsonObject["refresh_token"]!!.jsonPrimitive.content)
        assertNull(refresh.getHeader("Authorization"))
        assertEquals("Bearer renewed-jwt", server.takeRequest().getHeader("Authorization"))
        assertEquals("renewed-refresh", store.saved!!.refreshToken)
    }

    @Test fun privateChatFiltersByConversationInsteadOfSharedDepartment() = runBlocking {
        respond("""[{"id":"$threadId"}]"""); respond("[$message]")
        val messages = api.fetchChannelMessages("roads")
        val conversation = server.takeRequest()
        assertTrue(conversation.path!!.startsWith("/rest/v1/conversations?"))
        assertEquals(userId, api.json.parseToJsonElement(conversation.body.readUtf8()).jsonObject["user_id"]!!.jsonPrimitive.content)
        val read = server.takeRequest()
        assertEquals("eq.$threadId", read.requestUrl!!.queryParameter("conversation_id"))
        assertNull(read.requestUrl!!.queryParameter("channel_id"))
        assertEquals(8L, messages.single().id)
    }

    @Test fun duplicateReportResolvesAlreadyCommittedRequestWithoutAnotherInsert() = runBlocking {
        respond("[]"); respond("[$issue]")
        val created = api.createIssue(CreateIssueRequest(requestId, userId, "citizen_a", title = "Broken pavement", caption = "Broken pavement by the park",
            category = "Public Works", locationName = "Park", subLocation = "", latitude = 0.0, longitude = 0.0, mediaUrls = listOf("https://example.test/photo.jpg")))
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("request_id", post.requestUrl!!.queryParameter("on_conflict"))
        assertEquals("resolution=ignore-duplicates,return=representation", post.getHeader("Prefer"))
        assertEquals(requestId, api.json.parseToJsonElement(post.body.readUtf8()).jsonObject["request_id"]!!.jsonPrimitive.content)
        val recovery = server.takeRequest()
        assertEquals("GET", recovery.method)
        assertEquals("eq.$requestId", recovery.requestUrl!!.queryParameter("request_id"))
        assertEquals(17L, created.id)
    }

    @Test fun duplicateMessageRequiresConfirmationOfOriginalRequest() = runBlocking {
        respond("[]"); respond("[$message]")
        val sent = api.sendChannelMessage(SendMessageRequest(requestId, threadId, "roads", userId, "citizen_a", "Any update?", "2026-11-09T12:00:00Z"))
        assertEquals(requestId, sent.requestId)
        val post = server.takeRequest()
        assertEquals("request_id", post.requestUrl!!.queryParameter("on_conflict"))
        assertEquals(threadId, api.json.parseToJsonElement(post.body.readUtf8()).jsonObject["conversation_id"]!!.jsonPrimitive.content)
        assertEquals("eq.$requestId", server.takeRequest().requestUrl!!.queryParameter("request_id"))
    }

    @Test fun storageUsesOwnerPrefixAndStablePathAcrossRetry() = runBlocking {
        repeat(2) { respond("{}"); api.uploadFile("issue-media", requestId, byteArrayOf(1, 2)) }
        repeat(2) {
            val upload = server.takeRequest()
            assertEquals("/storage/v1/object/issue-media/$userId/$requestId.jpg", upload.path)
            assertEquals("Bearer user-jwt", upload.getHeader("Authorization"))
            assertEquals("true", upload.getHeader("x-upsert"))
        }
    }

    @Test fun permissionErrorDoesNotExposeRawServerDetailsOrPretendSuccess() = runBlocking {
        respond("""{"code":"42501","message":"private database details"}""", 403)
        try { api.sendChannelMessage(SendMessageRequest(requestId, threadId, "roads", userId, "citizen_a", "Hello", "now")); fail("Expected permission failure") }
        catch (e: ApiException) { assertEquals(403, e.status); assertFalse(e.retryable); assertFalse(e.message!!.contains("private database")) }
    }

    @Test fun statusUpdateWithNoPermittedRowsIsFailure() = runBlocking {
        respond("[]")
        try { api.updateIssueStatus(17, true); fail("Expected empty update failure") } catch (_: IllegalStateException) { }
        val body = api.json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("RESOLVED", body["status"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("resolved_at"))
    }

    private fun ownedIssue(): CivicIssueDto = api.json.decodeFromString<CivicIssueDto>(issue).copy(updatedAt = "2026-11-09T12:00:00+00:00")
    private fun edit() = EditIssueRequest("Corrected pavement", "Corrected pavement description", "Public Works", "Park entrance", "North gate", 1.0, 2.0)

    @Test fun reportEditRestrictsOwnerAndRevisionAndOnlySendsEditableFields() = runBlocking {
        respond("[$issue]")
        api.editIssue(ownedIssue(), edit())
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("eq.17", request.requestUrl!!.queryParameter("id"))
        assertEquals("eq.$userId", request.requestUrl!!.queryParameter("author_id"))
        assertEquals("eq.2026-11-09T12:00:00+00:00", request.requestUrl!!.queryParameter("updated_at"))
        val body = api.json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("title", "caption", "category", "location_name", "sub_location", "latitude", "longitude"), body.keys)
        assertEquals("Corrected pavement description", body["caption"]!!.jsonPrimitive.content)
    }

    @Test fun reportEditConflictDoesNotClaimSaveSucceeded() = runBlocking {
        respond("[]")
        try { api.editIssue(ownedIssue(), edit()); fail("Expected edit conflict") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("changed")) }
        assertEquals(1, server.requestCount)
    }

    @Test fun reportMutationForAnotherAuthorNeverReachesNetwork() = runBlocking {
        val other = ownedIssue().copy(authorId = "another-user")
        try { api.editIssue(other, edit()); fail("Expected owner check") } catch (_: IllegalStateException) { }
        try { api.deleteIssue(other); fail("Expected owner check") } catch (_: IllegalStateException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun reportDeleteFiltersOwnerAndConfirmsDeletedRow() = runBlocking {
        respond("[$issue]")
        api.deleteIssue(ownedIssue())
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("eq.$userId", request.requestUrl!!.queryParameter("author_id"))
        assertEquals("eq.2026-11-09T12:00:00+00:00", request.requestUrl!!.queryParameter("updated_at"))
        assertEquals("return=representation", request.getHeader("Prefer"))
    }

    @Test fun lostDeleteResponseCanBeRetriedWhenRowIsAlreadyGone() = runBlocking {
        respond("[]"); respond("[]")
        api.deleteIssue(ownedIssue())
        assertEquals("DELETE", server.takeRequest().method)
        val verify = server.takeRequest()
        assertEquals("GET", verify.method)
        assertEquals("eq.17", verify.requestUrl!!.queryParameter("id"))
    }

    @Test fun deleteWithEmptyResultAndExistingRowIsFailure() = runBlocking {
        respond("[]"); respond("[$issue]")
        try { api.deleteIssue(ownedIssue()); fail("Expected delete conflict") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("could not be deleted")) }
    }

    @Test fun postFlagUsesCallerIdentityAndIdempotencyKey() = runBlocking {
        respond("", 201)
        api.flagIssue(ownedIssue().copy(authorId = "another-user"), "Duplicate report")
        val request = server.takeRequest()
        assertEquals("issue_id,reporter_id", request.requestUrl!!.queryParameter("on_conflict"))
        val body = api.json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(userId, body["reporter_id"]!!.jsonPrimitive.content)
        assertEquals(17, body["issue_id"]!!.jsonPrimitive.int)
        assertEquals("Duplicate report", body["reason"]!!.jsonPrimitive.content)
    }

    @Test fun selfFlagsAndUnknownReasonsAreRejectedWithoutSending() = runBlocking {
        try { api.flagIssue(ownedIssue(), "Duplicate report"); fail("Expected self flag rejection") } catch (_: IllegalArgumentException) { }
        try { api.flagIssue(ownedIssue().copy(authorId = "other"), "unvalidated reason"); fail("Expected reason validation") } catch (_: IllegalArgumentException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun removeAvatarSendsExplicitNullAndDoesNotOverwriteOtherFields() = runBlocking {
        respond("""[{"id":"$userId","username":"citizen_a","display_name":"A","avatar_url":null}]""")
        assertNull(api.updateProfile(clearAvatar = true).avatarUrl)
        val body = api.json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("avatar_url"), body.keys)
        assertEquals(JsonNull, body["avatar_url"])
    }
}
