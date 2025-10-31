package com.biggates.device.monitor

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.biggates.device.PlatformContext
import com.biggates.device.SoundMode
import com.biggates.device.SoundModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidSoundModeMonitor(
    private val context: PlatformContext,
) : SoundModeMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<SoundModeState?>(null)
    override val state: StateFlow<SoundModeState?>
        get() = _state.asStateFlow()

    private var audioManager: AudioManager? = null
    private var notificationManager: NotificationManager? = null

    private val ringerModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            push()
        }
    }

    override suspend fun start() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 시스템 벨소리 모드 변경 브로드캐스트 수신
        context.registerReceiver(
            ringerModeReceiver,
            IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        )

        // 초기 상태 즉시 반영
        push()
    }

    override fun stop() {
        runCatching { context.unregisterReceiver(ringerModeReceiver) }
        scope.cancel()
    }

//    override suspend fun requestPermission(controller: PermissionController): PermissionState {
//        controller as AndroidPermissionController
//
//        // 설정 화면이 아예 없을 수도 있으므로, 인텐트 가능 여부 체크
//        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
//        val canResolve = intent.resolveActivity(context.packageManager) != null
//
//        // 미지원 취급: 진행 허용
//        if (!canResolve) return PermissionState.Granted
//
//        // 실제 접근 여부 확인
//        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//        if (nm.isNotificationPolicyAccessGranted) return PermissionState.Granted
//
//        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
//
//        val granted = withTimeoutOrNull(60_000) {
//            while (true) {
//                if (nm.isNotificationPolicyAccessGranted) return@withTimeoutOrNull true
//                delay(500)
//            }
//        } == true
//
//        return if (granted) PermissionState.Granted
//        else PermissionState.Denied(canAskAgain = true)
//    }

    private fun push() {
        val am = audioManager
        val nm = notificationManager
        if (am == null || nm == null) return

        val mode = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> SoundMode.Silent
            AudioManager.RINGER_MODE_VIBRATE -> SoundMode.Vibrate
            AudioManager.RINGER_MODE_NORMAL -> SoundMode.Normal
            else -> SoundMode.Unknown
        }

        val isDoNotDisturbEnabled: Boolean? = runCatching {
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        }.getOrNull()

        _state.update {
            SoundModeState(
                mode = mode,
                isDoNotDisturbEnabled = isDoNotDisturbEnabled
            )
        }
    }
}