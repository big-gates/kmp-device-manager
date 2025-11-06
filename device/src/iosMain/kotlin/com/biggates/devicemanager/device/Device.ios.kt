package com.biggates.devicemanager.device

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IosDefaultDeviceIdentityProvider : DeviceIdentityProvider {

    override fun readIdentity(): DeviceIdentity {
        val device = UIDevice.currentDevice

        val platformName = device.systemName
        val platformVersion = device.systemVersion

        val manufacturer = "Apple"
        val model = device.model

        val info = NSBundle.mainBundle.infoDictionary
        val appVersionName = info?.get("CFBundleShortVersionString") as? String
        val appVersionCode = info?.get("CFBundleVersion") as? String

        val deviceUniqueId = device.identifierForVendor?.UUIDString

        return DeviceIdentity(
            platformName = platformName,
            platformVersion = platformVersion,
            manufacturer = manufacturer,
            model = model,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            deviceUniqueId = deviceUniqueId
        )
    }
}