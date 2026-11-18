package com.smartai.adsmanager.domain

import com.smartai.adsmanager.appdetection.DiscoveredApp

/**
 * Connection Manager.
 *
 * Encapsulates connect / disconnect / select logic for applications and remembers the
 * user's selected application securely. The user must explicitly connect an app before
 * it can be used; nothing is accessed without consent.
 */
class ConnectionManager(
    private val repo: ConnectionRepository,
    private val clock: Clock,
    private val selection: SelectionStore,
) {

    suspend fun connect(app: DiscoveredApp): AppConnection {
        val connection = AppConnection(
            packageName = app.packageName,
            appName = app.appName,
            status = ConnectionStatus.CONNECTED,
            connectedAtEpochMs = clock.now(),
            permissionGranted = true,
        )
        repo.save(connection)
        selection.setSelectedPackage(app.packageName)
        return connection
    }

    suspend fun disconnect(packageName: String): Boolean {
        val existing = repo.getByPackage(packageName) ?: return false
        repo.delete(packageName)
        if (selection.getSelectedPackage() == packageName) {
            selection.setSelectedPackage(null)
        }
        return existing.status != ConnectionStatus.DISCONNECTED
    }

    suspend fun select(packageName: String) {
        selection.setSelectedPackage(packageName)
    }

    suspend fun all(): List<AppConnection> = repo.all()

    fun selectedPackage(): String? = selection.getSelectedPackage()

    suspend fun selected(): AppConnection? =
        selection.getSelectedPackage()?.let { repo.getByPackage(it) }

    suspend fun clearAll() {
        repo.clear()
        selection.setSelectedPackage(null)
    }
}
