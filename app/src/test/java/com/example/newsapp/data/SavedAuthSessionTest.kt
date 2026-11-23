package com.example.newsapp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedAuthSessionTest {
    private val session = SavedAuthSession("token", userId = 42, expiresAt = 2_000)

    @Test
    fun previouslyValidatedSessionCanOpenOfflineBeforeExpiry() {
        assertTrue(session.isValidAt(now = 1_999))
    }

    @Test
    fun sessionStopsBeingValidAtExpiry() {
        assertFalse(session.isValidAt(now = 2_000))
        assertTrue(session.hasExpired(now = 2_000))
    }

    @Test
    fun legacySessionRequiresOnlineValidation() {
        val legacySession = session.copy(expiresAt = 0)
        assertFalse(legacySession.isValidAt(now = 1_999))
        assertFalse(legacySession.hasExpired(now = 1_999))
    }

    @Test
    fun missingAccountOrTokenDisallowsOfflineAccess() {
        assertFalse(session.copy(userId = 0).isValidAt(now = 1_999))
        assertFalse(session.copy(token = "").isValidAt(now = 1_999))
    }
}
