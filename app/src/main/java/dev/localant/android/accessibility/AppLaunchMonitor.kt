package dev.localant.android.accessibility

import kotlinx.coroutines.delay

internal class AppLaunchMonitor(
    private val currentPackage: () -> String?,
    private val nowUptimeMs: () -> Long,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun awaitForeground(
        packageName: String,
        timeoutMs: Long,
        pollIntervalMs: Long,
    ): Boolean {
        require(timeoutMs >= 0) { "timeoutMs must be non-negative." }
        require(pollIntervalMs > 0) { "pollIntervalMs must be positive." }

        val deadline = nowUptimeMs() + timeoutMs
        while (true) {
            if (currentPackage() == packageName) return true
            val remaining = deadline - nowUptimeMs()
            if (remaining <= 0) return false
            pause(minOf(pollIntervalMs, remaining))
        }
    }
}
