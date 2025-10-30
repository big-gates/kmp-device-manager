package com.biggates.devicemanager.permission.location

import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState

expect suspend fun PermissionController.requestLocationPermission(): PermissionState