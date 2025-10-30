package com.biggates.devicemanager.monitor

import com.biggates.devicemanager.permission.PermissionController
import com.biggates.devicemanager.permission.PermissionState
import com.biggates.devicemanager.SoundVolume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.outputVolume
import platform.AVFAudio.setActive
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSTimer
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.setIsAccessibilityElement
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import platform.darwin.dispatch_time
import kotlin.math.abs

private const val VOLUME_CHANGED = "com.biggates.volumeChanged"

/**
 * Swift 없이 Kotlin/Native로만 구현한 볼륨 모니터.
 * - AVAudioSession setCategory(.ambient, .mixWithOthers) + setActive(true)
 * - 숨김 MPVolumeView를 윈도우에 부착(하드웨어 버튼 입력을 미디어 볼륨으로 라우팅 보장)
 * - 250ms 폴링으로 outputVolume 변화를 감지 → NSNotificationCenter로 브로드캐스트
 */
@OptIn(ExperimentalForeignApi::class)
class IosVolumeService : VolumeMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        SoundVolume(
            ringVolume = null,
            musicVolume = null,
            maxRingVolume = null,
            maxMusicVolume = null,
            normalizedOutputVolume = null
        )
    )
    override val state: StateFlow<SoundVolume>
        get() = _state.asStateFlow()

    private val session: AVAudioSession = AVAudioSession.sharedInstance()
    private var hiddenVolumeView: MPVolumeView? = null
    private var timer: NSTimer? = null
    private var last: Float? = null
    private var started = false

    override suspend fun start() {
        if (started) return
        started = true

        activateAudioSession()
        createHiddenVolumeView()

        // 윈도우가 준비된 뒤 한 틱 후 부착(씬 타이밍 문제 회피)
        dispatch_after(dispatch_time(0u, 0), dispatch_get_main_queue()) {
            attachHiddenVolumeViewIfNeeded()
        }

        // 초기값
        last = runCatching { session.outputVolume }.getOrNull()
        push(last)

        // 250ms 폴링 (KVO 없이도 안정적으로 동작)
        timer = NSTimer.scheduledTimerWithTimeInterval(0.25, repeats = true) { _ ->
            val cur = runCatching { session.outputVolume }.getOrNull()
            val changed = when {
                last == null && cur != null -> true
                last != null && cur == null -> true
                else -> abs((last ?: 0.0f) - (cur ?: 0.0f)) > 0.005
            }
            if (changed) {
                last = cur
                push(cur)
            }
        }
    }

    override fun stop() {
        timer?.invalidate()
        timer = null

        hiddenVolumeView?.removeFromSuperview()
        hiddenVolumeView = null

        runCatching { session.setActive(false, error = null) }
        scope.cancel()
        started = false
        last = null
    }

    override suspend fun requestPermission(controller: PermissionController): PermissionState {
        // iOS 볼륨 모니터링은 별도 권한이 필요 없습니다.
        return PermissionState.Granted
    }

    private fun activateAudioSession() {
        session.setCategory(
            category = AVAudioSessionCategoryAmbient,
            withOptions = AVAudioSessionCategoryOptionMixWithOthers,
            error = null
        )
        session.setActive(true, error = null)
    }

    private fun createHiddenVolumeView() {
        if (hiddenVolumeView != null) return
        hiddenVolumeView = MPVolumeView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
            setHidden(true)
            setIsAccessibilityElement(false)
        }
    }

    private fun attachHiddenVolumeViewIfNeeded() {
        val vv = hiddenVolumeView ?: return
        if (vv.superview != null) return
        val win = currentWindow() ?: return
        win.addSubview(vv)
    }

    private fun currentWindow(): UIWindow? {
        var result: UIWindow? = null
        // UIKit 접근은 메인 큐 보장
        dispatch_sync(dispatch_get_main_queue()) {
            val app = UIApplication.sharedApplication
            val scenes = app.connectedScenes.toList()
            for (s in scenes) {
                val ws = s as? UIWindowScene ?: continue
                val windows = ws.windows
                val key = windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
                if (key != null) { result = key; return@dispatch_sync }
                val first = windows.firstOrNull() as? UIWindow
                if (first != null) { result = first; return@dispatch_sync }
            }
            // 매우 초기 시점 호환
            val legacy = app.windows.firstOrNull() as? UIWindow
            if (legacy != null) result = legacy
        }
        return result
    }

    private fun push(value: Float?) {
        _state.update {
            it.copy(normalizedOutputVolume = value?.coerceIn(0.0f, 1.0f))
        }
    }
}