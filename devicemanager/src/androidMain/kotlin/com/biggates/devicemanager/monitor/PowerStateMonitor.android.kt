package com.biggates.devicemanager.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.PlatformContext
import com.biggates.devicemanager.PowerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidPowerStateMonitor(
    private val context: PlatformContext
) : PowerStateMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<PowerState?>(null)
    override val state: StateFlow<PowerState?>
        get() = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            push()
        }
    }

    override suspend fun start() {
        // 절전 모드 변경 브로드캐스트
        val filter = IntentFilter().apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
        context.registerReceiver(receiver, filter)
        push()
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController) = PermissionState.Granted

    private fun push() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        _state.update { PowerState(isLowPowerModeEnabled = pm.isPowerSaveMode) }
    }
}