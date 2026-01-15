# Device Manager KMP

Kotlin Multiplatform 라이브러리로 Android와 iOS에서 **권한 관리**와 **디바이스 정보**를 통합된 API로 제공합니다.

직관적이고 간결한 API를 제공합니다.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

| Module | Version |
|--------|---------|
| devicemanager-permission | 0.0.1 |
| devicemanager-device | 0.0.1 |

## Features

- **Permission Module** - 런타임 권한 요청 및 상태 확인
- **Device Module** - 위치 모니터링 및 디바이스 정보
- **Kotlin Multiplatform** - Android & iOS 지원
- **Coroutines** - suspend 함수 기반의 비동기 API

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
dependencies {
    // Permission 모듈만 사용
    implementation("io.github.big-gates:devicemanager-permission:0.0.1")

    // Device 모듈 사용 (Permission 모듈 포함)
    implementation("io.github.big-gates:devicemanager-device:0.0.1")
}
```

### Version Catalog

```toml
# libs.versions.toml
[versions]
devicemanager = "0.0.1"

[libraries]
devicemanager-permission = { module = "io.github.big-gates:devicemanager-permission", version.ref = "devicemanager" }
devicemanager-device = { module = "io.github.big-gates:devicemanager-device", version.ref = "devicemanager" }
```

---

## Quick Start

### 1. 초기화 (Android Only)

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // PermissionHandler 초기화 (필수)
        PermissionHandler.init(this)
    }
}
```

### 2. 권한 요청

```kotlin
// 단일 권한 요청
val status = Permission.Camera.request()

when (status) {
    PermissionStatus.Granted -> {
        // 권한 허용됨 - 기능 사용 가능
    }
    PermissionStatus.Denied -> {
        // 권한 거부됨 - 다시 요청 가능
    }
    PermissionStatus.PermanentlyDenied -> {
        // 영구 거부됨 - 설정에서 변경 필요
        openAppSettings()
    }
    else -> { }
}
```

### 3. 권한 상태 확인

```kotlin
// 현재 권한 상태 확인
val status = Permission.Location.status()

if (status.isGranted) {
    // 권한이 허용된 상태
}
```

---

## Permission Module

### Supported Permissions

| Permission | Android | iOS |
|------------|---------|-----|
| `Permission.Location` | ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION | locationWhenInUseAuthorization |
| `Permission.LocationAlways` | ACCESS_BACKGROUND_LOCATION (Q+) | locationAlwaysAuthorization |
| `Permission.Camera` | CAMERA | AVCaptureDevice |
| `Permission.Microphone` | RECORD_AUDIO | AVAudioSession |
| `Permission.Notification` | POST_NOTIFICATIONS (13+) | UNUserNotificationCenter |
| `Permission.Storage` | READ_MEDIA_* (13+) / READ_EXTERNAL_STORAGE | N/A (Sandbox) |
| `Permission.Photos` | READ_MEDIA_IMAGES, READ_MEDIA_VIDEO | PHPhotoLibrary |
| `Permission.Bluetooth` | BLUETOOTH_SCAN, BLUETOOTH_CONNECT (S+) | CBCentralManager |
| `Permission.Contacts` | READ_CONTACTS, WRITE_CONTACTS | CNContactStore |
| `Permission.Calendar` | READ_CALENDAR, WRITE_CALENDAR | EKEventStore |

### Permission Status

| Status | Description |
|--------|-------------|
| `Granted` | 권한이 허용됨 |
| `Denied` | 권한이 거부됨 (다시 요청 가능) |
| `PermanentlyDenied` | 영구 거부됨 (설정에서만 변경 가능) |
| `Restricted` | 기기 정책에 의해 제한됨 (iOS) |
| `Limited` | 제한된 접근 허용 (iOS 14+ Photos) |
| `NotDetermined` | 아직 결정되지 않음 |

### API Reference

```kotlin
// 권한 상태 확인
suspend fun Permission.status(): PermissionStatus

// 권한 요청
suspend fun Permission.request(): PermissionStatus

// 여러 권한 동시 요청
suspend fun List<Permission>.request(): Map<Permission, PermissionStatus>

// 권한 허용 여부 확인
suspend fun Permission.isGranted(): Boolean
suspend fun Permission.isDenied(): Boolean

// Rationale 표시 필요 여부 (Android)
suspend fun Permission.shouldShowRationale(): Boolean

// 앱 설정 화면 열기
suspend fun openAppSettings()

```

