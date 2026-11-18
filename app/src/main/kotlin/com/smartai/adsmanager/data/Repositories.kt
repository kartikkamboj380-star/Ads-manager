package com.smartai.adsmanager.data

import com.smartai.adsmanager.data.dao.ConnectedAppDao
import com.smartai.adsmanager.data.dao.RewardDao
import com.smartai.adsmanager.data.dao.SessionDao
import com.smartai.adsmanager.data.entity.ConnectedAppEntity
import com.smartai.adsmanager.data.entity.RewardEntity
import com.smartai.adsmanager.data.entity.SessionEntity
import com.smartai.adsmanager.domain.AdSession
import com.smartai.adsmanager.domain.AppConnection
import com.smartai.adsmanager.domain.ConnectionStatus
import com.smartai.adsmanager.domain.ConnectionRepository
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.RewardRecord
import com.smartai.adsmanager.domain.RewardRepository
import com.smartai.adsmanager.domain.RewardSource
import com.smartai.adsmanager.domain.RewardStatus
import com.smartai.adsmanager.domain.SessionRepository
import com.smartai.adsmanager.domain.SessionStatus

/** Room-backed implementation of [RewardRepository]. */
class RoomRewardRepository(private val dao: RewardDao) : RewardRepository {

    override suspend fun insert(record: RewardRecord) {
        dao.insert(record.toEntity())
    }

    override suspend fun update(record: RewardRecord) {
        dao.update(record.toEntity())
    }

    override suspend fun getById(id: String): RewardRecord? =
        dao.getById(id)?.toDomain()

    override suspend fun getAll(): List<RewardRecord> =
        dao.getAll().mapNotNull { it.toDomain() }

    override suspend fun getBySession(sessionId: String): List<RewardRecord> =
        dao.getBySession(sessionId).mapNotNull { it.toDomain() }

    override suspend fun getByStatus(status: RewardStatus): List<RewardRecord> =
        dao.getByStatus(status.name).mapNotNull { it.toDomain() }

    private fun RewardRecord.toEntity() = RewardEntity(
        id = id,
        appName = appName,
        packageName = packageName,
        amount = amount,
        status = status.name,
        timestampEpochMs = timestampEpochMs,
        sessionId = sessionId,
        verified = verified,
        source = source.name,
        note = note,
    )

    private fun RewardEntity.toDomain() = RewardRecord(
        id = id,
        appName = appName,
        packageName = packageName,
        amount = amount,
        status = runCatching { RewardStatus.valueOf(status) }.getOrDefault(RewardStatus.UNVERIFIED),
        timestampEpochMs = timestampEpochMs,
        sessionId = sessionId,
        verified = verified,
        source = runCatching { RewardSource.valueOf(source) }.getOrDefault(RewardSource.NONE),
        note = note,
    )
}

/** Room-backed implementation of [SessionRepository]. */
class RoomSessionRepository(private val dao: SessionDao) : SessionRepository {

    override suspend fun save(session: AdSession) {
        // Upsert: insert if new, update if exists.
        dao.getById(session.id)?.let { dao.update(session.toEntity()) } ?: dao.insert(session.toEntity())
    }

    override suspend fun getById(id: String): AdSession? =
        dao.getById(id)?.toDomain()

    override suspend fun activeSession(): AdSession? =
        dao.activeSession()?.toDomain()

    override suspend fun all(): List<AdSession> =
        dao.all().mapNotNull { it.toDomain() }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    private fun AdSession.toEntity() = SessionEntity(
        id = id,
        packageName = packageName,
        appName = appName,
        status = status.name,
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = endedAtEpochMs,
        lastState = lastState.name,
        rewardIds = rewardIds.joinToString(","),
    )

    private fun SessionEntity.toDomain() = AdSession(
        id = id,
        packageName = packageName,
        appName = appName,
        status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.FAILED),
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = endedAtEpochMs,
        lastState = runCatching { LoadingState.valueOf(lastState) }.getOrDefault(LoadingState.IDLE),
        rewardIds = rewardIds.split(",").filter { it.isNotBlank() },
    )
}

/** Room-backed implementation of [ConnectionRepository]. */
class RoomConnectionRepository(private val dao: ConnectedAppDao) : ConnectionRepository {

    override suspend fun save(connection: AppConnection) {
        dao.insert(connection.toEntity())
    }

    override suspend fun getByPackage(packageName: String): AppConnection? =
        dao.getByPackage(packageName)?.toDomain()

    override suspend fun all(): List<AppConnection> =
        dao.all().mapNotNull { it.toDomain() }

    override suspend fun delete(packageName: String) {
        dao.delete(packageName)
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun AppConnection.toEntity() = ConnectedAppEntity(
        packageName = packageName,
        appName = appName,
        appIconUri = appIconUri,
        status = status.name,
        connectedAtEpochMs = connectedAtEpochMs,
        permissionGranted = permissionGranted,
    )

    private fun ConnectedAppEntity.toDomain() = AppConnection(
        packageName = packageName,
        appName = appName,
        appIconUri = appIconUri,
        status = runCatching { ConnectionStatus.valueOf(status) }.getOrDefault(ConnectionStatus.DISCONNECTED),
        connectedAtEpochMs = connectedAtEpochMs,
        permissionGranted = permissionGranted,
    )
}
