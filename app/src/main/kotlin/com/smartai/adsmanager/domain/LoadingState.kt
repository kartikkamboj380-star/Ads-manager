package com.smartai.adsmanager.domain

/**
 * The complete ad-loading + capture state machine states required by the spec.
 *
 * The machine is forward-only and guarded: illegal or duplicate transitions are
 * rejected (never silently applied) so the UI always reflects a real, validated state.
 *
 * The states cover the full lifecycle: permission request → grant → capture
 * initialization → active capture → target app/screen detection → the loading flow,
 * plus terminal/error states (capture stopped, permission revoked, timeout, failed).
 */
enum class LoadingState {
    IDLE,

    // --- Permission / capture lifecycle -------------------------------------
    PERMISSION_REQUESTED,
    PERMISSION_GRANTED,
    CAPTURE_INITIALIZING,
    CAPTURE_ACTIVE,
    TARGET_APP_DETECTED,
    TARGET_SCREEN_DETECTED,

    // --- Recognition / loading flow ---------------------------------------
    SCREEN_DETECTED,
    TARGET_BUTTON_AVAILABLE,
    USER_ACTION_REQUIRED,
    LOADING,
    WAITING_FOR_VERIFICATION,
    COMPLETED,

    // --- Terminal / error states ------------------------------------------
    FAILED,
    TIMEOUT,
    CAPTURE_STOPPED,
    PERMISSION_REVOKED,
    STOPPED,
}
