package com.smartai.adsmanager.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartai.adsmanager.SmartAiAdsManagerApp
import com.smartai.adsmanager.appdetection.DiscoveredApp
import com.smartai.adsmanager.domain.AdSession
import com.smartai.adsmanager.domain.AppConnection
import com.smartai.adsmanager.domain.AssistanceStatus
import com.smartai.adsmanager.domain.ConnectionStatus
import com.smartai.adsmanager.domain.LoadingState
import com.smartai.adsmanager.domain.RewardRecord
import com.smartai.adsmanager.domain.RewardSource
import com.smartai.adsmanager.domain.ScreenAnalysis
import com.smartai.adsmanager.service.MonitoringController
import com.smartai.adsmanager.service.ScreenCaptureForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val connections: List<AppConnection> = emptyList(),
    val selectedPackage: String? = null,
    val selectedApp: AppConnection? = null,
    val assistanceStatus: AssistanceStatus =
        AssistanceStatus(LoadingState.IDLE, "Idle.", 0, false, false),
    val analysis: ScreenAnalysis? = null,
    val isMonitoring: Boolean = false,
    val session: AdSession? = null,
    val rewards: List<RewardRecord> = emptyList(),
    val awaitingCapture: Boolean = false,
    val aiMessage: String = "",
    val errorMessage: String? = null,
    val sessionDurationMs: Long = 0L,
)

