package com.smartai.adsmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Room entity backing reward records. */
@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey val id: String,
    val appName: String,
    val packageName: String,
    val amount: Double,
    val status: String,
    val timestampEpochMs: Long,
    val sessionId: String,
    val verified: Boolean,
    val source: String,
    val note: String,
)

/** Room entity backing ad-assistance sessions. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val status: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val lastState: String,
    val rewardIds: String, // comma-separated ids
)

/** Room entity backing connected applications. */
@Entity(tableName = "connected_apps")
data class ConnectedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val appIconUri: String?,
    val status: String,
    val connectedAtEpochMs: Long,
    val permissionGranted: Boolean,
)
