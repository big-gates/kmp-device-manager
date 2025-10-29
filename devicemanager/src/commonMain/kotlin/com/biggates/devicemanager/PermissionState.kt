package com.biggates.devicemanager

interface PermissionController

sealed class PermissionState {
    data object Granted : PermissionState()
    data class Denied(val canAskAgain: Boolean) : PermissionState()
    data object NotDetermined : PermissionState()
}