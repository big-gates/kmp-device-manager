package com.biggates.device.monitor

import com.biggates.device.BatteryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.darwin.NSObjectProtocol

class IosBatteryMonitor : BatteryMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state: MutableStateFlow<BatteryStatus?> = MutableStateFlow(null)
    override val state: StateFlow<BatteryStatus?>
        get() = _state.asStateFlow()

    private val notificationCenter = NSNotificationCenter.defaultCenter
    private val tokens = mutableListOf<NSObjectProtocol>()

    override suspend fun start() {
        UIDevice.currentDevice.batteryMonitoringEnabled = true

        // 초기 상태 반영
        push()

        // 변동 구독
        tokens += notificationCenter.addObserverForName(
            name = UIDeviceBatteryLevelDidChangeNotification,
            `object` = null,
            queue = null
        ) { _ -> push() }

        tokens += notificationCenter.addObserverForName(
            name = UIDeviceBatteryStateDidChangeNotification,
            `object` = null,
            queue = null
        ) { _ -> push() }
    }

    override fun stop() {
        tokens.forEach { notificationCenter.removeObserver(it) }
        tokens.clear()
        scope.cancel()
    }
    private fun push() {
        val level = UIDevice.currentDevice.batteryLevel
        val percent = if (level < 0f) null else (level * 100f).toInt().coerceIn(0, 100)

        val isCharging = when (UIDevice.currentDevice.batteryState) {
            UIDeviceBatteryState.UIDeviceBatteryStateCharging,
            UIDeviceBatteryState.UIDeviceBatteryStateFull -> true
            UIDeviceBatteryState.UIDeviceBatteryStateUnplugged -> false
            else -> null
        }

        _state.update {
            BatteryStatus(percent = percent, isCharging = isCharging)
        }
    }
}