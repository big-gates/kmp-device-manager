package com.biggates.device.permission.location

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.biggates.device.PlatformContext
import com.biggates.device.permission.AndroidPermissionController
import com.biggates.device.permission.AppPermission
import com.biggates.device.permission.PermissionState
import com.biggates.device.permission.requestWithAutoRetryAndSettings

suspend fun AndroidPermissionController.requestLocationPermissionWithAutoRetryAndSettings(): PermissionState {
    val foregroundPermissions = arrayOf(
        AppPermission.LocationWhenInUse,
        AppPermission.LocationAlways,
    )

    val foregroundGranted = requestWithAutoRetryAndSettings(
        permissions = foregroundPermissions
    )
    if (!foregroundGranted) {
        return PermissionState.Denied(canAskAgain = true)
    }

    // 안드로이드 10(API 29)+ 에서만 백그라운드 권한 추가
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // 이미 허용되어 있으면 통과
        if (context.checkLocationAlwaysGranted()) return PermissionState.Granted

        val backgroundGranted = requestBackgroundWithAutoFlow()
        return if (backgroundGranted) PermissionState.Granted
        else PermissionState.Denied(canAskAgain = true)
    }

    // 안드로이드 9 이하: 포그라운드만 있으면 충분
    return PermissionState.Granted
}

/**
 * R(30)+에서는 ACCESS_BACKGROUND_LOCATION을 같은 다이얼로그에서 함께 허용시키기 어려움.
 * 정책상 설정 이동이 섞이는 경우가 흔하므로, 아래처럼 처리:
 * - 먼저 단독으로 백그라운드 권한 요청(가능한 장치에서는 바로 나옴)
 * - 여전히 미허용이면 설정 이동 → 복귀 후 재확인
 */
@RequiresApi(Build.VERSION_CODES.Q)
suspend fun AndroidPermissionController.requestBackgroundWithAutoFlow(): Boolean {
    if (context.checkLocationAlwaysGranted()) return true

    // 우선 다이얼로그 요청을 시도(일부 기기에서 바로 지원될 수 있음)
    val result = launchPermissions(arrayOf(AppPermission.LocationAlways))
    val granted = result[AppPermission.LocationAlways] == true || context.checkLocationAlwaysGranted()
    if (granted) return true

    // 그래도 불가 → 설정 이동 후 재확인
    openAppSettings()
    val re = recheckPermissions(arrayOf(AppPermission.LocationAlways))
    return re[AppPermission.LocationAlways] == true || context.checkLocationAlwaysGranted()
}
actual fun PlatformContext.checkLocationWhenInUseGranted(): Boolean {
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

actual fun PlatformContext.checkLocationAlwaysGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}