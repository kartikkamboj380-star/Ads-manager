package com.smartai.adsmanager.data

import android.content.Context
import com.smartai.adsmanager.appdetection.AndroidAppDiscovery
import com.smartai.adsmanager.appdetection.AppDiscovery
import com.smartai.adsmanager.domain.AiCore
import com.smartai.adsmanager.domain.Clock
import com.smartai.adsmanager.domain.ConnectionManager
import com.smartai.adsmanager.domain.ConnectionRepository
import com.smartai.adsmanager.domain.ErrorRecoveryManager
import com.smartai.adsmanager.domain.MiningScreenMatcher
import com.smartai.adsmanager.domain.PermissionPolicy
import com.smartai.adsmanager.domain.RewardManager
import com.smartai.adsmanager.domain.RewardRepository
import com.smartai.adsmanager.domain.SessionManager
import com.smartai.adsmanager.domain.SessionRepository
import com.smartai.adsmanager.domain.SystemClock

/**
 * Manual dependency container. Holds the single instances of the database, repositories,
 * domain managers and engines for the whole app. Created once in the Application class.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase = AppDatabase.get(appContext)

    val rewardRepository: RewardRepository = RoomRewardRepository(database.rewardDao())
    val sessionRepository: SessionRepository = RoomSessionRepository(database.sessionDao())
    val connectionRepository: ConnectionRepository =
        RoomConnectionRepository(database.connectedAppDao())

    val secureStore: SecurePreferencesStore = SecurePreferencesStore(appContext)

    val clock: Clock = SystemClock()

    val appDiscovery: AppDiscovery = AndroidAppDiscovery(appContext)

    private val matcher = MiningScreenMatcher()

    val aiCore: AiCore = AiCore(matcher)
    val errorRecovery: ErrorRecoveryManager = ErrorRecoveryManager()
    val permissionPolicy: PermissionPolicy = PermissionPolicy()

    val rewardManager: RewardManager = RewardManager(rewardRepository, clock)
    val sessionManager: SessionManager = SessionManager(sessionRepository, clock)

    val connectionManager: ConnectionManager =
        ConnectionManager(connectionRepository, clock, secureStore)
}
