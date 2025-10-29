package com.biggates.devicemanager.monitor

import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.PowerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoPowerStateDidChangeNotification
import platform.Foundation.isLowPowerModeEnabled
import platform.darwin.NSObjectProtocol

class IosPowerStateMonitor : PowerStateMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nc = NSNotificationCenter.defaultCenter
    private val tokens = mutableListOf<NSObjectProtocol>()

    private val _state = MutableStateFlow<PowerState?>(null)
    override val state: StateFlow<PowerState?>
        get() = _state.asStateFlow()

    override suspend fun start() {
        tokens += nc.addObserverForName(NSProcessInfoPowerStateDidChangeNotification, null, null) { _ -> push() }
        push()
    }

    override fun stop() {
        tokens.forEach { nc.removeObserver(it) }
        tokens.clear()
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController) = PermissionState.Granted

    private fun push() {
        val enabled = NSProcessInfo.processInfo.isLowPowerModeEnabled()
        _state.value = PowerState(isLowPowerModeEnabled = enabled)
    }
}