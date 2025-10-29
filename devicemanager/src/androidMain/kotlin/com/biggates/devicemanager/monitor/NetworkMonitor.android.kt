package com.biggates.devicemanager.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.biggates.devicemanager.NetworkType
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidNetworkMonitor(
    private val context: PlatformContext
) : NetworkMonitor {

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(NetworkType.Other)
    override val state: StateFlow<NetworkType> = mutableState

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = push()
        override fun onLost(network: Network) = push()
        override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) = push()
    }

    override suspend fun start() {
        connectivity.registerDefaultNetworkCallback(callback)
        push()
    }

    override fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController) = PermissionState.Granted

    private fun push() {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        mutableState.value = when {
            caps == null -> NetworkType.None
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.Cellular
            else -> NetworkType.Other
        }
    }
}