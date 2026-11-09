package com.svce.issuegram.data.remote

import com.svce.issuegram.data.model.AuthSession
import com.svce.issuegram.data.model.AuthUser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

internal class MemorySessionStore(var saved: AuthSession? = null) : SessionPersistence {
    override suspend fun read() = saved
    override suspend fun save(session: AuthSession) { saved = session }
    override suspend fun clear() { saved = null }
}

class AuthManagerTest {
    private fun identity(token: String = "access", expires: Long = 5000, id: String = "user-a") =
        AuthSession(token, "refresh-$token", expiresAt = expires, user = AuthUser(id))

    @Test fun firstLaunchPersistsOneIdentityAcrossConcurrentCallers() = runBlocking {
        val store = MemorySessionStore()
        var creations = 0
        val gateway = object : AuthGateway {
            override suspend fun createAnonymousSession(): AuthSession { creations++; delay(20); return identity(expires = 0) }
            override suspend fun refreshSession(refreshToken: String): AuthSession = error("Unexpected refresh")
        }
        val auth = AuthManager(store, gateway) { 1000 }
        val sessions = List(20) { async { auth.begin() } }.awaitAll()
        assertEquals(1, creations)
        assertTrue(sessions.all { it.user.id == "user-a" })
        assertEquals(4600L, store.saved!!.expiresAt)
    }

    @Test fun reopeningUsesSavedIdentityWithoutSignup() = runBlocking {
        val saved = identity()
        val auth = AuthManager(MemorySessionStore(saved), object : AuthGateway {
            override suspend fun createAnonymousSession(): AuthSession = error("Must not sign up again")
            override suspend fun refreshSession(refreshToken: String): AuthSession = error("Unexpected refresh")
        }) { 1000 }
        assertEquals(saved, auth.begin())
    }

    @Test fun expiredSessionRefreshesOnceForConcurrentRequests() = runBlocking {
        val store = MemorySessionStore(identity(expires = 900))
        var refreshes = 0
        val auth = AuthManager(store, object : AuthGateway {
            override suspend fun createAnonymousSession(): AuthSession = error("Unexpected signup")
            override suspend fun refreshSession(refreshToken: String): AuthSession {
                assertEquals("refresh-access", refreshToken); refreshes++; delay(20)
                return identity("renewed", expires = 0)
            }
        }) { 1000 }
        val sessions = List(20) { async { auth.session() } }.awaitAll()
        assertEquals(1, refreshes)
        assertTrue(sessions.all { it.accessToken == "renewed" })
        assertEquals("refresh-renewed", store.saved!!.refreshToken)
    }

    @Test fun failedRefreshKeepsIdentityAndNeverCreatesReplacement() = runBlocking {
        val saved = identity(expires = 900)
        val store = MemorySessionStore(saved)
        val auth = AuthManager(store, object : AuthGateway {
            override suspend fun createAnonymousSession(): AuthSession = error("Must not replace identity")
            override suspend fun refreshSession(refreshToken: String): AuthSession = throw IOException("Offline")
        }) { 1000 }
        try { auth.begin(); fail("Expected offline failure") } catch (_: IOException) { }
        assertEquals(saved, store.saved)
    }

    @Test fun mismatchedRefreshCannotOverwriteSavedIdentity() = runBlocking {
        val saved = identity(expires = 900)
        val store = MemorySessionStore(saved)
        val auth = AuthManager(store, object : AuthGateway {
            override suspend fun createAnonymousSession(): AuthSession = error("Unexpected signup")
            override suspend fun refreshSession(refreshToken: String) = identity(id = "user-b")
        }) { 1000 }
        try { auth.session(); fail("Expected identity mismatch") } catch (_: IllegalStateException) { }
        assertEquals(saved, store.saved)
    }

    @Test fun requestWithoutIdentityDoesNotSilentlySignUp() = runBlocking {
        val auth = AuthManager(MemorySessionStore(), object : AuthGateway {
            override suspend fun createAnonymousSession(): AuthSession = error("Unexpected signup")
            override suspend fun refreshSession(refreshToken: String): AuthSession = error("Unexpected refresh")
        }) { 1000 }
        try { auth.session(); fail("Expected missing identity") } catch (_: IllegalStateException) { }
    }
}
