package com.biggates.device.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.biggates.device.BatteryStatus
import com.biggates.device.PlatformContext
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
            _state.update { parseFromIntent(intent) ?: it }
        }
    }

    override suspend fun start() {
        _state.update { readSnapshot() }

        // 이후 변화 구독
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
        scope.cancel()
    }

    /** 우선 BatteryManager, 실패 시 ACTION_BATTERY_CHANGED 폴백 */
    private fun readSnapshot(): BatteryStatus? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val percentFromBm = pct.takeIf { it in 0..100 }

        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val isCharging = sticky?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }

        val percentFromIntent = sticky?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null
        }

        val finalPercent = percentFromBm ?: percentFromIntent
        val finalCharging = isCharging

        return if (finalPercent != null || finalCharging != null) {
            BatteryStatus(percent = finalPercent, isCharging = finalCharging)
        } else {
            null
        }
    }

    private fun parseFromIntent(intent: Intent): BatteryStatus? {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }

        // 인텐트 기반 퍼센트 (일부 기기에서 더 신뢰됨)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else null

        return BatteryStatus(percent = percent, isCharging = isCharging)
    }
}