package com.biggates.devicemanager.monitor

import com.biggates.devicemanager.Location
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
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
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.darwin.NSObject

class IosLocationMonitor : LocationMonitor, NSObject(), CLLocationManagerDelegateProtocol {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<Location?>(null)
    override val state: StateFlow<Location?>
        get() = _state.asStateFlow()

    private var cLLocationManager: CLLocationManager? = null
    private var liveTrackingEnabled: Boolean = false

    override suspend fun start() {
        cLLocationManager = CLLocationManager().apply {
            delegate = this@IosLocationMonitor
            desiredAccuracy = kCLLocationAccuracyHundredMeters
            distanceFilter = 10.0
            allowsBackgroundLocationUpdates = true
            pausesLocationUpdatesAutomatically = false
            activityType = CLActivityTypeOther
        }
        when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> startInternal()
            else -> { /* 권한 없으면 대기 */ }
        }
    }

    override fun stop() {
        cLLocationManager?.stopUpdatingLocation()
        cLLocationManager?.stopMonitoringSignificantLocationChanges()
        cLLocationManager?.delegate = null
        cLLocationManager = null
        scope.cancel()
    }

    override suspend fun enableLiveTracking(enable: Boolean) {
        liveTrackingEnabled = enable
        cLLocationManager?.let {
            it.stopUpdatingLocation()
            it.stopMonitoringSignificantLocationChanges()
            startInternal()
        }
    }

    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        val m = cLLocationManager ?: CLLocationManager().also { cLLocationManager = it }
        val status = CLLocationManager.authorizationStatus()
        if (status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse) {
            return PermissionState.Granted
        }
        // 최초에는 사용 중 권한부터 요청
        m.requestWhenInUseAuthorization()
        return PermissionState.NotDetermined
    }

    private fun startInternal() {
        val locationManager = cLLocationManager ?: return
        if (liveTrackingEnabled) locationManager.startUpdatingLocation()
        else locationManager.startMonitoringSignificantLocationChanges()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = (didUpdateLocations.lastOrNull() as? CLLocation) ?: return
        val (latitude, longitude) = location.coordinate().useContents { latitude to longitude }
        val snapshot = Location(
            latitude = latitude,
            longitude = longitude,
            horizontalAccuracyMeters = if (location.horizontalAccuracy >= 0) location.horizontalAccuracy else null,
            speedMetersPerSecond = if (location.speed >= 0) location.speed else null,
            bearingDegrees = if (location.course >= 0) (location.course % 360.0 + 360.0) % 360.0 else null
        )
        _state.update { snapshot }
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        val s = CLLocationManager.authorizationStatus()
        if (s == kCLAuthorizationStatusAuthorizedAlways || s == kCLAuthorizationStatusAuthorizedWhenInUse) {
            startInternal()
        }
    }
}