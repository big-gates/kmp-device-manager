package com.biggates.devicemanager

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings

class AndroidDefaultDeviceIdentityProvider(
    private val context: PlatformContext
): DeviceIdentityProvider {

    @SuppressLint("HardwareIds")
    override fun readIdentity(): DeviceIdentity {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val modelRaw = Build.MODEL?.trim().orEmpty()
        val model = if (modelRaw.startsWith(manufacturer, ignoreCase = true)) {
            modelRaw
        } else {
            "$manufacturer $modelRaw".trim()
        }

        val platformName = "Android"
        val platformVersion = "${Build.VERSION.RELEASE}(${Build.VERSION.SDK_INT})"

        val appVersionName = runCatching {
            val pm = context.packageManager
            pm.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()

        val appVersionCode = runCatching {
            val pm = context.packageManager
            val p = pm.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode
            else @Suppress("DEPRECATION") p.versionCode.toLong()
            code.toString()
        }.getOrNull()

        val deviceUniqueId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        return DeviceIdentity(
            platformName = platformName,
            platformVersion = platformVersion,
            manufacturer = manufacturer.ifBlank { null },
            model = model.ifBlank { null },
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            deviceUniqueId = deviceUniqueId
        )
    }

}