package com.biggates.devicemanager.monitor

import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import kotlinx.coroutines.flow.StateFlow

interface Monitor<T> {
    val state: StateFlow<T>
    suspend fun start()
    fun stop()
    suspend fun requestPermission(controller: PermissionController): PermissionState
}