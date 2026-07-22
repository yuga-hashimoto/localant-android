package dev.localant.android.accessibility

internal class ForegroundWindowTracker {
    @Volatile
    private var latestEventPackage: String? = null

    fun observe(packageName: String?) {
        val normalized = packageName?.trim().orEmpty()
        if (normalized.isEmpty()) return
        latestEventPackage = normalized
    }

    fun currentPackage(rootPackage: String?): String? =
        latestEventPackage
            ?: rootPackage?.trim()?.takeIf(String::isNotEmpty)

    fun requireMatchingRoot(rootPackage: String?): String {
        val normalizedRoot = rootPackage?.trim()?.takeIf(String::isNotEmpty)
            ?: throw DeviceOperationException(
                "ACCESSIBILITY_UNAVAILABLE",
                "The active Android accessibility window has no package name.",
            )
        val foregroundPackage = currentPackage(normalizedRoot)
            ?: throw DeviceOperationException(
                "ACCESSIBILITY_UNAVAILABLE",
                "The foreground Android package is unavailable.",
            )
        if (foregroundPackage != normalizedRoot) {
            throw DeviceOperationException(
                "WINDOW_MISMATCH",
                "The accessibility window belongs to $normalizedRoot while $foregroundPackage is visible.",
            )
        }
        return foregroundPackage
    }
}
