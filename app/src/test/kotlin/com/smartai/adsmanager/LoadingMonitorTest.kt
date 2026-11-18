package com.smartai.adsmanager

import com.smartai.adsmanager.domain.LoadingMonitor
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.LoadingStateMachine
import com.smartai.adsmanager.domain.NoOpVerificationDetector
import com.smartai.adsmanager.domain.TextPatternVerificationDetector
import com.smartai.adsmanager.domain.VerificationDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingMonitorTest {

    private fun monitor(
        clock: FakeClock,
        detector: VerificationDetector = TextPatternVerificationDetector(),
        timeoutMs: Long = 1000L,
        warningMs: Long = 500L,
    ): Pair<LoadingMonitor, LoadingStateMachine> {
        val sm = LoadingStateMachine()
        val m = LoadingMonitor(sm, clock, timeoutMs = timeoutMs, warningThresholdMs = warningMs, detector = detector)
        return m to sm
    }

    /** Drive the machine to USER_ACTION_REQUIRED (the real entry point for user action). */
    private fun LoadingStateMachine.prepareForUserAction() {
        onScreenDetected()
        onTargetButtonAvailable()
        onUserActionRequired()
    }

    @Test
    fun `starting loading transitions to loading and tracks elapsed time`() {
        val clock = FakeClock()
        val (monitor, sm) = monitor(clock)
        sm.prepareForUserAction()
        monitor.onLoadingStarted()
        assertEquals(LoadingState.LOADING, sm.state)
        clock.advance(1200)
        assertEquals(1200L, monitor.elapsedMs())
    }

    @Test
    fun `verification signal completes the load (not a delay)`() {
        val clock = FakeClock()
        val (monitor, sm) = monitor(clock)
        sm.prepareForUserAction()
        monitor.onLoadingStarted()
        clock.advance(100)
        // A real reward-confirmation signal proves completion.
        monitor.tick("Reward received")
        assertEquals(LoadingState.COMPLETED, sm.state)
    }

    @Test
    fun `timeout fires when no signal and time exceeds limit`() {
        val clock = FakeClock()
        // NoOp detector -> completion never claimed by delay.
        val (monitor, sm) = monitor(clock, NoOpVerificationDetector(), timeoutMs = 1000L)
        sm.prepareForUserAction()
        monitor.onLoadingStarted()
        clock.advance(900)
        monitor.tick("still loading")
        assertEquals(LoadingState.LOADING, sm.state)
        clock.advance(200)
        monitor.tick("still loading")
        assertEquals(LoadingState.TIMEOUT, sm.state)
    }

    @Test
    fun `warning becomes active before timeout`() {
        val clock = FakeClock()
        val (monitor, sm) = monitor(clock, NoOpVerificationDetector(), timeoutMs = 1000L, warningMs = 500L)
        sm.prepareForUserAction()
        monitor.onLoadingStarted()
        clock.advance(400)
        assertFalse(monitor.isWarningActive())
        clock.advance(200) // 600ms > 500 threshold
        assertTrue(monitor.isWarningActive())
        assertEquals(LoadingState.LOADING, sm.state)
    }

    @Test
    fun `stop transitions to stopped`() {
        val clock = FakeClock()
        val (monitor, sm) = monitor(clock)
        sm.prepareForUserAction()
        monitor.onLoadingStarted()
        monitor.stop()
        assertEquals(LoadingState.STOPPED, sm.state)
    }

    @Test
    fun `retry returns to user action required`() {
        val clock = FakeClock()
        val (monitor, sm) = monitor(clock, NoOpVerificationDetector(), timeoutMs = 1000L)
        sm.prepareForUserAction()
        monitor.onLoadingStarted()
        clock.advance(1500)
        monitor.tick("x")
        assertEquals(LoadingState.TIMEOUT, sm.state)
        monitor.requestRetry()
        assertEquals(LoadingState.USER_ACTION_REQUIRED, sm.state)
    }
}
