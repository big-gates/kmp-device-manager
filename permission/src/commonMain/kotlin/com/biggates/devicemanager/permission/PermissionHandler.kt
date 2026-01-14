package com.biggates.devicemanager.permission

/**
 * 플랫폼별 권한 처리를 담당하는 핸들러
 *
 * Android: Activity 기반 권한 요청
 * iOS: 네이티브 프레임워크 기반 권한 요청
 */
expect object PermissionHandler {

    /**
     * 권한 상태 확인
     */
    suspend fun checkStatus(permission: Permission): PermissionStatus

    /**
     * 권한 요청
     */
    suspend fun request(permission: Permission): PermissionStatus

    /**
     * 여러 권한 동시 요청
     */
    suspend fun request(permissions: List<Permission>): Map<Permission, PermissionStatus>

    /**
     * 권한 설명이 필요한지 확인 (Android shouldShowRequestPermissionRationale)
     */
    suspend fun shouldShowRationale(permission: Permission): Boolean

    /**
     * 앱 설정 화면 열기
     */
    suspend fun openAppSettings()
}

/**
 * 현재 권한 상태 확인
 *
 * ```kotlin
 * val status = Permission.Location.status()
 * if (status.isGranted) {
 *     // 권한 허용됨
 * }
 * ```
 */
suspend fun Permission.status(): PermissionStatus =
    PermissionHandler.checkStatus(this)

/**
 * 권한 요청
 *
 * ```kotlin
 * val status = Permission.Camera.request()
 * when (status) {
 *     PermissionStatus.Granted -> // 허용됨
 *     PermissionStatus.Denied -> // 거부됨
 *     PermissionStatus.PermanentlyDenied -> // 영구 거부
 *     else -> // 기타
 * }
 * ```
 */
suspend fun Permission.request(): PermissionStatus =
    PermissionHandler.request(this)

/**
 * 여러 권한 동시 요청
 *
 * ```kotlin
 * val results = listOf(Permission.Camera, Permission.Microphone).request()
 * if (results.all { it.value.isGranted }) {
 *     // 모든 권한 허용됨
 * }
 * ```
 */
suspend fun List<Permission>.request(): Map<Permission, PermissionStatus> =
    PermissionHandler.request(this)

/**
 * 권한 설명이 필요한지 확인
 */
suspend fun Permission.shouldShowRationale(): Boolean =
    PermissionHandler.shouldShowRationale(this)

/**
 * 앱 설정 화면 열기
 *
 * 영구 거부된 권한을 변경하려면 설정에서 직접 변경해야 함
 */
suspend fun openAppSettings() =
    PermissionHandler.openAppSettings()

/**
 * 권한이 허용되었는지 확인
 */
suspend fun Permission.isGranted(): Boolean =
    status().isGranted

/**
 * 권한이 거부되었는지 확인
 */
suspend fun Permission.isDenied(): Boolean =
    status().isDenied

/**
 * 권한이 영구 거부되었는지 확인
 */
suspend fun Permission.isPermanentlyDenied(): Boolean =
    status().isPermanentlyDenied