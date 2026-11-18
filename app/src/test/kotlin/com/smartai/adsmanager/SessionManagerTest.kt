package com.smartai.adsmanager

import com.smartai.adsmanager.domain.SessionManager
import com.smartai.adsmanager.domain.SessionStatus
import com.smartai.adsmanager.domain.StartSessionResultType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private fun manager() =
        SessionManager(InMemorySessionRepository(), FakeClock())

    @Test
    fun `start creates an active session`() = runBlocking {
        val m = manager()
        val result = m.start("com.app", "App")
        assertEquals(StartSessionResultType.STARTED, result.type)
        assertNotNull(m.getActive())
        assertEquals(SessionStatus.ACTIVE, m.getActive()!!.status)
    }

    @Test
    fun `duplicate session is prevented`() = runBlocking {
        val m = manager()
        m.start("com.app", "App")
        val second = m.start("com.app", "App")
        assertEquals(StartSessionResultType.DUPLICATE, second.type)
    }

    @Test
    fun `stop ends the session and frees the slot`() = runBlocking {
        val m = manager()
        val result = m.start("com.app", "App")
        m.stop(result.sessionId)
        assertNull(m.getActive())
        // After stopping, a new session can start.
        assertEquals(StartSessionResultType.STARTED, m.start("com.app", "App").type)
    }

    @Test
    fun `restart recovery marks interrupted active session as failed`() = runBlocking {
        val repo = InMemorySessionRepository()
        val m = SessionManager(repo, FakeClock())
        // Simulate an active session persisted from a previous process.
        val result = m.start("com.app", "App")
        // Simulate process death: a fresh manager with the same repo, no in-memory id.
        val recovered = SessionManager(repo, FakeClock())
        val interrupted = recovered.recover()
        assertNotNull(interrupted)
        assertEquals(SessionStatus.FAILED, interrupted!!.status)
        assertFalse(recovered.hasActiveSession())
        // The session id is surfaced for the user to review.
        assertEquals(result.sessionId, interrupted.id)
    }

    @Test
    fun `hasActiveSession reflects state`() = runBlocking {
        val m = manager()
        assertFalse(m.hasActiveSession())
        m.start("com.app", "App")
        assertTrue(m.hasActiveSession())
    }
}
