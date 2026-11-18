package com.smartai.adsmanager.domain

/**
 * Permission Manager policy (domain logic).
 *
 * Decides the minimum permissions required to start a monitoring session and checks
 * whether the user has granted them. The Android layer maps [PermissionType] to real
 * runtime permissions / MediaProjection consent.
 */
class PermissionPolicy {

    fun requiredForMonitoring(): List<PermissionType> =
        listOf(PermissionType.SCREEN_CAPTURE, PermissionType.POST_NOTIFICATIONS)

    fun missing(snapshot: PermissionSnapshot): List<PermissionType> =
        requiredForMonitoring().filter { !snapshot.isGranted(it) }

    fun canStartMonitoring(snapshot: PermissionSnapshot): Boolean =
        missing(snapshot).isEmpty()
}
