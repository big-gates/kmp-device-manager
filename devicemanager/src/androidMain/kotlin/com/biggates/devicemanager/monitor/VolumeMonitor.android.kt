package com.biggates.devicemanager.monitor

import android.content.*
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.biggates.devicemanager.PermissionController
import com.biggates.devicemanager.PermissionState
import com.biggates.devicemanager.PlatformContext
import com.biggates.devicemanager.SoundVolume
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidVolumeMonitor(
    private val context: PlatformContext
) : VolumeMonitor {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<SoundVolume?>(null)
    override val state: StateFlow<SoundVolume?>
        get() = _state.asStateFlow()

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = push()
    }
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = push()
    }

    override suspend fun start() {
        // 시스템 볼륨 변경 감시
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver)
        // 벨소리 모드 변경 감시
        context.registerReceiver(ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        push()
    }

    override fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(volumeObserver) }
        runCatching { context.unregisterReceiver(ringerReceiver) }
        scope.cancel()
    }

    override suspend fun requestPermission(controller: PermissionController) = PermissionState.Granted

    private fun push() {
        val ring = audio.getStreamVolume(AudioManager.STREAM_RING)
        val ringMax = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        val music = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val normalized = if (musicMax > 0) music.toFloat() / musicMax else null

        _state.update {
            SoundVolume(
                ringVolume = ring,
                musicVolume = music,
                maxRingVolume = ringMax,
                maxMusicVolume = musicMax,
                normalizedOutputVolume = normalized
            )
        }
    }
}