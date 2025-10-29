package com.biggates.devicemanager.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.os.Looper
import androidx.core.content.ContextCompat
import com.biggates.devicemanager.AndroidPermissionController
import com.biggates.devicemanager.Location
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.PlatformContext
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
        if (hasLocationPermission()) {
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
        if (hasLocationPermission()) {
            stop()
            // 재시작
            fusedClient = LocationServices.getFusedLocationProviderClient(context)
            startLocationUpdatesInternal()
        }
    }

    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        controller as AndroidPermissionController

        val result = controller.launchPermissions(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        return if (granted) PermissionState.Granted
        else PermissionState.Denied(canAskAgain = true)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == android.content.pm.PackageManager.PERMISSION_GRANTED || coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesInternal() {
        val live = liveTrackingEnabled

        val request = LocationRequest.Builder(if (live) 2_000L else 60_000L)
            .setPriority(if (live) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateIntervalMillis(if (live) 1_500L else 30_000L)
            .apply {
                try {
                    setMinUpdateDistanceMeters(if (live) 10f else 50f)
                } catch (_: Throwable) {}
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
}