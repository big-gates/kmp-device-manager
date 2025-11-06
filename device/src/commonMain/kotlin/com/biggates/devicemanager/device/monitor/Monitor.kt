package com.biggates.devicemanager.device.monitor

import kotlinx.coroutines.flow.StateFlow

interface Monitor<T> {
    val state: StateFlow<T>
    suspend fun start()
    fun stop()
}