package com.biggates.devicemanager.monitor.location

import android.annotation.SuppressLint
import android.os.Looper
import com.biggates.devicemanager.Location
import com.biggates.devicemanager.PlatformContext
import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.permission.location.hasForegroundLocationPermissionGranted
import com.biggates.devicemanager.permission.location.requestLocationPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.location.Location as AndroidLocation

class AndroidLocationMonitor(
    private val context: PlatformContext
) : LocationMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<Location?>(null)
    override val state: StateFlow<Location?>
        get() = _state.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var liveTrackingEnabled: Boolean = false

    override suspend fun start() {
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
        if (context.hasForegroundLocationPermissionGranted()) {
            startLocationUpdatesInternal()
        }
    }

    override fun stop() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
        scope.cancel()
    }

    override suspend fun enableLiveTracking(enable: Boolean) {
        liveTrackingEnabled = enable
        if (context.hasForegroundLocationPermissionGranted()) {
            stop()
            // 재시작
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
            startLocationUpdatesInternal()
        }
    }

    /**
     * 권한 요청 규칙
     * 1) 먼저 포그라운드 위치 권한(정밀 또는 대략)을 요청한다.
     * 2) 안드로이드 10(API 29) 이상에서만 백그라운드 위치 권한을 별도로 추가 요청한다.
     *    안드로이드 11(API 30) 이상에서는 시스템 정책에 따라 설정 화면으로 이동될 수 있다.
     */
    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        return controller.requestLocationPermission()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesInternal() {
        val spec = currentSpec(liveTrackingEnabled)

        val request = LocationRequest.Builder(spec.intervalMillis)
            .setPriority(if (spec.highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateIntervalMillis(spec.minUpdateIntervalMillis)
            .apply {
                spec.minUpdateDistanceMeters?.let {
                    try {
                        setMinUpdateDistanceMeters(it)
                    } catch (_: Throwable) {}
                }
            }
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: AndroidLocation = result.lastLocation ?: return
                _state.update {
                    Location(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                        speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble() else null,
                        bearingDegrees = if (location.hasBearing())
                            ((location.bearing.toDouble() % 360 + 360) % 360) else null
                    )
                }
            }
        }
        fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun currentSpec(live: Boolean) = LocationRequestSpec(
        intervalMillis = if (live) 5_000L else 60_000L,
        minUpdateIntervalMillis = if (live) 3_000L else 30_000L,
        minUpdateDistanceMeters = if (live) 20f else 100f,
        highAccuracy = live
    )

    data class LocationRequestSpec(
        val intervalMillis: Long,
        val minUpdateIntervalMillis: Long,
        val minUpdateDistanceMeters: Float?,
        val highAccuracy: Boolean
    )
}