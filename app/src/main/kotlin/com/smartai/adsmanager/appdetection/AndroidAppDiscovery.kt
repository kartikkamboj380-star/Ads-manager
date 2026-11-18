package com.smartai.adsmanager.appdetection

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Android implementation of [AppDiscovery].
 *
 * Uses [PackageManager] to enumerate installed applications. On Android 11+ this
 * respects the package-visibility mechanism: only apps covered by the manifest's
 * <queries> declarations (or the QUERY_ALL_PACKAGES permission) are visible. The app
 * never accesses an application without the user explicitly connecting it.
 */
class AndroidAppDiscovery(
    private val context: Context,
) : AppDiscovery {

    private val pm: PackageManager = context.packageManager

    override fun discoverApps(query: String?): List<DiscoveredApp> {
        // GET_META_DATA is only needed if we read labels/icons; we use it so the label
        // resolves reliably. Package visibility still governs which packages are returned.
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: SecurityException) {
            // Some OEMs restrict even with QUERY_ALL_PACKAGES; degrade gracefully.
            emptyList()
        }

        val mapped = installed.mapNotNull { info ->
            val label = info.loadLabel(pm).toString()
            // Skip apps that don't expose a usable label.
            if (label.isBlank()) return@mapNotNull null
            DiscoveredApp(
                packageName = info.packageName,
                appName = label,
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }
        return AppFilter.apply(mapped, query)
    }
}
