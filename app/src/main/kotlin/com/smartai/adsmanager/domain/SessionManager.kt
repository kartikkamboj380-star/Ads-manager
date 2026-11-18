package com.smartai.adsmanager.domain

import java.util.UUID

enum class StartSessionResultType { STARTED, DUPLICATE }

data class StartSessionResult(
    val type: StartSessionResultType,
    val sessionId: String,
    val message: String,
)

/**
 * Session Manager.
 *
 * Guarantees a single active ad-assistance session per app at a time (duplicate-session
 * prevention) and supports restart recovery: if the process was killed while a session
 * was active, [recover] surfaces the interrupted session for the user to review rather
 * than silently resuming or falsely claiming an active session.
 */
class SessionManager(
    private val repo: SessionRepository,
    private val clock: Clock,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private var currentSessionId: String? = null

    suspend fun start(packageName: String, appName: String): StartSessionResult {
        // Duplicate-session prevention: refuse to start if one is already active.
        val activeId = currentSessionId ?: repo.activeSession()?.id
        if (activeId != null) {
            return StartSessionResult(
                type = StartSessionResultType.DUPLICATE,
                sessionId = activeId,
                message = "An active ad-assistance session already exists. Stop it before starting a new one.",
            )
        }
        val id = idGenerator()
        val session = AdSession(
            id = id,
            packageName = packageName,
            appName = appName,
            status = SessionStatus.ACTIVE,
            startedAtEpochMs = clock.now(),
        )
        repo.save(session)
        currentSessionId = id
        return StartSessionResult(
            type = StartSessionResultType.STARTED,
            sessionId = id,
            message = "Ad-assistance session started.",
        )
    }

    suspend fun stop(sessionId: String): AdSession? {
        val session = repo.getById(sessionId) ?: return null
        val ended = session.copy(
            status = SessionStatus.STOPPED,
            endedAtEpochMs = clock.now(),
        )
        repo.save(ended)
        if (currentSessionId == sessionId) currentSessionId = null
        return ended
    }

    suspend fun fail(sessionId: String): AdSession? {
        val session = repo.getById(sessionId) ?: return null
        val failed = session.copy(
            status = SessionStatus.FAILED,
            endedAtEpochMs = clock.now(),
        )
        repo.save(failed)
        if (currentSessionId == sessionId) currentSessionId = null
        return failed
    }

    suspend fun getActive(): AdSession? =
        currentSessionId?.let { repo.getById(it) } ?: repo.activeSession()

    /**
     * Restart recovery: a session persisted as ACTIVE but no longer actually running is
     * marked FAILED (interrupted) and returned for the user to review/resume.
     */
    suspend fun recover(): AdSession? {
        val active = repo.activeSession() ?: return null
        val interrupted = active.copy(
            status = SessionStatus.FAILED,
            endedAtEpochMs = clock.now(),
        )
        repo.save(interrupted)
        currentSessionId = null
        return interrupted
    }

    suspend fun hasActiveSession(): Boolean = getActive()?.isActive == true
}
