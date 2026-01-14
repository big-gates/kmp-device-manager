package com.biggates.devicemanager.permission

/**
 * 권한 상태를 나타내는 sealed class
 *
 */
sealed class PermissionStatus {

    /**
     * 권한이 허용됨
     */
    data object Granted : PermissionStatus()

    /**
     * 권한이 거부됨 (다시 요청 가능)
     */
    data object Denied : PermissionStatus()

    /**
     * 권한이 영구적으로 거부됨 (설정에서만 변경 가능)
     * - Android: "다시 묻지 않기" 선택됨
     * - iOS: 권한 거부 후 재요청 불가
     */
    data object PermanentlyDenied : PermissionStatus()

    /**
     * 권한이 제한됨 (iOS 전용)
     * - 기기 정책에 의해 권한이 제한된 상태
     * - 예: 자녀 보호 기능, MDM 정책
     */
    data object Restricted : PermissionStatus()

    /**
     * 제한된 접근 허용 (iOS 14+ 사진 라이브러리)
     * - 사용자가 선택한 사진에만 접근 가능
     */
    data object Limited : PermissionStatus()

    /**
     * 권한 상태를 아직 결정하지 않음
     * - 권한 요청 다이얼로그가 아직 표시되지 않은 상태
     */
    data object NotDetermined : PermissionStatus()

    /**
     * 권한이 허용되었는지 확인
     */
    val isGranted: Boolean
        get() = this is Granted

    /**
     * 권한이 거부되었는지 확인 (일시적 또는 영구적)
     */
    val isDenied: Boolean
        get() = this is Denied || this is PermanentlyDenied

    /**
     * 권한이 영구적으로 거부되었는지 확인
     */
    val isPermanentlyDenied: Boolean
        get() = this is PermanentlyDenied

    /**
     * 권한이 제한되었는지 확인 (iOS Restricted)
     */
    val isRestricted: Boolean
        get() = this is Restricted

    /**
     * 권한이 제한된 접근 상태인지 확인 (iOS Limited)
     */
    val isLimited: Boolean
        get() = this is Limited

    /**
     * 권한이 부여되었거나 제한된 접근이 허용되었는지 확인
     */
    val isGrantedOrLimited: Boolean
        get() = this is Granted || this is Limited
}
