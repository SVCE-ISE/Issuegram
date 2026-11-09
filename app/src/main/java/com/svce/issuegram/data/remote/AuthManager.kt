package com.svce.issuegram.data.remote

import com.svce.issuegram.data.model.AuthSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SessionPersistence {
    suspend fun read(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear()
}

interface AuthGateway {
    suspend fun createAnonymousSession(): AuthSession
    suspend fun refreshSession(refreshToken: String): AuthSession
}

class AuthManager(
    private val store: SessionPersistence,
    private val gateway: AuthGateway,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mutex = Mutex()

    suspend fun begin(): AuthSession = mutex.withLock {
        store.read()?.let { return@withLock valid(it) }
        gateway.createAnonymousSession().withExpiry(now()).also { store.save(it) }
    }

    suspend fun session(forceRefresh: Boolean = false): AuthSession = mutex.withLock {
        val saved = store.read() ?: throw IllegalStateException("Get started to create your civic identity.")
        valid(saved, forceRefresh)
    }

    private suspend fun valid(saved: AuthSession, force: Boolean = false): AuthSession {
        if (!force && saved.expiresAt > now() + 60) return saved
        val refreshed = gateway.refreshSession(saved.refreshToken).withExpiry(now())
        check(refreshed.user.id == saved.user.id) { "The refreshed identity did not match." }
        store.save(refreshed)
        return refreshed
    }
}
