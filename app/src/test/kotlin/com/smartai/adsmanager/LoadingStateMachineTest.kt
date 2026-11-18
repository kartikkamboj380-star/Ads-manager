package com.smartai.adsmanager

import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.LoadingStateMachine
import com.smartai.adsmanager.domain.TransitionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LoadingStateMachineTest {

    @Test
    fun `happy path transitions through loading to completed`() {
        val m = LoadingStateMachine()
        assertEquals(TransitionResult.SUCCESS, m.onScreenDetected().result)
        assertEquals(TransitionResult.SUCCESS, m.onTargetButtonAvailable().result)
        assertEquals(TransitionResult.SUCCESS, m.onUserActionRequired().result)
        assertEquals(TransitionResult.SUCCESS, m.onUserActionStarted().result)
        assertEquals(LoadingState.LOADING, m.state)
        assertEquals(TransitionResult.SUCCESS, m.onWaitingForVerification().result)
        assertEquals(TransitionResult.SUCCESS, m.onCompleted().result)
        assertEquals(LoadingState.COMPLETED, m.state)
    }

    @Test
    fun `duplicate transition is reported and does not change state`() {
        val m = LoadingStateMachine()
        m.onScreenDetected()
        val outcome = m.onScreenDetected()
        assertEquals(TransitionResult.DUPLICATE, outcome.result)
        assertEquals(LoadingState.SCREEN_DETECTED, m.state)
    }

    @Test
    fun `illegal transition is rejected and keeps state`() {
        val m = LoadingStateMachine()
        // IDLE -> COMPLETED is not allowed.
        val outcome = m.transition(LoadingState.COMPLETED)
        assertEquals(TransitionResult.ILLEGAL, outcome.result)
        assertEquals(LoadingState.IDLE, m.state)
    }

    @Test
    fun `timeout and failed can transition back to user action required for retry`() {
        val m = LoadingStateMachine()
        m.onScreenDetected()
        m.onTargetButtonAvailable()
        m.onUserActionRequired()
        m.onUserActionStarted()
        m.onTimeout()
        assertEquals(LoadingState.TIMEOUT, m.state)
        assertEquals(TransitionResult.SUCCESS, m.onUserActionRequired().result)

        m.onUserActionStarted()
        m.onFailed()
        assertEquals(LoadingState.FAILED, m.state)
        assertEquals(TransitionResult.SUCCESS, m.onUserActionRequired().result)
    }

    @Test
    fun `stopped is reachable from active states`() {
        val m = LoadingStateMachine()
        m.onScreenDetected()
        assertEquals(TransitionResult.SUCCESS, m.onStopped().result)
        assertEquals(LoadingState.STOPPED, m.state)
    }

    @Test
    fun `capture lifecycle transitions are valid and guarded`() {
        val m = LoadingStateMachine()
        assertEquals(TransitionResult.SUCCESS, m.onPermissionRequested().result)
        assertEquals(LoadingState.PERMISSION_REQUESTED, m.state)
        assertEquals(TransitionResult.SUCCESS, m.onPermissionGranted().result)
        assertEquals(TransitionResult.SUCCESS, m.onCaptureInitializing().result)
        assertEquals(LoadingState.CAPTURE_INITIALIZING, m.state)
        assertEquals(TransitionResult.SUCCESS, m.onCaptureActive().result)
        assertEquals(LoadingState.CAPTURE_ACTIVE, m.state)
        assertEquals(TransitionResult.SUCCESS, m.onTargetAppDetected().result)
        assertEquals(LoadingState.TARGET_APP_DETECTED, m.state)
        assertEquals(TransitionResult.SUCCESS, m.onTargetScreenDetected().result)
        assertEquals(LoadingState.TARGET_SCREEN_DETECTED, m.state)
    }

    @Test
    fun `skipping permission to capture is rejected`() {
        val m = LoadingStateMachine()
        // IDLE -> CAPTURE_ACTIVE is not allowed without going through permission first.
        val outcome = m.transition(LoadingState.CAPTURE_ACTIVE)
        assertEquals(TransitionResult.ILLEGAL, outcome.result)
        assertEquals(LoadingState.IDLE, m.state)
    }

    @Test
    fun `capture stopped and permission revoked are terminal-ish`() {
        val m = LoadingStateMachine()
        m.onPermissionRequested()
        m.onPermissionGranted()
        m.onCaptureInitializing()
        m.onCaptureActive()
        assertEquals(TransitionResult.SUCCESS, m.onCaptureStopped().result)
        assertEquals(LoadingState.CAPTURE_STOPPED, m.state)
        assertEquals(TransitionResult.SUCCESS, m.transition(LoadingState.IDLE).result)

        m.onPermissionRequested()
        assertEquals(TransitionResult.SUCCESS, m.onPermissionRevoked().result)
        assertEquals(LoadingState.PERMISSION_REVOKED, m.state)
    }
}
