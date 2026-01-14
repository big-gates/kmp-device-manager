package com.biggates.devicemanager.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

actual object PermissionHandler {

    private var activityRef: WeakReference<ComponentActivity>? = null

    /**
     * Activity 초기화 - 권한 요청 전에 반드시 호출해야 함
     *
     * ```kotlin
     * class MainActivity : ComponentActivity() {
     *     override fun onCreate(savedInstanceState: Bundle?) {
     *         super.onCreate(savedInstanceState)
     *         PermissionHandler.init(this)
     *     }
     * }
     * ```
     */
    fun init(activity: ComponentActivity) {
        activityRef = WeakReference(activity)
    }

    private fun requireActivity(): ComponentActivity {
        return activityRef?.get()
            ?: error("PermissionHandler not initialized. Call PermissionHandler.init(activity) first.")
    }

    actual suspend fun checkStatus(permission: Permission): PermissionStatus {
        val activity = activityRef?.get() ?: return PermissionStatus.NotDetermined
        val context = activity.applicationContext

        val androidPermissions = permission.toAndroidPermissions()

        // 빈 권한 목록인 경우 (해당 API 레벨에서 필요 없음)
        if (androidPermissions.isEmpty()) {
            return PermissionStatus.Granted
        }

        // 모든 권한이 허용되었는지 확인
        val allGranted = androidPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            return PermissionStatus.Granted
        }

        // 알림 권한 특수 처리: 시스템 토글이 꺼져 있으면 영구 거부로 처리
        if (permission == Permission.Notification) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return PermissionStatus.PermanentlyDenied
            }
        }

        // Rationale 확인으로 영구 거부 여부 판단
        val shouldShowRationale = androidPermissions.any {
            activity.shouldShowRequestPermissionRationale(it)
        }

        return if (shouldShowRationale) {
            PermissionStatus.Denied
        } else {
            // Rationale이 false이고 권한이 없으면:
            // 아직 요청 안 함 (NotDetermined)
            // 영구 거부됨 (PermanentlyDenied)
            // Android에서는 구분이 어려워서 NotDetermined로 반환
            PermissionStatus.NotDetermined
        }
    }

    actual suspend fun request(permission: Permission): PermissionStatus {
        val activity = requireActivity()
        val androidPermissions = permission.toAndroidPermissions()

        // 빈 권한 목록인 경우 (해당 API 레벨에서 필요 없음)
        if (androidPermissions.isEmpty()) {
            return PermissionStatus.Granted
        }

        // 이미 허용된 경우
        val currentStatus = checkStatus(permission)
        if (currentStatus == PermissionStatus.Granted) {
            return PermissionStatus.Granted
        }

        // 권한 요청
        val result = requestPermissions(activity, androidPermissions)

        // 결과 분석
        val allGranted = androidPermissions.all { result[it] == true }

        if (allGranted) {
            return PermissionStatus.Granted
        }

        // Rationale 확인
        val shouldShowRationale = androidPermissions.any {
            activity.shouldShowRequestPermissionRationale(it)
        }

        return if (shouldShowRationale) {
            PermissionStatus.Denied
        } else {
            PermissionStatus.PermanentlyDenied
        }
    }

    actual suspend fun request(permissions: List<Permission>): Map<Permission, PermissionStatus> {
        val results = mutableMapOf<Permission, PermissionStatus>()

        for (permission in permissions) {
            results[permission] = request(permission)
        }

        return results
    }

    actual suspend fun shouldShowRationale(permission: Permission): Boolean {
        val activity = activityRef?.get() ?: return false
        val androidPermissions = permission.toAndroidPermissions()

        return androidPermissions.any {
            activity.shouldShowRequestPermissionRationale(it)
        }
    }

    actual suspend fun openAppSettings() {
        val activity = requireActivity()

        suspendCancellableCoroutine { cont ->
            val registry = activity.activityResultRegistry
            val key = "settings-${hashCode()}-${System.currentTimeMillis()}"

            var launcher: ActivityResultLauncher<Intent>? = null
            launcher = registry.register(
                key,
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (!cont.isCompleted) {
                    cont.resume(Unit)
                }
                launcher?.unregister()
            }

            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null)
            )
            launcher.launch(intent)

            cont.invokeOnCancellation {
                launcher.unregister()
            }
        }
    }

    private suspend fun requestPermissions(
        activity: ComponentActivity,
        permissions: List<String>
    ): Map<String, Boolean> {
        return suspendCancellableCoroutine { cont ->
            val registry = activity.activityResultRegistry
            val key = "perm-${hashCode()}-${System.currentTimeMillis()}"

            var launcher: ActivityResultLauncher<Array<String>>? = null
            launcher = registry.register(
                key,
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                if (!cont.isCompleted) {
                    cont.resume(result)
                }
                launcher?.unregister()
            }

            launcher.launch(permissions.toTypedArray())

            cont.invokeOnCancellation {
                launcher.unregister()
            }
        }
    }
}

internal fun Permission.toAndroidPermissions(): List<String> {
    return when (this) {
        Permission.Location -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        Permission.LocationAlways -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyList() // API 28 이하에서는 런타임 권한 없음
        }

        Permission.Camera -> listOf(Manifest.permission.CAMERA)

        Permission.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)

        Permission.Notification -> if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList() // API 32 이하에서는 런타임 권한 없음
        }

        Permission.Storage -> when {
            Build.VERSION.SDK_INT >= 33 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        Permission.Photos -> when {
            Build.VERSION.SDK_INT >= 33 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        Permission.Bluetooth -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            emptyList() // API 30 이하에서는 런타임 권한 없음 (위치 권한으로 대체)
        }

        Permission.Contacts -> listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )

        Permission.Calendar -> listOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }
}