### Usage Examples

#### 단일 권한 요청

```kotlin
lifecycleScope.launch {
    val status = Permission.Camera.request()

    if (status.isGranted) {
        openCamera()
    }
}
```

#### 여러 권한 동시 요청

```kotlin
lifecycleScope.launch {
    val results = listOf(
        Permission.Camera,
        Permission.Microphone
    ).request()

    val allGranted = results.all { it.value.isGranted }

    if (allGranted) {
        startVideoRecording()
    }
}
```

#### 권한 상태에 따른 UI 분기

```kotlin
@Composable
fun CameraScreen() {
    var permissionStatus by remember { mutableStateOf<PermissionStatus?>(null) }

    LaunchedEffect(Unit) {
        permissionStatus = Permission.Camera.status()
    }

    when (permissionStatus) {
        PermissionStatus.Granted -> {
            CameraPreview()
        }
        PermissionStatus.PermanentlyDenied -> {
            SettingsPrompt(
                onOpenSettings = {
                    coroutineScope.launch { openAppSettings() }
                }
            )
        }
        else -> {
            RequestPermissionButton(
                onClick = {
                    coroutineScope.launch {
                        permissionStatus = Permission.Camera.request()
                    }
                }
            )
        }
    }
}
```

---

## Device Module

Device 모듈은 Permission 모듈을 포함하며, 위치 모니터링 등 디바이스 기능을 제공합니다.

### Location Monitor

```kotlin
// LocationMonitor 생성
val locationMonitor = AndroidLocationMonitor(context) // Android
val locationMonitor = IosLocationMonitor()            // iOS

// 위치 업데이트 시작 (권한 필요)
locationMonitor.start()

// 위치 상태 구독
locationMonitor.state.collect { location ->
    location?.let {
        println("위도: ${it.latitude}, 경도: ${it.longitude}")
    }
}

// 실시간 추적 모드 전환
locationMonitor.enableLiveTracking(true)

// 중지
locationMonitor.stop()
```

> **Note**: `start()` 및 `enableLiveTracking()` 호출 전에 위치 권한이 필요합니다.
> 권한이 없으면 `IllegalStateException`이 발생합니다.

```kotlin
// 권한 확인 후 사용
if (Permission.Location.isGranted()) {
    locationMonitor.start()
} else {
    val status = Permission.Location.request()
    if (status.isGranted) {
        locationMonitor.start()
    }
}
```

---

## Platform Setup

### Android

`AndroidManifest.xml`에 필요한 권한을 선언하세요:

```xml
<!-- 위치 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- 카메라 / 마이크 -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 알림 (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 저장소 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

<!-- 블루투스 (Android 12+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- 연락처 / 캘린더 -->
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.WRITE_CONTACTS" />
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />
```

### iOS

`Info.plist`에 필요한 Usage Description을 추가하세요:

```xml
<!-- 위치 -->
<key>NSLocationWhenInUseUsageDescription</key>
<string>앱에서 위치 정보를 사용합니다.</string>
<key>NSLocationAlwaysUsageDescription</key>
<string>백그라운드에서 위치 정보를 사용합니다.</string>

<!-- 카메라 / 마이크 -->
<key>NSCameraUsageDescription</key>
<string>카메라를 사용합니다.</string>
<key>NSMicrophoneUsageDescription</key>
<string>마이크를 사용합니다.</string>

<!-- 사진 라이브러리 -->
<key>NSPhotoLibraryUsageDescription</key>
<string>사진 라이브러리에 접근합니다.</string>

<!-- 블루투스 -->
<key>NSBluetoothAlwaysUsageDescription</key>
<string>블루투스를 사용합니다.</string>

<!-- 연락처 / 캘린더 -->
<key>NSContactsUsageDescription</key>
<string>연락처에 접근합니다.</string>
<key>NSCalendarsUsageDescription</key>
<string>캘린더에 접근합니다.</string>
```

---

## Requirements

- **Kotlin**: 1.9.0+
- **Android**: minSdk 24+
- **iOS**: 14.0+

---

## License

```
Copyright 2025 Big Gates

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
