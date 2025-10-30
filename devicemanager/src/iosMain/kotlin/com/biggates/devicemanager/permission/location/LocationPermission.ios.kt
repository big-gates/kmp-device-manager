package com.biggates.devicemanager.permission.location

import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse

actual suspend fun PermissionController.requestLocationPermission(): PermissionState {
    val manager = CLLocationManager()
    val status = CLLocationManager.authorizationStatus()
    if (status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse) {
        return PermissionState.Granted
    }
    manager.requestWhenInUseAuthorization() // Always가 필요하면 별도 분기에서 requestAlwaysAuthorization()
    return PermissionState.NotDetermined
}