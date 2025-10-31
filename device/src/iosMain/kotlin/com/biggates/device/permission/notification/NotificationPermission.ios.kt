package com.biggates.device.permission.notification

import com.biggates.device.PlatformContext
import com.biggates.device.permission.IosPermissionController
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

suspend fun IosPermissionController.requestNotifications(): Boolean {
    if (context.checkNotificationsGranted()) return true
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val ok = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, _ -> cont.resume(granted) {} }
    }
    return ok
}

actual suspend fun PlatformContext.checkNotificationsGranted(): Boolean {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val settings = suspendCancellableCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { s -> cont.resume(s) }
    }
    return when (settings?.authorizationStatus) {
        UNAuthorizationStatusAuthorized,
        UNAuthorizationStatusProvisional,
        UNAuthorizationStatusEphemeral -> true

        else -> false
    }
}