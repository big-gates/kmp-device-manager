package com.biggates.device.permission.location

import com.biggates.device.PlatformContext
import com.biggates.device.permission.IosPermissionController
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.darwin.NSObject

@OptIn(InternalCoroutinesApi::class)
suspend fun IosPermissionController.requestLocationWhenInUse(): Boolean {
    if (context.checkLocationWhenInUseGranted()) return true
    if (CLLocationManager.authorizationStatus() == kCLAuthorizationStatusDenied) return false

    val manager = CLLocationManager()
    return suspendCancellableCoroutine { cont ->
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                val s = CLLocationManager.authorizationStatus()
                when (s) {
                    kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> cont.tryResume(true) {}
                    kCLAuthorizationStatusDenied -> cont.tryResume(false) {}
                }
            }
        }
        manager.delegate = delegate
        manager.requestWhenInUseAuthorization()
        cont.invokeOnCancellation { manager.delegate = null }
    }
}

@OptIn(InternalCoroutinesApi::class)
suspend fun IosPermissionController.requestLocationAlways(): Boolean {
    if (context.checkLocationAlwaysGranted()) return true
    val s = CLLocationManager.authorizationStatus()
    if (s == kCLAuthorizationStatusDenied) return false

    val manager = CLLocationManager()
    return suspendCancellableCoroutine { cont ->
        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                val st = CLLocationManager.authorizationStatus()
                if (st == kCLAuthorizationStatusAuthorizedAlways) {
                    cont.tryResume(true) {}
                } else if (st == kCLAuthorizationStatusDenied) {
                    cont.tryResume(false) {}
                }
            }
        }
        manager.delegate = delegate

        // WhenInUse가 이미 있으면 승격, 없으면 일단 WhenInUse부터
        if (s == kCLAuthorizationStatusAuthorizedWhenInUse) {
            manager.requestAlwaysAuthorization()
        } else {
            manager.requestWhenInUseAuthorization()
            // 콜백에서 바로 Always를 또 요청하지 않고,
            // call-site에서 requestWithAutoRetry… 로 재흐름 처리 권장
        }
        cont.invokeOnCancellation { manager.delegate = null }
    }
}

actual fun PlatformContext.checkLocationWhenInUseGranted(): Boolean {
    val s = CLLocationManager.authorizationStatus()
    return s == kCLAuthorizationStatusAuthorizedWhenInUse || s == kCLAuthorizationStatusAuthorizedAlways
}

actual fun PlatformContext.checkLocationAlwaysGranted(): Boolean {
    val s = CLLocationManager.authorizationStatus()
    return s == kCLAuthorizationStatusAuthorizedAlways
}