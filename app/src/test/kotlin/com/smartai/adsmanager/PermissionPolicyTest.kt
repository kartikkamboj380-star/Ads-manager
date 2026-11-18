package com.smartai.adsmanager

import com.smartai.adsmanager.domain.PermissionPolicy
import com.smartai.adsmanager.domain.PermissionSnapshot
import com.smartai.adsmanager.domain.PermissionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {

    private val policy = PermissionPolicy()

    @Test
    fun `monitoring requires screen capture and notifications`() {
        assertEquals(
            listOf(PermissionType.SCREEN_CAPTURE, PermissionType.POST_NOTIFICATIONS),
            policy.requiredForMonitoring(),
        )
    }

    @Test
    fun `can start only when all required permissions granted`() {
        assertTrue(
            policy.canStartMonitoring(
                PermissionSnapshot(setOf(PermissionType.SCREEN_CAPTURE, PermissionType.POST_NOTIFICATIONS)),
            ),
        )
    }

    @Test
    fun `cannot start when permissions missing`() {
        assertFalse(policy.canStartMonitoring(PermissionSnapshot(emptySet())))
        assertFalse(
            policy.canStartMonitoring(PermissionSnapshot(setOf(PermissionType.SCREEN_CAPTURE))),
        )
    }

    @Test
    fun `missing lists the missing permissions`() {
        val missing = policy.missing(PermissionSnapshot(setOf(PermissionType.SCREEN_CAPTURE)))
        assertEquals(listOf(PermissionType.POST_NOTIFICATIONS), missing)
    }
}
