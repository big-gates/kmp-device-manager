package com.biggates.device.permission.location

import com.biggates.device.PlatformContext
import com.biggates.device.permission.AppPermission
import com.biggates.device.permission.PermissionController
import com.biggates.device.permission.PermissionState
import com.biggates.device.permission.requestWithAutoRetryAndSettings

suspend fun PermissionController.requestLocationWhenInUse(): PermissionState {
    if (context.checkLocationWhenInUseGranted()) return PermissionState.Granted

    val requestPermission = AppPermission.LocationWhenInUse

    val granted = requestWithAutoRetryAndSettings(arrayOf(requestPermission))
    if (granted) return PermissionState.Granted

    val canAskAgain = shouldShowRationale(AppPermission.LocationWhenInUse)
    return PermissionState.Denied(canAskAgain = canAskAgain)
}
expect suspend fun PermissionController.requestLocationAlways(): PermissionState

expect fun PlatformContext.checkLocationWhenInUseGranted(): Boolean

expect fun PlatformContext.checkLocationAlwaysGranted(): Boolean