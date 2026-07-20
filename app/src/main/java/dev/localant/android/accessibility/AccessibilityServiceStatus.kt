package dev.localant.android.accessibility

import android.content.Context
import android.provider.Settings

object AccessibilityServiceStatus {
    fun isEnabled(context: Context): Boolean {
        val globallyEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!globallyEnabled) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return containsComponent(
            enabledServices = enabledServices,
            expectedPackage = context.packageName,
            expectedClass = LocalAntAccessibilityService::class.java.name,
        )
    }

    internal fun containsComponent(
        enabledServices: String?,
        expectedPackage: String,
        expectedClass: String,
    ): Boolean = enabledServices
        .orEmpty()
        .split(':')
        .any { flattened ->
            val separator = flattened.indexOf('/')
            if (separator <= 0 || separator == flattened.lastIndex) return@any false
            val packageName = flattened.substring(0, separator)
            val rawClass = flattened.substring(separator + 1)
            val className = if (rawClass.startsWith('.')) packageName + rawClass else rawClass
            packageName == expectedPackage && className == expectedClass
        }
}
