package com.smartai.adsmanager.domain

import java.util.UUID

/**
 * Reward Manager.
 *
 * Only accepts rewards confirmed through an official supported API or the target
 * application's legitimate reward-confirmation mechanism. When no official integration
 * exists, manual confirmation is recorded and clearly labeled [RewardStatus.UNVERIFIED].
 * Rewards are never fabricated and another app's balance is never altered.
 */
class RewardManager(
    private val repo: RewardRepository,
    private val clock: Clock,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * Record a reward that the user manually confirmed. Because there is no official
     * integration, it is stored as [RewardStatus.UNVERIFIED] and never claimed as verified.
     */
    suspend fun recordManualReward(
        appName: String,
        packageName: String,
        amount: Double,
        sessionId: String,
        note: String = "Manually confirmed by the user; no official verification available.",
    ): RewardRecord {
        val record = RewardRecord(
            id = idGenerator(),
            appName = appName,
            packageName = packageName,
            amount = amount,
            status = RewardStatus.UNVERIFIED,
            timestampEpochMs = clock.now(),
            sessionId = sessionId,
            verified = false,
            source = RewardSource.MANUAL,
            note = note,
        )
        repo.insert(record)
        return record
    }

    /**
     * Record a reward that is already confirmed through a legitimate source.
     * [source] must be [RewardSource.OFFICIAL_API] or
     * [RewardSource.APP_LEGITIMATE_MECHANISM].
     */
    suspend fun recordVerifiedReward(
        appName: String,
        packageName: String,
        amount: Double,
        sessionId: String,
        source: RewardSource,
        note: String = "",
    ): RewardRecord {
        val verifiedSource = when (source) {
            RewardSource.OFFICIAL_API, RewardSource.APP_LEGITIMATE_MECHANISM -> source
            else -> throw IllegalArgumentException(
                "Verified rewards require OFFICIAL_API or APP_LEGITIMATE_MECHANISM source."
            )
        }
        val record = RewardRecord(
            id = idGenerator(),
            appName = appName,
            packageName = packageName,
            amount = amount,
            status = RewardStatus.COMPLETED,
            timestampEpochMs = clock.now(),
            sessionId = sessionId,
            verified = true,
            source = verifiedSource,
            note = note,
        )
        repo.insert(record)
        return record
    }

    /**
     * Verify an existing (pending/manual) record against a confirmation source.
     *
     * - OFFICIAL_API / APP_LEGITIMATE_MECHANISM -> [RewardStatus.COMPLETED], verified.
     * - MANUAL -> [RewardStatus.UNVERIFIED], not verified.
     * - NONE -> [RewardStatus.FAILED] (no way to confirm).
     *
     * Returns the updated record, or null if the record does not exist.
     */
    suspend fun verifyReward(recordId: String, source: RewardSource): RewardRecord? {
        val existing = repo.getById(recordId) ?: return null
        val updated = when (source) {
            RewardSource.OFFICIAL_API, RewardSource.APP_LEGITIMATE_MECHANISM ->
                existing.copy(
                    status = RewardStatus.COMPLETED,
                    verified = true,
                    source = source,
                    timestampEpochMs = clock.now(),
                )
            RewardSource.MANUAL ->
                existing.copy(
                    status = RewardStatus.UNVERIFIED,
                    verified = false,
                    source = RewardSource.MANUAL,
                    timestampEpochMs = clock.now(),
                )
            RewardSource.NONE ->
                existing.copy(
                    status = RewardStatus.FAILED,
                    verified = false,
                    source = RewardSource.NONE,
                    timestampEpochMs = clock.now(),
                    note = "No verification source was available for this record.",
                )
        }
        repo.update(updated)
        return updated
    }

    /** Mark a record as failed (e.g. confirmation error / timeout). */
    suspend fun markFailed(recordId: String, reason: String): RewardRecord? {
        val existing = repo.getById(recordId) ?: return null
        val updated = existing.copy(
            status = RewardStatus.FAILED,
            verified = false,
            timestampEpochMs = clock.now(),
            note = reason,
        )
        repo.update(updated)
        return updated
    }

    suspend fun history(): List<RewardRecord> =
        repo.getAll().sortedByDescending { it.timestampEpochMs }

    suspend fun pending(): List<RewardRecord> = repo.getByStatus(RewardStatus.PENDING)

    suspend fun completed(): List<RewardRecord> = repo.getByStatus(RewardStatus.COMPLETED)

    suspend fun failed(): List<RewardRecord> = repo.getByStatus(RewardStatus.FAILED)

    suspend fun unverified(): List<RewardRecord> = repo.getByStatus(RewardStatus.UNVERIFIED)

    suspend fun bySession(sessionId: String): List<RewardRecord> =
        repo.getBySession(sessionId).sortedBy { it.timestampEpochMs }
}
