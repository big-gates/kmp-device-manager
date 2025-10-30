package com.biggates.devicemanager.permission.notification

import android.Manifest
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.biggates.devicemanager.permission.AndroidPermissionController
import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.permission.requestWithAutoRetryAndSettings

actual suspend fun PermissionController.requestNotificationsPermission(): PermissionState {
    this as AndroidPermissionController

    // Android 13 미만은 런타임 퍼미션 없음 → 시스템 설정에서 차단했는지만 확인
    if (Build.VERSION.SDK_INT < 33) {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return if (enabled) PermissionState.Granted
        else PermissionState.Denied(canAskAgain = true)
    }

    // Android 13+ : POST_NOTIFICATIONS
    val alreadyEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (alreadyEnabled) return PermissionState.Granted

    val result = launchPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    val granted = result[Manifest.permission.POST_NOTIFICATIONS] == true
    if (granted) return PermissionState.Granted

    // 거부됨: 영구거부 여부 판단
    val permanentlyDenied = !shouldShowRationale(Manifest.permission.POST_NOTIFICATIONS)
    return PermissionState.Denied(canAskAgain = !permanentlyDenied)
}

suspend fun AndroidPermissionController.requestNotificationsPermissionWithAutoRetryAndSettings(): PermissionState {

    // Android 12L 이하: 런타임 권한 없음 → 시스템 알림 차단 여부만 확인
    if (Build.VERSION.SDK_INT < 33) {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (enabled) return PermissionState.Granted

        // 사용자가 신경 안쓰도록: 설정 열고 돌아오면 재확인
        openAppSettings()
        // recheck: POST_NOTIFICATIONS가 없으므로 NotificationManagerCompat로 다시 확인
        val reEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return if (reEnabled) PermissionState.Granted
        else PermissionState.Denied(canAskAgain = true)
    }

    // Android 13+: POST_NOTIFICATIONS 런타임 권한
    val alreadyEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    if (alreadyEnabled) return PermissionState.Granted

    val granted = requestWithAutoRetryAndSettings(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    return if (granted) PermissionState.Granted
    else {
        // 영구거부 여부에 따라 canAskAgain 설정
        val permanentlyDenied = !shouldShowRationale(Manifest.permission.POST_NOTIFICATIONS)
        PermissionState.Denied(canAskAgain = !permanentlyDenied)
    }
}