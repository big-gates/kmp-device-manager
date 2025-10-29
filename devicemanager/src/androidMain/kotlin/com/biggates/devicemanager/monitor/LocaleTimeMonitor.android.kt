package com.biggates.devicemanager.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.biggates.devicemanager.LocaleTimeState
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
import kotlinx.datetime.TimeZone

class AndroidLocaleTimeMonitor(
    private val context: PlatformContext
) : LocaleTimeMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        LocaleTimeState(localeTag = currentLocaleTag(), timezoneId = currentTimezoneId())
    )
    override val state: StateFlow<LocaleTimeState>
        get() = _state.asStateFlow()

    private val localeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            push()
        }
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            push()
        }
    }

    override suspend fun start() {
        ContextCompat.registerReceiver(
            context,
            localeReceiver,
            IntentFilter().apply {
                Intent.ACTION_LOCALE_CHANGED
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(
            context,
            timeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        push()
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(localeReceiver) }
        runCatching { context.unregisterReceiver(timeReceiver) }
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController) = PermissionState.Granted

    private fun push() {
        _state.update {
            LocaleTimeState(
                localeTag = currentLocaleTag(),
                timezoneId = currentTimezoneId()
            )
        }
    }

    private fun currentLocaleTag(): String {
        val locales = context.resources.configuration.locales
        val locale = if (locales.size() > 0) locales[0] else java.util.Locale.getDefault()
        return locale.toLanguageTag()
    }

    private fun currentTimezoneId(): String {
        return TimeZone.currentSystemDefault().id
    }
}