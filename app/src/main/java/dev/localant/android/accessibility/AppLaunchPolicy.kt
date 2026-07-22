package dev.localant.android.accessibility

internal object AppLaunchPolicy {
    fun requireAllowed(deviceLocked: Boolean, overlayPermissionGranted: Boolean) {
        if (deviceLocked) {
            throw DeviceOperationException(
                "DEVICE_LOCKED",
                "Unlock the Android device before launching an app.",
            )
        }
        if (!overlayPermissionGranted) {
            throw DeviceOperationException(
                "OVERLAY_PERMISSION_REQUIRED",
                "Grant LocalAnt the Display over other apps permission before launching apps remotely.",
            )
        }
    }
}
