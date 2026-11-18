package com.smartai.adsmanager.appdetection

/** A discovered installed application (domain-level, no Android types). */
data class DiscoveredApp(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean = false,
)
