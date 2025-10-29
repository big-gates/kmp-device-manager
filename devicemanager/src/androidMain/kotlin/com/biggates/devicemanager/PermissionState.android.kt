package com.biggates.devicemanager

/**
 * 권한 컨트롤러 (UI에서 런처를 연결)
 */
class AndroidPermissionController(
    val context: PlatformContext,
    val launchPermissions: suspend (Array<String>) -> Map<String, Boolean>
) : PermissionController