package com.biggates.device.permission.location

import com.biggates.device.PlatformContext

expect fun PlatformContext.checkLocationWhenInUseGranted(): Boolean

expect fun PlatformContext.checkLocationAlwaysGranted(): Boolean