package dev.localant.android.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStatusTest {
    @Test
    fun relativeAndAbsoluteComponentNamesAreRecognized() {
        assertTrue(
            AccessibilityServiceStatus.containsComponent(
                "other.pkg/.Other:dev.localant.android/.accessibility.LocalAntAccessibilityService",
                "dev.localant.android",
                "dev.localant.android.accessibility.LocalAntAccessibilityService",
            ),
        )
        assertTrue(
            AccessibilityServiceStatus.containsComponent(
                "dev.localant.android/dev.localant.android.accessibility.LocalAntAccessibilityService",
                "dev.localant.android",
                "dev.localant.android.accessibility.LocalAntAccessibilityService",
            ),
        )
    }

    @Test
    fun differentServiceIsRejected() {
        assertFalse(
            AccessibilityServiceStatus.containsComponent(
                "dev.localant.android/.accessibility.OtherService",
                "dev.localant.android",
                "dev.localant.android.accessibility.LocalAntAccessibilityService",
            ),
        )
    }
}
