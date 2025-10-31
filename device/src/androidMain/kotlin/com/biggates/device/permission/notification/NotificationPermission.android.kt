package com.biggates.device.permission.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.biggates.device.PlatformContext
import com.biggates.device.permission.AndroidPermissionController
import com.biggates.device.permission.AppPermission
import com.biggates.device.permission.PermissionState
import com.biggates.device.permission.requestWithAutoRetryAndSettings

suspend fun AndroidPermissionController.requestNotificationsPermissionWithAutoRetryAndSettings(): PermissionState {
    // 1) 빠른 패스: 이미 모두 충족(알림 토글 + POST_NOTIFICATIONS) → 끝
    if (context.checkNotificationsGranted()) return PermissionState.Granted

    // 2) Android 12L 이하: 런타임 권한 없음 → 설정으로 유도 후 재확인
    if (Build.VERSION.SDK_INT < 33) {
        openAppSettings() // 사용자가 돌아오면 바로 재확인
        return if (context.checkNotificationsGranted()) {
            PermissionState.Granted
        } else {
            // 런타임 권한 개념이 없고, 설정 토글만 문제이므로 언제든 다시 물을 수 있음
            PermissionState.Denied(canAskAgain = true)
        }
    }

    // 3) Android 13+: 런타임 권한 요청 → 실패 시 설정으로 유도 후 최종 재확인
    val grantedByPrompt = requestWithAutoRetryAndSettings(arrayOf(AppPermission.Notifications))
    if (grantedByPrompt && context.checkNotificationsGranted()) {
        return PermissionState.Granted
    }

    // 여기 도달했다면: (a) 런타임 권한 거부이거나 (b) 시스템 알림 토글이 꺼져 있는 경우
    // 설정 화면 한 번 더 열어 사용자가 토글/권한을 직접 정정할 기회를 준다.
    openAppSettings()
    if (context.checkNotificationsGranted()) return PermissionState.Granted

    // 영구 거부 여부에 따라 canAskAgain 결정
    val permanentlyDenied = !shouldShowRationale(AppPermission.Notifications)
    return PermissionState.Denied(canAskAgain = !permanentlyDenied)
}

actual suspend fun PlatformContext.checkNotificationsGranted(): Boolean {
    // 시스템에서 앱 알림 허용(토글)이 꺼져 있으면 무조건 false
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false

    // Android 13+는 POST_NOTIFICATIONS 런타임 권한도 있어야 함
    if (Build.VERSION.SDK_INT >= 33) {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }
    return true
}