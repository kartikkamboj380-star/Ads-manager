package com.smartai.adsmanager.appdetection

/**
 * Pure search/filter logic for discovered apps (no Android dependencies), so it is
 * unit-testable. Matches by app name (case-insensitive, contains) and sorts by name.
 */
object AppFilter {

    fun apply(apps: List<DiscoveredApp>, query: String?): List<DiscoveredApp> {
        val q = query?.trim()?.lowercase().orEmpty()
        return apps
            .filter { app ->
                q.isEmpty() || app.appName.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q)
            }
            .sortedBy { it.appName.lowercase() }
    }
}
