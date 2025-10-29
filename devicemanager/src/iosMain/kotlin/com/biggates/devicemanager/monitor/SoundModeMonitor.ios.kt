package com.biggates.devicemanager.monitor

import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.SoundMode
import com.biggates.devicemanager.SoundModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosSoundModeMonitor : SoundModeMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(SoundModeState(SoundMode.Unknown, null))
    override val state: StateFlow<SoundModeState>
        get() = _state.asStateFlow()

    override suspend fun start() {
        // iOS는 공식적으로 무음 스위치 상태를 제공하지 않습니다.

    }

    override fun stop() {
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        // 별도 권한 필요 없음
        return PermissionState.Granted
    }
}