package com.smartai.adsmanager.domain

/**
 * Injectable time source so loading/timeout logic is deterministic and testable
 * without real wall-clock waiting.
 */
interface Clock {
    fun now(): Long
}

/** Production clock backed by the system wall clock. */
class SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
