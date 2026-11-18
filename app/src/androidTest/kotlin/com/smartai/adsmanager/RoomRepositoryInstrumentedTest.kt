package com.smartai.adsmanager

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartai.adsmanager.data.AppDatabase
import com.smartai.adsmanager.data.RoomConnectionRepository
import com.smartai.adsmanager.data.RoomRewardRepository
import com.smartai.adsmanager.data.RoomSessionRepository
import com.smartai.adsmanager.domain.ConnectionStatus
import com.smartai.adsmanager.domain.RewardSource
import com.smartai.adsmanager.domain.RewardStatus
import com.smartai.adsmanager.domain.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the Room-backed repositories. These run on a device/emulator
 * (or Robolectric) and verify real persistence round-trips.
 */
@RunWith(AndroidJUnit4::class)
class RoomRepositoryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var rewardRepo: RoomRewardRepository
    private lateinit var sessionRepo: RoomSessionRepository
    private lateinit var connectionRepo: RoomConnectionRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rewardRepo = RoomRewardRepository(db.rewardDao())
        sessionRepo = RoomSessionRepository(db.sessionDao())
        connectionRepo = RoomConnectionRepository(db.connectedAppDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `reward repository round trip`() = runBlocking {
        val record = com.smartai.adsmanager.domain.RewardRecord(
            id = "r1", appName = "App", packageName = "com.app", amount = 1.0,
            status = RewardStatus.COMPLETED, timestampEpochMs = 1000L, sessionId = "s1",
            verified = true, source = RewardSource.OFFICIAL_API, note = "ok",
        )
        rewardRepo.insert(record)
        assertEquals(1, rewardRepo.getAll().size)
        assertNotNull(rewardRepo.getById("r1"))
        assertEquals(1, rewardRepo.getByStatus(RewardStatus.COMPLETED).size)
        assertEquals(1, rewardRepo.getBySession("s1").size)
    }

    @Test
    fun `session repository round trip and active query`() = runBlocking {
        val session = com.smartai.adsmanager.domain.AdSession(
            id = "s1", packageName = "com.app", appName = "App",
            status = SessionStatus.ACTIVE, startedAtEpochMs = 1000L,
        )
        sessionRepo.save(session)
        assertNotNull(sessionRepo.getById("s1"))
        assertNotNull(sessionRepo.activeSession())
        assertEquals("s1", sessionRepo.activeSession()!!.id)
    }

    @Test
    fun `connection repository round trip`() = runBlocking {
        val connection = com.smartai.adsmanager.domain.AppConnection(
            packageName = "com.app", appName = "App", status = ConnectionStatus.CONNECTED,
            connectedAtEpochMs = 1000L, permissionGranted = true,
        )
        connectionRepo.save(connection)
        assertEquals(1, connectionRepo.all().size)
        assertNotNull(connectionRepo.getByPackage("com.app"))
        connectionRepo.delete("com.app")
        assertNull(connectionRepo.getByPackage("com.app"))
    }
}
