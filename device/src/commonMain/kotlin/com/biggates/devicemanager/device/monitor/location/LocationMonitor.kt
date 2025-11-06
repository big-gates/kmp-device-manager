package com.biggates.devicemanager.device.monitor.location

import com.biggates.devicemanager.device.Location
import com.biggates.devicemanager.device.monitor.Monitor

interface LocationMonitor : Monitor<Location?> {
    /**
     * 실시간 연속 추적이 필요한 경우 true,
     * 기본적으로 배터리 절약 모드(중요 변화 중심)로 운용하려면 false.
     */
    suspend fun enableLiveTracking(enable: Boolean)
}