/**
 * Central ViewModel. Bridges the domain managers and the [MonitoringController] singleton
 * to the UI, and exposes all user-facing actions.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SmartAiAdsManagerApp).container

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredApp>>(emptyList())
    val discovered: StateFlow<List<DiscoveredApp>> = _discovered.asStateFlow()

    private var rewardRecordedForCurrentLoad = false

    init {
        // Observe the live monitoring controller.
        viewModelScope.launch {
            MonitoringController.statusFlow.collect { status ->
                _ui.update { it.copy(assistanceStatus = status) }
                // Completion implies a legitimate verification signal was detected ->
                // record a verified reward exactly once per loading cycle.
                if (status.state == LoadingState.COMPLETED && !rewardRecordedForCurrentLoad) {
                    rewardRecordedForCurrentLoad = true
                    recordCompletedReward(status)
                }
            }
        }
        viewModelScope.launch {
            MonitoringController.analysisFlow.collect { analysis ->
                _ui.update { it.copy(analysis = analysis) }
            }
        }
        viewModelScope.launch {
            MonitoringController.runningFlow.collect { running ->
                _ui.update { it.copy(isMonitoring = running) }
            }
        }
        viewModelScope.launch {
            MonitoringController.errorFlow.collect { error ->
                _ui.update { it.copy(errorMessage = error ?: it.errorMessage) }
            }
        }
        // Session-duration ticker: updates the elapsed session time while monitoring.
        viewModelScope.launch {
            while (true) {
                val session = _ui.value.session
                if (session != null && _ui.value.isMonitoring) {
                    _ui.update {
                        it.copy(
                            sessionDurationMs =
                                (container.clock.now() - session.startedAtEpochMs).coerceAtLeast(0L),
                        )
                    }
                }
                delay(1000L)
            }
        }

        // Restart recovery: mark any interrupted session as failed for review.
        viewModelScope.launch {
            val interrupted = container.sessionManager.recover()
            if (interrupted != null) {
                _ui.update {
                    it.copy(
                        session = interrupted,
                        errorMessage = "A previous session was interrupted. You can start a new one.",
                    )
                }
            }
            refreshConnections()
            refreshRewards()
        }
    }

    // --- Connections -----------------------------------------------------------

    fun refreshConnections() {
        viewModelScope.launch {
            val connections = container.connectionManager.all()
            val selectedPackage = container.connectionManager.selectedPackage()
            _ui.update {
                it.copy(
                    connections = connections,
                    selectedPackage = selectedPackage,
                    selectedApp = connections.find { c -> c.packageName == selectedPackage },
                )
            }
        }
    }

    fun connectApp(app: DiscoveredApp) {
        viewModelScope.launch {
            container.connectionManager.connect(app)
            container.secureStore.setConsent(true)
            refreshConnections()
        }
    }

    fun disconnectApp(packageName: String) {
        viewModelScope.launch {
            container.connectionManager.disconnect(packageName)
            refreshConnections()
        }
    }

    fun selectApp(packageName: String) {
        viewModelScope.launch {
            container.connectionManager.select(packageName)
            refreshConnections()
        }
    }

    // --- Assistance / monitoring ----------------------------------------------

    /** User tapped Start. Signal the Activity to request MediaProjection consent. */
    fun requestAssistance() {
        val app = _ui.value.selectedApp
        if (app == null) {
            _ui.update { it.copy(errorMessage = "Select a connected application first.") }
            return
        }
        if (MonitoringController.isRunning) {
            _ui.update { it.copy(errorMessage = "Monitoring is already running.") }
            return
        }
        rewardRecordedForCurrentLoad = false
        // Enter the PERMISSION_REQUESTED state before launching the consent dialog.
        MonitoringController.onPermissionRequested()
        _ui.update { it.copy(awaitingCapture = true, errorMessage = null) }
    }

    /** Called by the Activity when the user denies (or cancels) screen-capture consent. */
    fun onCaptureDenied() {
        MonitoringController.onPermissionRevoked()
        _ui.update {
            it.copy(
                awaitingCapture = false,
                errorMessage = "Screen-capture permission was not granted. The app is still " +
                    "stable — grant permission and try Start again.",
            )
        }
    }

    /** Called by the Activity with the MediaProjection consent result. */
    fun onCaptureResult(resultCode: Int, data: Intent, packageName: String, appName: String) {
        viewModelScope.launch {
            val result = container.sessionManager.start(packageName, appName)
            if (result.type == com.smartai.adsmanager.domain.StartSessionResultType.DUPLICATE) {
                _ui.update { it.copy(errorMessage = result.message, awaitingCapture = false) }
                return@launch
            }
            _ui.update {
                it.copy(
                    session = container.sessionManager.getActive(),
                    awaitingCapture = false,
                    errorMessage = null,
                )
            }
            val intent = Intent(getApplication(), ScreenCaptureForegroundService::class.java).apply {
                action = ScreenCaptureForegroundService.ACTION_START
                putExtra(ScreenCaptureForegroundService.EXTRA_PROJECTION, data)
                putExtra(ScreenCaptureForegroundService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureForegroundService.EXTRA_PACKAGE_NAME, packageName)
            }
            // Started from the visible Activity (foreground), so startForegroundService is
            // permitted. The service calls startForeground() itself, first thing.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        }
    }

    fun userStartedLoading() {
        rewardRecordedForCurrentLoad = false
        MonitoringController.userStartedLoading()
    }

    fun requestRetry() {
        rewardRecordedForCurrentLoad = false
        MonitoringController.requestRetry()
    }

    fun stopAssistance() {
        MonitoringController.stop()
        viewModelScope.launch {
            _ui.value.session?.let { container.sessionManager.stop(it.id) }
            rewardRecordedForCurrentLoad = false
            _ui.update {
                it.copy(
                    isMonitoring = false,
                    session = null,
                    errorMessage = null,
                    sessionDurationMs = 0L,
                )
            }
            val intent = Intent(getApplication(), ScreenCaptureForegroundService::class.java).apply {
                action = ScreenCaptureForegroundService.ACTION_STOP
            }
            getApplication<Application>().stopService(intent)
            refreshRewards()
        }
    }

    // --- Rewards --------------------------------------------------------------

    fun manualConfirmReward(amount: Double) {
        val app = _ui.value.selectedApp ?: return
        val session = _ui.value.session ?: return
        viewModelScope.launch {
            container.rewardManager.recordManualReward(
                appName = app.appName,
                packageName = app.packageName,
                amount = amount,
                sessionId = session.id,
            )
            refreshRewards()
        }
    }

    private suspend fun recordCompletedReward(status: AssistanceStatus) {
        val app = _ui.value.selectedApp ?: return
        val session = _ui.value.session ?: return
        // COMPLETED is only reached via a legitimate verification signal, so the source
        // is the app's legitimate reward-confirmation mechanism.
        container.rewardManager.recordVerifiedReward(
            appName = app.appName,
            packageName = app.packageName,
            amount = 1.0,
            sessionId = session.id,
            source = RewardSource.APP_LEGITIMATE_MECHANISM,
            note = "Auto-recorded after a legitimate reward-confirmation signal was detected.",
        )
        refreshRewards()
    }

    fun refreshRewards() {
        viewModelScope.launch {
            _ui.update { it.copy(rewards = container.rewardManager.history()) }
        }
    }

    fun clearError() {
        MonitoringController.clearError()
        _ui.update { it.copy(errorMessage = null) }
    }

    // --- App discovery --------------------------------------------------------

    /** Discover installed applications (respects Android package visibility). */
    fun searchApps(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = container.appDiscovery.discoverApps(query)
            _discovered.update { apps }
        }
    }

    val aiCore = container.aiCore
    val safetySummary: String get() = aiCore.safetySummary()
}
