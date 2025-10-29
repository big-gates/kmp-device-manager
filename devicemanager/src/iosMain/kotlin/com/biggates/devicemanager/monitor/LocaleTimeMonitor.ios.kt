package com.biggates.devicemanager.monitor

import com.biggates.devicemanager.LocaleTimeState
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSLocale
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.Foundation.NSTimeZone
import platform.Foundation.canonicalLanguageIdentifierFromString
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages
import platform.Foundation.systemTimeZone
import platform.darwin.NSObjectProtocol

class IosLocaleTimeMonitor : LocaleTimeMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nc = NSNotificationCenter.defaultCenter
    private val tokens = mutableListOf<NSObjectProtocol>()

    private val _state = MutableStateFlow(
        LocaleTimeState(
            localeTag = currentBcp47(),
            timezoneId = NSTimeZone.systemTimeZone.name
        )
    )
    override val state: StateFlow<LocaleTimeState>
        get() = _state.asStateFlow()

    override suspend fun start() {
        tokens += nc.addObserverForName(NSCurrentLocaleDidChangeNotification, null, null) { _ -> push() }
        tokens += nc.addObserverForName(NSSystemTimeZoneDidChangeNotification, null, null) { _ -> push() }
        push()
    }

    override fun stop() {
        tokens.forEach { nc.removeObserver(it) }
        tokens.clear()
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController) = PermissionState.Granted

    private fun push() {
        _state.update {
            LocaleTimeState(
                localeTag = currentBcp47(),
                timezoneId = NSTimeZone.systemTimeZone.name
            )
        }
    }

    private fun currentBcp47(): String? {
        val preferred = NSLocale.preferredLanguages.firstOrNull()?.toString()
        if (!preferred.isNullOrBlank()) return preferred.replace('_', '-')
        val lang = NSLocale.currentLocale.languageCode
        val region = NSLocale.currentLocale.countryCode
        val raw = if (!region.isNullOrEmpty()) "$lang-$region" else lang
        return NSLocale.canonicalLanguageIdentifierFromString(raw).replace('_', '-')
    }
}