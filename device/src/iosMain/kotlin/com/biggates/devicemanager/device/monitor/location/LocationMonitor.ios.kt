package com.biggates.devicemanager.device.monitor.location

import com.biggates.devicemanager.device.Location
import com.biggates.devicemanager.permission.Permission
import com.biggates.devicemanager.permission.isGranted
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.CoreLocation.CLActivityTypeOther
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.darwin.NSObject

class IosLocationMonitor : LocationMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<Location?>(null)
    override val state: StateFlow<Location?> get() = _state.asStateFlow()

    private var cLLocationManager: CLLocationManager? = null
    private var delegate: LocationDelegate? = null
    private var liveTrackingEnabled = false

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun start() {
        check(Permission.Location.isGranted()) {
            "Location permission (NSLocationWhenInUseUsageDescription) is required before calling start()."
        }

        val manager = cLLocationManager ?: CLLocationManager().also { cLLocationManager = it }

        val locationDelegate = LocationDelegate(
            onUpdate = { cl ->
                _state.update {
                    val (lat, lon) = cl.coordinate().useContents { latitude to longitude }
                    Location(
                        latitude = lat,
                        longitude = lon,
                        horizontalAccuracyMeters = cl.horizontalAccuracy.takeIf { it >= 0 },
                        speedMetersPerSecond = cl.speed.takeIf { it >= 0 },
                        bearingDegrees = cl.course.takeIf { it >= 0 }?.let { (it % 360.0 + 360.0) % 360.0 }
                    )
                }
            },
            onAuthChanged = {
                startInternal()
            }
        )
        delegate = locationDelegate
        manager.delegate = locationDelegate

        // 기본 설정
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.distanceFilter = 10.0
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        manager.activityType = CLActivityTypeOther

        startInternal()
    }

    override fun stop() {
        cLLocationManager?.stopUpdatingLocation()
        cLLocationManager?.stopMonitoringSignificantLocationChanges()
        cLLocationManager?.delegate = null
        delegate = null
        cLLocationManager = null
        scope.cancel()
    }

    override suspend fun enableLiveTracking(enable: Boolean) {
        check(Permission.Location.isGranted()) {
            "Location permission (NSLocationWhenInUseUsageDescription) is required before calling enableLiveTracking()."
        }
        liveTrackingEnabled = enable
        cLLocationManager?.let {
            it.stopUpdatingLocation()
            it.stopMonitoringSignificantLocationChanges()
            startInternal()
        }
    }

    private fun startInternal() {
        val manager = cLLocationManager ?: return
        if (liveTrackingEnabled) manager.startUpdatingLocation()
        else manager.startMonitoringSignificantLocationChanges()
    }
}

private class LocationDelegate(
    private val onUpdate: (CLLocation) -> Unit,
    private val onAuthChanged: () -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        (didUpdateLocations.lastOrNull() as? CLLocation)?.let(onUpdate)
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthChanged()
    }
}