package com.biggates.devicemanager

import com.biggates.devicemanager.monitor.BatteryMonitor
import com.biggates.devicemanager.monitor.LocaleTimeMonitor
import com.biggates.devicemanager.monitor.LocationMonitor
import com.biggates.devicemanager.monitor.NetworkMonitor
import com.biggates.devicemanager.monitor.PowerStateMonitor
import com.biggates.devicemanager.monitor.SoundModeMonitor
import com.biggates.devicemanager.monitor.VolumeMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceManager(
    private val identityProvider: DeviceIdentityProvider,
    private val batteryMonitor: BatteryMonitor,
    private val locationMonitor: LocationMonitor,
    private val volumeMonitor: VolumeMonitor,
    private val networkMonitor: NetworkMonitor,
    private val localeTimeMonitor: LocaleTimeMonitor,
    private val powerStateMonitor: PowerStateMonitor,
    private val soundModeMonitor: SoundModeMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _device = MutableStateFlow(
        Device(
            identity = identityProvider.readIdentity(),
            localeTimeState = localeTimeMonitor.state.value,
            batteryStatus = batteryMonitor.state.value,
            networkType = networkMonitor.state.value,
            soundVolume = volumeMonitor.state.value,
            soundModeState = soundModeMonitor.state.value,
            location = locationMonitor.state.value,
            powerState = powerStateMonitor.state.value,
        )
    )
    val device: StateFlow<Device>
        get() = _device.asStateFlow()

    suspend fun start() {
        batteryMonitor.start()
        locationMonitor.start()
        volumeMonitor.start()
        networkMonitor.start()
        localeTimeMonitor.start()
        powerStateMonitor.start()
        soundModeMonitor.start()

        scope.launch {
            combine(
                localeTimeMonitor.state,
                batteryMonitor.state,
                networkMonitor.state,
                volumeMonitor.state,
                powerStateMonitor.state,
                locationMonitor.state,
                soundModeMonitor.state
            ) { arr ->
                val localeTime = arr[0] as LocaleTimeState
                val battery = arr[1] as BatteryStatus
                val network = arr[2] as NetworkType
                val volume = arr[3] as SoundVolume
                val power = arr[4] as PowerState
                val location = arr[5] as Location?
                val soundModeState = arr[6] as SoundModeState

                val identity = _device.value.identity
                Device(
                    identity = identity,
                    localeTimeState = localeTime,
                    batteryStatus = battery,
                    networkType = network,
                    soundVolume = volume,
                    location = location,
                    powerState = power,
                    soundModeState = soundModeState
                )
            }.collect { merged -> _device.update { merged } }
        }
    }

    fun stop() {
        batteryMonitor.stop()
        locationMonitor.stop()
        volumeMonitor.stop()
        networkMonitor.stop()
        localeTimeMonitor.stop()
        powerStateMonitor.stop()
        soundModeMonitor.stop()
        scope.cancel()
    }
}