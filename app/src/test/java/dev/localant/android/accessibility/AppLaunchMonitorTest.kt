package dev.localant.android.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchMonitorTest {
    @Test
    fun succeedsOnlyAfterTargetPackageBecomesForeground() = runTest {
        var current = "com.twitter.android"
        var now = 0L
        val monitor = AppLaunchMonitor(
            currentPackage = { current },
            nowUptimeMs = { now },
            pause = { delayMs ->
                now += delayMs
                current = "com.android.settings"
            },
        )

        assertTrue(
            monitor.awaitForeground(
                packageName = "com.android.settings",
                timeoutMs = 1_000,
                pollIntervalMs = 100,
            ),
        )
    }

    @Test
    fun failsWhenStartActivityDoesNotActuallyChangeForegroundApp() = runTest {
        var now = 0L
        val monitor = AppLaunchMonitor(
            currentPackage = { "com.twitter.android" },
            nowUptimeMs = { now },
            pause = { delayMs -> now += delayMs },
        )

        assertFalse(
            monitor.awaitForeground(
                packageName = "com.android.settings",
                timeoutMs = 300,
                pollIntervalMs = 100,
            ),
        )
    }
}
