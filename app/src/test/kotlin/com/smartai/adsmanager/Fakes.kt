package com.smartai.adsmanager

import com.smartai.adsmanager.appdetection.AppDiscovery
import com.smartai.adsmanager.appdetection.DiscoveredApp
import com.smartai.adsmanager.domain.AdSession
import com.smartai.adsmanager.domain.AppConnection
import com.smartai.adsmanager.domain.Clock
import com.smartai.adsmanager.domain.ConnectionRepository
import com.smartai.adsmanager.domain.RewardRecord
import com.smartai.adsmanager.domain.RewardRepository
import com.smartai.adsmanager.domain.RewardStatus
import com.smartai.adsmanager.domain.ScreenAnalysis
import com.smartai.adsmanager.domain.SessionRepository
import com.smartai.adsmanager.domain.SelectionStore
import com.smartai.adsmanager.screenrecognition.ScreenRecognitionEngine

/** Deterministic clock for tests. */
class FakeClock(var time: Long = 0L) : Clock {
    override fun now(): Long = time
    fun advance(ms: Long) {
        time += ms
    }
}

class InMemoryRewardRepository : RewardRepository {
    private val map = mutableMapOf<String, RewardRecord>()
    override suspend fun insert(record: RewardRecord) { map[record.id] = record }
    override suspend fun update(record: RewardRecord) { map[record.id] = record }
    override suspend fun getById(id: String): RewardRecord? = map[id]
    override suspend fun getAll(): List<RewardRecord> = map.values.toList()
    override suspend fun getBySession(sessionId: String): List<RewardRecord> =
        map.values.filter { it.sessionId == sessionId }
    override suspend fun getByStatus(status: RewardStatus): List<RewardRecord> =
        map.values.filter { it.status == status }
}

class InMemorySessionRepository : SessionRepository {
    private val map = mutableMapOf<String, AdSession>()
    override suspend fun save(session: AdSession) { map[session.id] = session }
    override suspend fun getById(id: String): AdSession? = map[id]
    override suspend fun activeSession(): AdSession? =
        map.values.firstOrNull { it.status == com.smartai.adsmanager.domain.SessionStatus.ACTIVE }
    override suspend fun all(): List<AdSession> = map.values.toList()
    override suspend fun delete(id: String) { map.remove(id) }
}

class InMemoryConnectionRepository : ConnectionRepository {
    private val map = mutableMapOf<String, AppConnection>()
    override suspend fun save(connection: AppConnection) { map[connection.packageName] = connection }
    override suspend fun getByPackage(packageName: String): AppConnection? = map[packageName]
    override suspend fun all(): List<AppConnection> = map.values.toList()
    override suspend fun delete(packageName: String) { map.remove(packageName) }
    override suspend fun clear() { map.clear() }
}

class InMemorySelectionStore(var selected: String? = null) : SelectionStore {
    override fun getSelectedPackage(): String? = selected
    override fun setSelectedPackage(packageName: String?) { selected = packageName }
}

class FakeAppDiscovery(private val apps: List<DiscoveredApp> = emptyList()) : AppDiscovery {
    override fun discoverApps(query: String?): List<DiscoveredApp> =
        com.smartai.adsmanager.appdetection.AppFilter.apply(apps, query)
}

/** Screen recognition engine that returns a fixed analysis (mock OCR screen, no real ads). */
class FakeScreenRecognitionEngine(
    private val text: String = "",
) : ScreenRecognitionEngine {
    override suspend fun recognizeFromBitmap(bitmap: android.graphics.Bitmap?): ScreenAnalysis =
        recognizeFromText(text)
    override fun recognizeFromText(text: String): ScreenAnalysis =
        com.smartai.adsmanager.domain.MiningScreenMatcher().analyze(text).copy(recognizedText = text)
}
