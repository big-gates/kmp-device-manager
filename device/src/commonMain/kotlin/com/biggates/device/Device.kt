package com.biggates.device

enum class NetworkType { None, Wifi, Cellular, Other }

/**
 * @param ringVolume 0..maxRing (iOS는 null)
 * @param musicVolume 0..maxMusic (iOS는 null)
 * @param maxRingVolume (iOS는 null)
 * @param maxMusicVolume (iOS는 null)
 * @param normalizedOutputVolume iOS: 0.0..1.0, Android: 참고값(음악 스트림 비율)
 * */
data class SoundVolume(
    val ringVolume: Int?,
    val musicVolume: Int?,
    val maxRingVolume: Int?,
    val maxMusicVolume: Int?,
    val normalizedOutputVolume: Float?
)

/**
 * @param percent 0~100, 알 수 없으면 null
 * @param isCharging 알 수 없으면 null
 */
data class BatteryStatus(
    val percent: Int?,
    val isCharging: Boolean?
)

/**
 * @param localeTag BCP-47 (예: "ko-KR")
 * @param timezoneId 예: "Asia/Seoul"
 */
data class LocaleTimeState(
    val localeTag: String?,
    val timezoneId: String?
)

data class PowerState(
    val isLowPowerModeEnabled: Boolean?
)

/**
 * @param platformName 예: "Android", "iOS"
 * @param platformVersion 예: "14(34)" 또는 "18.1.1"
 * @param manufacturer Android: 제조사, iOS: "Apple"
 * @param model 기기 모델명
 * @param appVersionName 예: "1.2.3"
 * @param appVersionCode 예: "123" 또는 CFBundleVersion
 * @param deviceUniqueId ndroid: ANDROID_ID, iOS: identifierForVendor
 */
data class DeviceIdentity(
    val platformName: String,
    val platformVersion: String?,
    val manufacturer: String?,
    val model: String?,
    val appVersionName: String?,
    val appVersionCode: String?,
    val deviceUniqueId: String?
)

data class Location(
    val latitude: Double?,
    val longitude: Double?,
    val horizontalAccuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val bearingDegrees: Double?
)

/**
 * 현재 기기의 알림/벨소리 관련 사운드 모드 상태를 표현한다.
 *
 * - Normal: 벨소리 켜짐, 소리 출력 가능
 * - Vibrate: 진동 모드
 * - Silent: 완전 무음
 * - Unknown: iOS 등 공개되지 않은 플랫폼 또는 알 수 없는 상태
 *
 * isDoNotDisturbEnabled: 방해 금지 모드(알림 차단 모드)가 활성화되었는지 여부
 *   - Android 에서는 NotificationManager.interruptionFilter 기반으로 추정 가능
 *   - iOS 의 집중 모드(Focus Mode)는 공식 퍼블릭 API로 노출되지 않는다 → 항상 null 또는 false
 */
data class SoundModeState(
    val mode: SoundMode,
    val isDoNotDisturbEnabled: Boolean?
)

enum class SoundMode {
    Normal,
    Vibrate,
    Silent,
    Unknown
}

/**
 * @param soundVolume 플랫폼별로 채우는 항목 다름
 */
data class Device(
    val identity: DeviceIdentity,
    val localeTimeState: LocaleTimeState,
    val batteryStatus: BatteryStatus?,
    val networkType: NetworkType,
    val soundVolume: SoundVolume?,
    val soundModeState: SoundModeState?,
    val powerState: PowerState?,
    val location: Location?
)

/**
 * 정적 디바이스 정보 제공자
 */
interface DeviceIdentityProvider {
    fun readIdentity(): DeviceIdentity
}

