package dev.localant.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppLaunchPolicyTest {
    @Test
    fun lockedDeviceIsRejected() {
        val error = assertThrows(DeviceOperationException::class.java) {
            AppLaunchPolicy.requireAllowed(deviceLocked = true, overlayPermissionGranted = true)
        }

        assertEquals("DEVICE_LOCKED", error.code)
    }

    @Test
    fun missingOverlayPermissionIsRejected() {
        val error = assertThrows(DeviceOperationException::class.java) {
            AppLaunchPolicy.requireAllowed(deviceLocked = false, overlayPermissionGranted = false)
        }

        assertEquals("OVERLAY_PERMISSION_REQUIRED", error.code)
    }

    @Test
    fun unlockedDeviceWithOverlayPermissionIsAllowed() {
        AppLaunchPolicy.requireAllowed(deviceLocked = false, overlayPermissionGranted = true)
    }
}
