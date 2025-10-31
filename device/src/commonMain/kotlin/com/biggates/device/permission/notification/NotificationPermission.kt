package com.biggates.device.permission.notification

import com.biggates.device.PlatformContext

expect suspend fun PlatformContext.checkNotificationsGranted(): Boolean