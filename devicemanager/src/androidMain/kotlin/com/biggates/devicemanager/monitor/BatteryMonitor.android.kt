package com.biggates.devicemanager.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.biggates.devicemanager.BatteryStatus
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidBatteryMonitor(
    private val context: PlatformContext
) : BatteryMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<BatteryStatus?>(null)
    override val state: StateFlow<BatteryStatus?>
        get() = _state.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent ?: return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL)

            val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 }

            _state.update { BatteryStatus(percent = percent, isCharging = isCharging) }
        }
    }

    override suspend fun start() {
        // sticky 브로드캐스트를 즉시 한 번 수신
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryReceiver.onReceive(context, sticky)

        // 이후 변화 구독
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        // 배터리 정보는 별도 런타임 권한이 필요하지 않음
        return PermissionState.Granted
    }
}