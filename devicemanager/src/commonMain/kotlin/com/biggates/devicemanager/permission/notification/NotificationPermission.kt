package com.biggates.devicemanager.permission.notification

import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState

/**
 * 런타임 알림 권한 요청/체크(안드로이드 13+, iOS 10+).
 * - Android < 33: 항상 Granted로 처리
 * - iOS: UNUserNotificationCenter 권한 상태에 따라 처리
 */
expect suspend fun PermissionController.requestNotificationsPermission(): PermissionState