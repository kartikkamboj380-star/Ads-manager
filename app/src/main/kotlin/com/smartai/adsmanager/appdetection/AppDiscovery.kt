package com.smartai.adsmanager.appdetection

/** Abstraction over installed-application discovery. Android impl + test fakes. */
interface AppDiscovery {
    /** Return installed apps visible to this app, optionally filtered by [query]. */
    fun discoverApps(query: String? = null): List<DiscoveredApp>
}
