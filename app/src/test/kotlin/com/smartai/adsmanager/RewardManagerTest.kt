package com.smartai.adsmanager

import com.smartai.adsmanager.domain.RewardSource
import com.smartai.adsmanager.domain.RewardStatus
import com.smartai.adsmanager.domain.RewardManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardManagerTest {

    private fun manager() = RewardManager(InMemoryRewardRepository(), FakeClock())

    @Test
    fun `manual reward is recorded as unverified`() = runBlocking {
        val manager = manager()
        val rec = manager.recordManualReward("App", "com.app", 1.0, "session-1")
        assertEquals(RewardStatus.UNVERIFIED, rec.status)
        assertFalse(rec.verified)
        assertEquals(RewardSource.MANUAL, rec.source)
        assertEquals(1, manager.unverified().size)
    }

    @Test
    fun `official api verification completes the reward`() = runBlocking {
        val manager = manager()
        val rec = manager.recordManualReward("App", "com.app", 1.0, "session-1")
        val updated = manager.verifyReward(rec.id, RewardSource.OFFICIAL_API)
        assertNotNull(updated)
        assertEquals(RewardStatus.COMPLETED, updated!!.status)
        assertTrue(updated.verified)
        assertEquals(1, manager.completed().size)
    }

    @Test
    fun `legitimate mechanism verification completes the reward`() = runBlocking {
        val manager = manager()
        val rec = manager.recordManualReward("App", "com.app", 1.0, "session-1")
        val updated = manager.verifyReward(rec.id, RewardSource.APP_LEGITIMATE_MECHANISM)
        assertEquals(RewardStatus.COMPLETED, updated!!.status)
        assertTrue(updated.verified)
    }

    @Test
    fun `manual verification stays unverified`() = runBlocking {
        val manager = manager()
        val rec = manager.recordManualReward("App", "com.app", 1.0, "session-1")
        val updated = manager.verifyReward(rec.id, RewardSource.MANUAL)
        assertEquals(RewardStatus.UNVERIFIED, updated!!.status)
        assertFalse(updated.verified)
    }

    @Test
    fun `no source verification fails the reward`() = runBlocking {
        val manager = manager()
        val rec = manager.recordManualReward("App", "com.app", 1.0, "session-1")
        val updated = manager.verifyReward(rec.id, RewardSource.NONE)
        assertEquals(RewardStatus.FAILED, updated!!.status)
        assertFalse(updated.verified)
        assertEquals(1, manager.failed().size)
    }

    @Test
    fun `verified reward cannot be recorded from a manual source`() = runBlocking {
        val manager = manager()
        var thrown: IllegalArgumentException? = null
        try {
            manager.recordVerifiedReward("App", "com.app", 1.0, "session-1", RewardSource.MANUAL)
        } catch (e: IllegalArgumentException) {
            thrown = e
        }
        assertNotNull(thrown)
        assertEquals(IllegalArgumentException::class.java, thrown!!.javaClass)
    }

    @Test
    fun `verified reward records as completed`() = runBlocking {
        val manager = manager()
        val rec = manager.recordVerifiedReward("App", "com.app", 2.0, "session-1", RewardSource.OFFICIAL_API)
        assertEquals(RewardStatus.COMPLETED, rec.status)
        assertTrue(rec.verified)
        assertEquals(1, manager.completed().size)
    }

    @Test
    fun `history is sorted by timestamp descending`() = runBlocking {
        val manager = manager()
        val clock = FakeClock()
        // Use a manager bound to a controllable clock.
        val m2 = RewardManager(InMemoryRewardRepository(), clock)
        m2.recordManualReward("A", "com.a", 1.0, "s1")
        clock.advance(1000)
        m2.recordManualReward("B", "com.b", 1.0, "s1")
        val history = m2.history()
        assertEquals(listOf("B", "A"), history.map { it.appName })
    }
}
