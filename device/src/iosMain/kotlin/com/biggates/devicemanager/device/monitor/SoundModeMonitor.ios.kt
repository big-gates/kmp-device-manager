package com.biggates.devicemanager.device.monitor

import com.biggates.devicemanager.device.SoundMode
import com.biggates.devicemanager.device.SoundModeState
import com.biggates.devicemanager.device.monitor.SoundModeMonitor
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
}