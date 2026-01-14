package com.biggates.devicemanager.permission

/**
 * 사용법:
 * ```kotlin
 * // 권한 상태 확인
 * val status = Permission.Location.status()
 *
 * // 권한 요청
 * val result = Permission.Location.request()
 *
 * // 여러 권한 동시 요청
 * val results = listOf(Permission.Location, Permission.Camera).request()
 * ```
 */
sealed interface Permission {

    /**
     * 앱 사용 중 위치 권한 (Foreground location)
     * - Android: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
     * - iOS: locationWhenInUseAuthorization
     */
    data object Location : Permission

    /**
     * 백그라운드 위치 권한 (Background location)
     * - Android (Q+): ACCESS_BACKGROUND_LOCATION
     * - iOS: locationAlwaysAuthorization
     */
    data object LocationAlways : Permission

    /**
     * 카메라 권한
     * - Android: CAMERA
     * - iOS: AVCaptureDevice camera
     */
    data object Camera : Permission

    /**
     * 마이크 권한
     * - Android: RECORD_AUDIO
     * - iOS: AVAudioSession microphone
     */
    data object Microphone : Permission

    /**
     * 푸시 알림 권한
     * - Android (13+): POST_NOTIFICATIONS
     * - iOS: UNUserNotificationCenter
     */
    data object Notification : Permission

    /**
     * 외부 저장소 권한
     * - Android: READ/WRITE_EXTERNAL_STORAGE (legacy), READ_MEDIA_* (Android 13+)
     * - iOS: N/A (샌드박스)
     */
    data object Storage : Permission

    /**
     * 사진 라이브러리 권한
     * - Android: READ_MEDIA_IMAGES, READ_MEDIA_VIDEO
     * - iOS: PHPhotoLibrary
     */
    data object Photos : Permission

    /**
     * 블루투스 권한
     * - Android (S+): BLUETOOTH_SCAN, BLUETOOTH_CONNECT
     * - iOS: CBCentralManager
     */
    data object Bluetooth : Permission

    /**
     * 연락처 권한
     * - Android: READ_CONTACTS, WRITE_CONTACTS
     * - iOS: CNContactStore
     */
    data object Contacts : Permission

    /**
     * 캘린더 권한
     * - Android: READ_CALENDAR, WRITE_CALENDAR
     * - iOS: EKEventStore
     */
    data object Calendar : Permission
}
