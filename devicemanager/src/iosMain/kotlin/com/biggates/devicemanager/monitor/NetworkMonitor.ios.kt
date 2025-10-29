package com.biggates.devicemanager.monitor

import com.biggates.devicemanager.NetworkType
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.darwin.DISPATCH_QUEUE_SERIAL_WITH_AUTORELEASE_POOL
import platform.darwin.dispatch_queue_create

class IosNetworkMonitor : NetworkMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(NetworkType.Other)
    override val state: StateFlow<NetworkType>
        get() = _state.asStateFlow()

    private var monitor: nw_path_monitor_t? = null

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun start() {
        monitor = nw_path_monitor_create()
        val queue = dispatch_queue_create("com.biggates.device.net", DISPATCH_QUEUE_SERIAL_WITH_AUTORELEASE_POOL)
        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            val t = when (status) {
                nw_path_status_satisfied -> when {
                    nw_path_uses_interface_type(path, nw_interface_type_wifi) -> NetworkType.Wifi
                    nw_path_uses_interface_type(path, nw_interface_type_cellular) -> NetworkType.Cellular
                    else -> NetworkType.Other
                }
                else -> NetworkType.None
            }
            _state.value = t
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }

    override fun stop() {
        runCatching { nw_path_monitor_cancel(monitor) }
        monitor = null
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController) = PermissionState.Granted
}
