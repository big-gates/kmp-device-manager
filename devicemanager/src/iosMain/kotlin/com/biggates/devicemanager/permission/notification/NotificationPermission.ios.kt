package com.biggates.devicemanager.permission.notification

import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter

actual suspend fun PermissionController.requestNotificationsPermission(): PermissionState {
    val center = UNUserNotificationCenter.currentNotificationCenter()

    // 현재 설정 조회
    val settings = suspendCancellableCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { s ->
            cont.resume(s) { cause, _, _ -> }
        }
    }

    // 이미 허용 상태?
    when (settings?.authorizationStatus) {
        UNAuthorizationStatusAuthorized,
        UNAuthorizationStatusProvisional,
        UNAuthorizationStatusEphemeral -> return PermissionState.Granted
        UNAuthorizationStatusDenied -> return PermissionState.Denied(canAskAgain = false) // 사용자가 명시 거부
        else -> Unit // NotDetermined → 아래에서 요청
    }

    // 권한 요청(alert, sound, badge)
    val granted = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or
                    UNAuthorizationOptionSound or
                    UNAuthorizationOptionBadge
        ) { ok, _ ->
            cont.resume(ok) { cause, _, _ ->  }
        }
    }

    return if (granted) PermissionState.Granted
    else PermissionState.Denied(canAskAgain = true) // iOS는 보통 재요청 가능(설정 유도는 앱에서)
}