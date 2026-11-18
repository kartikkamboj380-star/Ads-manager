package com.smartai.adsmanager

import android.content.Intent
import com.smartai.adsmanager.service.ScreenCapturePermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Uses the literal consent result code (`-1`, i.e. `Activity.RESULT_OK`) so the test
 * does not depend on how the Kotlin compiler inlines the Android framework constant in
 * a JVM unit test (which made the result ambiguous under `returnDefaultValues`).
 *
 * `Intent` is a JVM stub here (returnDefaultValues), so constructing it is deterministic
 * and [ScreenCapturePermissionManager.interpretResult] returns the same reference on grant.
 */
class ScreenCapturePermissionManagerTest {

    private val manager = ScreenCapturePermissionManager()

    @Test
    fun `granted result returns the consent intent`() {
        val data = Intent()
        // Activity.RESULT_OK is -1.
        assertEquals(data, manager.interpretResult(-1, data))
    }

    @Test
    fun `non-ok result returns null even with data`() {
        val data = Intent()
        assertNull(manager.interpretResult(0, data))
        assertNull(manager.interpretResult(-2, data))
    }

    @Test
    fun `ok result with null data returns null`() {
        assertNull(manager.interpretResult(-1, null))
    }
}
