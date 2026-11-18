package com.smartai.adsmanager

import com.smartai.adsmanager.appdetection.DiscoveredApp
import com.smartai.adsmanager.domain.ConnectionManager
import com.smartai.adsmanager.domain.ConnectionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionManagerTest {

    private fun manager(): Pair<ConnectionManager, InMemorySelectionStore> {
        val store = InMemorySelectionStore()
        return ConnectionManager(InMemoryConnectionRepository(), FakeClock(), store) to store
    }

    @Test
    fun `connect saves connection and selects the app`() = runBlocking {
        val (m, store) = manager()
        m.connect(DiscoveredApp("com.app", "App"))
        assertEquals(1, m.all().size)
        assertEquals("com.app", m.selectedPackage())
        assertEquals(ConnectionStatus.CONNECTED, m.selected()!!.status)
        assertEquals("com.app", store.selected)
    }

    @Test
    fun `disconnect removes connection and clears selection if selected`() = runBlocking {
        val (m, _) = manager()
        m.connect(DiscoveredApp("com.app", "App"))
        m.disconnect("com.app")
        assertTrue(m.all().isEmpty())
        assertNull(m.selectedPackage())
    }

    @Test
    fun `disconnect of non-connected app returns false`() = runBlocking {
        val (m, _) = manager()
        assertTrue(m.disconnect("com.nope").not())
    }

    @Test
    fun `select changes the selected app`() = runBlocking {
        val (m, _) = manager()
        m.connect(DiscoveredApp("com.a", "A"))
        m.connect(DiscoveredApp("com.b", "B"))
        m.select("com.b")
        assertEquals("com.b", m.selectedPackage())
        assertEquals("B", m.selected()!!.appName)
    }

    @Test
    fun `disconnect keeps selection when another app is selected`() = runBlocking {
        val (m, _) = manager()
        m.connect(DiscoveredApp("com.a", "A"))
        m.connect(DiscoveredApp("com.b", "B"))
        m.select("com.b")
        m.disconnect("com.a")
        // com.b was selected, so selection remains.
        assertEquals("com.b", m.selectedPackage())
        assertNotNull(m.selected())
    }

    @Test
    fun `clearAll removes everything`() = runBlocking {
        val (m, _) = manager()
        m.connect(DiscoveredApp("com.a", "A"))
        m.clearAll()
        assertTrue(m.all().isEmpty())
        assertNull(m.selectedPackage())
    }
}
