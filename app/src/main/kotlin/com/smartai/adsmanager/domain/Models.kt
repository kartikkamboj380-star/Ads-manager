package com.smartai.adsmanager.domain

/**
 * Domain models shared across the AI core, engines and managers.
 *
 * These are pure Kotlin (no Android dependencies) so the core logic is unit-testable
 * on the JVM without a device or the Android framework.
 */

// ---------------------------------------------------------------------------
// Reward model
// ---------------------------------------------------------------------------

enum class RewardStatus {
    /** Reward is recorded but not yet confirmed. */
    PENDING,

    /** Confirmed through an official / legitimate mechanism. */
    COMPLETED,

    /** Confirmation failed or could not be obtained. */
    FAILED,

    /** Recorded via manual confirmation only; not verified. */
    UNVERIFIED,
}

enum class RewardSource {
    /** Confirmed via a supported official reward API. */
    OFFICIAL_API,

    /** Confirmed via the target app's own legitimate reward confirmation mechanism. */
    APP_LEGITIMATE_MECHANISM,

    /** Manual user confirmation only (no official integration available). */
    MANUAL,

    /** No source available / not confirmed. */
    NONE,
}

data class RewardRecord(
    val id: String,
    val appName: String,
    val packageName: String,
    val amount: Double,
    val status: RewardStatus,
    val timestampEpochMs: Long,
    val sessionId: String,
    val verified: Boolean,
    val source: RewardSource,
    val note: String = "",
)

// ---------------------------------------------------------------------------
// Connection / session model
// ---------------------------------------------------------------------------

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
}

enum class SessionStatus {
    INACTIVE,
    STARTING,
    ACTIVE,
    STOPPING,
    STOPPED,
    FAILED,
}

data class AppConnection(
    val packageName: String,
    val appName: String,
    val appIconUri: String? = null,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectedAtEpochMs: Long = 0L,
    val permissionGranted: Boolean = false,
)

data class AdSession(
    val id: String,
    val packageName: String,
    val appName: String,
    val status: SessionStatus,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long = 0L,
    val lastState: LoadingState = LoadingState.IDLE,
    val rewardIds: List<String> = emptyList(),
) {
    val isActive: Boolean
        get() = status == SessionStatus.ACTIVE || status == SessionStatus.STARTING
}

// ---------------------------------------------------------------------------
// Screen recognition model
// ---------------------------------------------------------------------------

/**
 * The exact options present on the target app's Mining screen.
 *
 * Only [LOADING_AD] is ever a permitted target. The matcher and AI core must never
 * direct the user toward any other option.
 */
enum class TargetScreenOption {
    ACTIVE,
    PLAY_FOR_REWARDS,
    LOADING_AD,
    SHOW_OFFERS,
    SUBSCRIBE,
}

enum class PermissionType {
    SCREEN_CAPTURE,
    POST_NOTIFICATIONS,
}

/**
 * A single visually-detected screen element (a text block / button-like region found by
 * OCR + geometry analysis). Used by the live monitor to show exactly what the agent is
 * seeing and where elements are located on the screen.
 *
 * The agent only *observes* these elements — it never taps them automatically.
 */
data class DetectedElement(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** Heuristic: the element looks like a tappable button (compact, short text). */
    val isButtonLike: Boolean = false,
    /** Recognition confidence for this element in [0.0, 1.0]. */
    val confidence: Double = 0.0,
)

/** Category of an agent activity-log entry (shown in the live monitor). */
enum class AgentEventType { INFO, DETECTION, ACTION, GUIDANCE, ERROR }

/** A single entry in the agent's live activity log. */
data class AgentEvent(
    val timestampMs: Long,
    val type: AgentEventType,
    val message: String,
)

data class ScreenAnalysis(
    val recognized: Boolean,
    val matchedScreen: String?,
    val detectedOptions: Set<TargetScreenOption>,
    val targetButtonAvailable: Boolean,
    val loadingIndicatorVisible: Boolean,
    val loadingElapsedMs: Long,
    val error: String? = null,
    val recognizedText: String = "",
    /**
     * Confidence score in [0.0, 1.0] for the recognition result. Low-confidence results
     * must never drive a decision; the agent asks the user for help instead.
     */
    val confidence: Double = 0.0,
    // --- Live pipeline diagnostics (so the UI can show the agent is actually working) ---
    /** Number of frames the agent has captured/processed so far in this session. */
    val framesProcessed: Int = 0,
    /** True when the most recent OCR attempt produced usable text. */
    val ocrSucceeded: Boolean = false,
    /** Latest OCR/capture error, if the pipeline is not producing usable frames. */
    val pipelineError: String? = null,
    // --- Visual element detection (live monitor + smarter recognition) ---
    /** All text/button-like elements detected on the current frame, with their bounds. */
    val detectedElements: List<DetectedElement> = emptyList(),
    /** The best-matching element for the Loading ad control, if detected. */
    val targetElement: DetectedElement? = null,
    /** General screen classification label (e.g. "Mining", "Loading", "Unknown"). */
    val screenClassification: String = "Unknown",
)

/** The state of the underlying screen-capture pipeline (independent of the loading flow). */
enum class CaptureStatus {
    INACTIVE,
    INITIALIZING,
    ACTIVE,
    STOPPED,
    REVOKED,
    ERROR,
}

data class AssistanceStatus(
    val state: LoadingState,
    val message: String,
    val elapsedMs: Long,
    val targetButtonAvailable: Boolean,
    val loadingIndicatorVisible: Boolean,
    val timeoutWarning: Boolean = false,
    /** Whether the foreground capture service is actively producing frames. */
    val captureActive: Boolean = false,
    /** Whether the AI agent / monitoring loop is running. */
    val agentRunning: Boolean = false,
    /** Epoch-ms of the last successfully processed frame, or 0 if none yet. */
    val lastFrameTimeEpochMs: Long = 0L,
    /** The capture pipeline status. */
    val captureStatus: CaptureStatus = CaptureStatus.INACTIVE,
    /** The most recent error message, if any. */
    val lastError: String? = null,
)

// ---------------------------------------------------------------------------
// Verification signal (completion must be proven, never assumed from a delay)
// ---------------------------------------------------------------------------

enum class VerificationSource {
    OFFICIAL_API,
    APP_LEGITIMATE_MECHANISM,
    MANUAL,
    NONE,
}

data class VerificationSignal(
    val detected: Boolean,
    val source: VerificationSource,
    val message: String,
)

// ---------------------------------------------------------------------------
// Error recovery
// ---------------------------------------------------------------------------

enum class ErrorCategory {
    TIMEOUT,
    VERIFICATION_FAILED,
    SCREEN_NOT_FOUND,
    TARGET_MISSING,
    LOADING_ERROR,
    UNKNOWN,
}

enum class RecoveryAction {
    RETRY,
    STOP,
    MONITOR,
    NONE,
}

data class RecoveryPlan(
    val action: RecoveryAction,
    val message: String,
)

// ---------------------------------------------------------------------------
// Permission
// ---------------------------------------------------------------------------

data class PermissionRequirement(
    val type: PermissionType,
    val granted: Boolean,
)

data class PermissionSnapshot(
    val granted: Set<PermissionType>,
) {
    fun isGranted(type: PermissionType): Boolean = type in granted
}
