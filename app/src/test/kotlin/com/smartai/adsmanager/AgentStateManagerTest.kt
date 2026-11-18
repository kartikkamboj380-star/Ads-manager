package com.smartai.adsmanager

import com.smartai.adsmanager.domain.CaptureStatus
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.service.AgentStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStateManagerTest {

    private fun manager() = AgentStateManager(FakeClock())

    /** Drive the machine through the valid permission→capture sequence to CAPTURE_ACTIVE. */
    private fun activate(m: AgentStateManager) {
        m.permissionRequested()
        m.permissionGranted()
        m.captureInitializing()
        m.captureStarted()
    }

    @Test
    fun `initial status is idle with capture inactive`() {
        val m = manager()
        val status = m.statusFlow.value
        assertEquals(LoadingState.IDLE, status.state)
        assertFalse(status.captureActive)
        assertFalse(status.agentRunning)
        assertEquals(CaptureStatus.INACTIVE, status.captureStatus)
        assertNull(status.lastError)
    }

    @Test
    fun `capture lifecycle advances through permission and capture states`() {
        val m = manager()

        m.permissionRequested()
        assertEquals(LoadingState.PERMISSION_REQUESTED, m.state)

        m.permissionGranted()
        assertEquals(LoadingState.PERMISSION_GRANTED, m.state)

        m.captureInitializing()
        assertEquals(LoadingState.CAPTURE_INITIALIZING, m.state)
        assertEquals(CaptureStatus.INITIALIZING, m.statusFlow.value.captureStatus)

        m.captureStarted()
        assertEquals(LoadingState.CAPTURE_ACTIVE, m.state)
        assertTrue(m.statusFlow.value.captureActive)
        assertTrue(m.statusFlow.value.agentRunning)
        assertEquals(CaptureStatus.ACTIVE, m.statusFlow.value.captureStatus)

        m.targetAppDetected()
        assertEquals(LoadingState.TARGET_APP_DETECTED, m.state)

        m.targetScreenDetected()
        assertEquals(LoadingState.TARGET_SCREEN_DETECTED, m.state)
    }

    @Test
    fun `capture stopped clears agent running flag`() {
        val m = manager()
        activate(m)
        assertTrue(m.statusFlow.value.agentRunning)

        m.captureStopped()
        assertEquals(LoadingState.CAPTURE_STOPPED, m.state)
        assertFalse(m.statusFlow.value.agentRunning)
        assertFalse(m.statusFlow.value.captureActive)
        assertEquals(CaptureStatus.STOPPED, m.statusFlow.value.captureStatus)
    }

    @Test
    fun `permission revoked transitions and clears agent running`() {
        val m = manager()
        activate(m)

        m.permissionRevoked()
        assertEquals(LoadingState.PERMISSION_REVOKED, m.state)
        assertFalse(m.statusFlow.value.agentRunning)
        assertEquals(CaptureStatus.REVOKED, m.statusFlow.value.captureStatus)
    }

    @Test
    fun `capture failure records an error and fails the state`() {
        val m = manager()
        activate(m)

        m.captureFailed("MediaProjection consent expired")
        assertEquals(LoadingState.FAILED, m.state)
        assertEquals("MediaProjection consent expired", m.statusFlow.value.lastError)
        assertFalse(m.statusFlow.value.agentRunning)
    }

    @Test
    fun `last frame time is surfaced in status`() {
        val m = manager()
        m.setLastFrameTime(12_345L)
        assertEquals(12_345L, m.statusFlow.value.lastFrameTimeEpochMs)
    }

    @Test
    fun `stop returns to stopped state`() {
        val m = manager()
        activate(m)
        m.stop()
        assertEquals(LoadingState.STOPPED, m.state)
        assertFalse(m.statusFlow.value.agentRunning)
    }
}
