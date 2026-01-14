package com.biggates.devicemanager.permission

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual object PermissionHandler {

    actual suspend fun checkStatus(permission: Permission): PermissionStatus {
        return when (permission) {
            Permission.Location -> checkLocationWhenInUseStatus()
            Permission.LocationAlways -> checkLocationAlwaysStatus()
            Permission.Camera -> checkCameraStatus()
            Permission.Microphone -> checkMicrophoneStatus()
            Permission.Notification -> checkNotificationStatus()
            Permission.Storage -> PermissionStatus.Granted // iOS는 샌드박스라 항상 허용
            Permission.Photos -> checkPhotosStatus()
            Permission.Bluetooth -> checkBluetoothStatus()
            Permission.Contacts -> checkContactsStatus()
            Permission.Calendar -> checkCalendarStatus()
        }
    }

    actual suspend fun request(permission: Permission): PermissionStatus {
        return when (permission) {
            Permission.Location -> requestLocationWhenInUse()
            Permission.LocationAlways -> requestLocationAlways()
            Permission.Camera -> requestCamera()
            Permission.Microphone -> requestMicrophone()
            Permission.Notification -> requestNotification()
            Permission.Storage -> PermissionStatus.Granted
            Permission.Photos -> requestPhotos()
            Permission.Bluetooth -> requestBluetooth()
            Permission.Contacts -> requestContacts()
            Permission.Calendar -> requestCalendar()
        }
    }

    actual suspend fun request(permissions: List<Permission>): Map<Permission, PermissionStatus> {
        val results = mutableMapOf<Permission, PermissionStatus>()
        for (permission in permissions) {
            results[permission] = request(permission)
        }
        return results
    }

    actual suspend fun shouldShowRationale(permission: Permission): Boolean {
        // iOS에는 공식 라쇼날 신호가 없음
        // 아직 결정되지 않은 경우에만 true 반환
        return checkStatus(permission) == PermissionStatus.NotDetermined
    }

    actual suspend fun openAppSettings() {
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication.openURL(
            url,
            options = emptyMap<Any?, Any?>(),
            completionHandler = { _ -> }
        )
    }

    private fun checkLocationWhenInUseStatus(): PermissionStatus {
        return when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> PermissionStatus.Granted
            kCLAuthorizationStatusDenied -> PermissionStatus.PermanentlyDenied
            kCLAuthorizationStatusRestricted -> PermissionStatus.Restricted
            kCLAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            else -> PermissionStatus.NotDetermined
        }
    }

    private fun checkLocationAlwaysStatus(): PermissionStatus {
        return when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways -> PermissionStatus.Granted
            kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.Denied // WhenInUse만 허용된 경우
            kCLAuthorizationStatusDenied -> PermissionStatus.PermanentlyDenied
            kCLAuthorizationStatusRestricted -> PermissionStatus.Restricted
            kCLAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            else -> PermissionStatus.NotDetermined
        }
    }

    private suspend fun requestLocationWhenInUse(): PermissionStatus {
        if (!CLLocationManager.locationServicesEnabled()) {
            return PermissionStatus.PermanentlyDenied
        }

        val currentStatus = checkLocationWhenInUseStatus()
        if (currentStatus != PermissionStatus.NotDetermined) {
            return currentStatus
        }

        return awaitLocationAuthorization { manager ->
            manager.requestWhenInUseAuthorization()
        }
    }

    private suspend fun requestLocationAlways(): PermissionStatus {
        if (!CLLocationManager.locationServicesEnabled()) {
            return PermissionStatus.PermanentlyDenied
        }

        // 먼저 WhenInUse 권한 필요
        val whenInUseStatus = checkLocationWhenInUseStatus()
        if (whenInUseStatus == PermissionStatus.NotDetermined) {
            val result = requestLocationWhenInUse()
            if (result != PermissionStatus.Granted) {
                return result
            }
        } else if (whenInUseStatus != PermissionStatus.Granted) {
            return whenInUseStatus
        }

        val alwaysStatus = checkLocationAlwaysStatus()
        if (alwaysStatus == PermissionStatus.Granted) {
            return PermissionStatus.Granted
        }

        return awaitLocationAuthorization { manager ->
            manager.requestAlwaysAuthorization()
        }
    }

    private suspend fun awaitLocationAuthorization(
        request: (CLLocationManager) -> Unit
    ): PermissionStatus = suspendCancellableCoroutine { continuation ->
        val manager = CLLocationManager()
        var finished = false

        fun finish(status: PermissionStatus) {
            if (!finished) {
                finished = true
                manager.delegate = null
                continuation.resume(status)
            }
        }

        fun handleStatus(status: Int) {
            when (status) {
                kCLAuthorizationStatusAuthorizedAlways,
                kCLAuthorizationStatusAuthorizedWhenInUse -> finish(PermissionStatus.Granted)
                kCLAuthorizationStatusDenied -> finish(PermissionStatus.PermanentlyDenied)
                kCLAuthorizationStatusRestricted -> finish(PermissionStatus.Restricted)
                else -> Unit // 계속 대기
            }
        }

        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                handleStatus(CLLocationManager.authorizationStatus())
            }

            override fun locationManager(
                manager: CLLocationManager,
                didChangeAuthorizationStatus: CLAuthorizationStatus
            ) {
                handleStatus(didChangeAuthorizationStatus)
            }
        }

        manager.delegate = delegate
        request(manager)

        continuation.invokeOnCancellation { manager.delegate = null }
    }

    private fun checkCameraStatus(): PermissionStatus {
        return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
            AVAuthorizationStatusDenied -> PermissionStatus.PermanentlyDenied
            AVAuthorizationStatusRestricted -> PermissionStatus.Restricted
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            else -> PermissionStatus.NotDetermined
        }
    }

    private fun checkMicrophoneStatus(): PermissionStatus {
        return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio)) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.Granted
            AVAuthorizationStatusDenied -> PermissionStatus.PermanentlyDenied
            AVAuthorizationStatusRestricted -> PermissionStatus.Restricted
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            else -> PermissionStatus.NotDetermined
        }
    }

    private suspend fun requestCamera(): PermissionStatus {
        val currentStatus = checkCameraStatus()
        if (currentStatus != PermissionStatus.NotDetermined) {
            return currentStatus
        }

        return suspendCancellableCoroutine { cont ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                cont.resume(if (granted) PermissionStatus.Granted else PermissionStatus.PermanentlyDenied)
            }
        }
    }

    private suspend fun requestMicrophone(): PermissionStatus {
        val currentStatus = checkMicrophoneStatus()
        if (currentStatus != PermissionStatus.NotDetermined) {
            return currentStatus
        }

        return suspendCancellableCoroutine { cont ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
                cont.resume(if (granted) PermissionStatus.Granted else PermissionStatus.PermanentlyDenied)
            }
        }
    }

    private suspend fun checkNotificationStatus(): PermissionStatus {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val settings = suspendCancellableCoroutine { cont ->
            center.getNotificationSettingsWithCompletionHandler { s -> cont.resume(s) }
        }

        return when (settings?.authorizationStatus) {
            UNAuthorizationStatusAuthorized,
            UNAuthorizationStatusProvisional,
            UNAuthorizationStatusEphemeral -> PermissionStatus.Granted
            UNAuthorizationStatusDenied -> PermissionStatus.PermanentlyDenied
            UNAuthorizationStatusNotDetermined -> PermissionStatus.NotDetermined
            else -> PermissionStatus.NotDetermined
        }
    }

    private suspend fun requestNotification(): PermissionStatus {
        val currentStatus = checkNotificationStatus()
        if (currentStatus != PermissionStatus.NotDetermined) {
            return currentStatus
        }

        val center = UNUserNotificationCenter.currentNotificationCenter()
        val granted = suspendCancellableCoroutine { cont ->
            center.requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
            ) { granted, _ -> cont.resume(granted) }
        }

        return if (granted) PermissionStatus.Granted else PermissionStatus.PermanentlyDenied
    }

    // ─────────────────────────────────────────────────────────────
    // Photos (TODO: PHPhotoLibrary 구현 필요)
    // ─────────────────────────────────────────────────────────────

    private fun checkPhotosStatus(): PermissionStatus {
        // TODO: PHPhotoLibrary.authorizationStatus() 구현
        return PermissionStatus.NotDetermined
    }

    private suspend fun requestPhotos(): PermissionStatus {
        // TODO: PHPhotoLibrary.requestAuthorization() 구현
        return PermissionStatus.NotDetermined
    }

    // ─────────────────────────────────────────────────────────────
    // Bluetooth (TODO: CBCentralManager 구현 필요)
    // ─────────────────────────────────────────────────────────────

    private fun checkBluetoothStatus(): PermissionStatus {
        // TODO: CBCentralManager 구현
        return PermissionStatus.NotDetermined
    }

    private suspend fun requestBluetooth(): PermissionStatus {
        // TODO: CBCentralManager 구현
        return PermissionStatus.NotDetermined
    }

    // ─────────────────────────────────────────────────────────────
    // Contacts (TODO: CNContactStore 구현 필요)
    // ─────────────────────────────────────────────────────────────

    private fun checkContactsStatus(): PermissionStatus {
        // TODO: CNContactStore.authorizationStatusForEntityType() 구현
        return PermissionStatus.NotDetermined
    }

    private suspend fun requestContacts(): PermissionStatus {
        // TODO: CNContactStore.requestAccessForEntityType() 구현
        return PermissionStatus.NotDetermined
    }

    // ─────────────────────────────────────────────────────────────
    // Calendar (TODO: EKEventStore 구현 필요)
    // ─────────────────────────────────────────────────────────────

    private fun checkCalendarStatus(): PermissionStatus {
        // TODO: EKEventStore.authorizationStatusForEntityType() 구현
        return PermissionStatus.NotDetermined
    }

    private suspend fun requestCalendar(): PermissionStatus {
        // TODO: EKEventStore.requestAccessToEntityType() 구현
        return PermissionStatus.NotDetermined
    }
}
