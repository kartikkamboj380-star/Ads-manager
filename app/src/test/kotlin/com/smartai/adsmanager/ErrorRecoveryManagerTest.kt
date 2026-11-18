package com.smartai.adsmanager

import com.smartai.adsmanager.domain.ErrorCategory
import com.smartai.adsmanager.domain.ErrorRecoveryManager
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.RecoveryAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorRecoveryManagerTest {

    private val manager = ErrorRecoveryManager(maxRetries = 2)

    @Test
    fun `timeout state classifies as timeout`() {
        assertEquals(
            ErrorCategory.TIMEOUT,
            manager.classify(RuntimeException("boom"), LoadingState.TIMEOUT),
        )
    }

    @Test
    fun `classification by message`() {
        assertEquals(
            ErrorCategory.VERIFICATION_FAILED,
            manager.classify(RuntimeException("verification failed"), LoadingState.LOADING),
        )
        assertEquals(
            ErrorCategory.SCREEN_NOT_FOUND,
            manager.classify(RuntimeException("could not recognize screen via ocr"), LoadingState.IDLE),
        )
        assertEquals(
            ErrorCategory.TARGET_MISSING,
            manager.classify(RuntimeException("target button missing"), LoadingState.SCREEN_DETECTED),
        )
    }

    @Test
    fun `timeout within retry budget suggests retry`() {
        val plan = manager.plan(RuntimeException("timeout"), LoadingState.TIMEOUT, retryCount = 0)
        assertEquals(RecoveryAction.RETRY, plan.action)
    }

    @Test
    fun `timeout beyond retry budget suggests stop`() {
        val plan = manager.plan(RuntimeException("timeout"), LoadingState.TIMEOUT, retryCount = 2)
        assertEquals(RecoveryAction.STOP, plan.action)
    }

    @Test
    fun `verification failure never auto-retries`() {
        val plan = manager.plan(
            RuntimeException("verification failed"),
            LoadingState.WAITING_FOR_VERIFICATION,
            retryCount = 0,
        )
        assertEquals(RecoveryAction.STOP, plan.action)
    }

    @Test
    fun `screen not found suggests continue monitoring`() {
        val plan = manager.plan(
            RuntimeException("screen not found"),
            LoadingState.SCREEN_DETECTED,
            retryCount = 0,
        )
        assertEquals(RecoveryAction.MONITOR, plan.action)
    }
}
