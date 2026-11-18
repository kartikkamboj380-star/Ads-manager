package com.smartai.adsmanager.domain

/**
 * Repository ports for the domain managers. The Android (Room) implementations live in
 * the `data` layer and satisfy these interfaces, keeping the core logic testable with
 * in-memory fakes.
 *
 * Methods are `suspend` because persistence runs on a background thread.
 */

interface RewardRepository {
    suspend fun insert(record: RewardRecord)
    suspend fun update(record: RewardRecord)
    suspend fun getById(id: String): RewardRecord?
    suspend fun getAll(): List<RewardRecord>
    suspend fun getBySession(sessionId: String): List<RewardRecord>
    suspend fun getByStatus(status: RewardStatus): List<RewardRecord>
}

interface SessionRepository {
    suspend fun save(session: AdSession)
    suspend fun getById(id: String): AdSession?
    suspend fun activeSession(): AdSession?
    suspend fun all(): List<AdSession>
    suspend fun delete(id: String)
}

interface ConnectionRepository {
    suspend fun save(connection: AppConnection)
    suspend fun getByPackage(packageName: String): AppConnection?
    suspend fun all(): List<AppConnection>
    suspend fun delete(packageName: String)
    suspend fun clear()
}

/** Secure storage for the currently-selected application package name. */
interface SelectionStore {
    fun getSelectedPackage(): String?
    fun setSelectedPackage(packageName: String?)
}
