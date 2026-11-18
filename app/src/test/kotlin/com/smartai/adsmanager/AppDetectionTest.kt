package com.smartai.adsmanager

import com.smartai.adsmanager.appdetection.AppFilter
import com.smartai.adsmanager.appdetection.DiscoveredApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDetectionTest {

    private val apps = listOf(
        DiscoveredApp("com.example.miningapp", "Mining Master"),
        DiscoveredApp("com.example.wallet", "Wallet Pro"),
        DiscoveredApp("com.example.game", "Candy Crush"),
        DiscoveredApp("com.example.browser", "Browser"),
    )

    @Test
    fun `discoverApps returns all apps when no query`() {
        val discovery = FakeAppDiscovery(apps)
        assertEquals(4, discovery.discoverApps(null).size)
        assertEquals(4, discovery.discoverApps("").size)
    }

    @Test
    fun `search filters by app name case-insensitively`() {
        val result = AppFilter.apply(apps, "mining")
        assertEquals(1, result.size)
        assertEquals("Mining Master", result[0].appName)
    }

    @Test
    fun `search filters by package name`() {
        val result = AppFilter.apply(apps, "com.example.wallet")
        assertEquals(1, result.size)
        assertEquals("Wallet Pro", result[0].appName)
    }

    @Test
    fun `results are sorted by name`() {
        val result = AppFilter.apply(apps, null)
        assertEquals(listOf("Browser", "Candy Crush", "Mining Master", "Wallet Pro"),
            result.map { it.appName })
    }

    @Test
    fun `no match returns empty list`() {
        val result = AppFilter.apply(apps, "nonexistent")
        assertTrue(result.isEmpty())
    }
}
