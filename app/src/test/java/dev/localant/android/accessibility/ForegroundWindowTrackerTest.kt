package dev.localant.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ForegroundWindowTrackerTest {
    @Test
    fun accessibilityRootIsUsedBeforeAnyWindowEvent() {
        val tracker = ForegroundWindowTracker()

        assertEquals(
            "com.twitter.android",
            tracker.currentPackage(rootPackage = "com.twitter.android"),
        )
    }

    @Test
    fun latestWindowEventRemainsAuthoritativeAfterLongIdle() {
        val tracker = ForegroundWindowTracker()
        tracker.observe("com.openai.chatgpt")

        assertEquals(
            "com.openai.chatgpt",
            tracker.currentPackage(rootPackage = "com.twitter.android"),
        )
    }

    @Test
    fun rootMismatchIsRejectedInsteadOfOperatingHiddenApp() {
        val tracker = ForegroundWindowTracker()
        tracker.observe("com.openai.chatgpt")

        val error = assertThrows(DeviceOperationException::class.java) {
            tracker.requireMatchingRoot(rootPackage = "com.twitter.android")
        }

        assertEquals("WINDOW_MISMATCH", error.code)
    }

    @Test
    fun matchingRootIsAccepted() {
        val tracker = ForegroundWindowTracker()
        tracker.observe("com.android.settings")

        assertEquals(
            "com.android.settings",
            tracker.requireMatchingRoot(rootPackage = "com.android.settings"),
        )
    }
}
