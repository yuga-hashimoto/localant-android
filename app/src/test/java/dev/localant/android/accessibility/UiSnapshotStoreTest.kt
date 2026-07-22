package dev.localant.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UiSnapshotStoreTest {
    @Test
    fun nodeFromHiddenPreviousPackageIsRejected() {
        val store = UiSnapshotStore()
        store.replace("com.twitter.android", mapOf("node_0001" to listOf(0, 1)))

        val error = assertThrows(DeviceOperationException::class.java) {
            store.resolve("node_0001", currentPackage = "com.openai.chatgpt")
        }

        assertEquals("STALE_UI_SNAPSHOT", error.code)
    }

    @Test
    fun nodeFromCurrentPackageResolvesItsPath() {
        val store = UiSnapshotStore()
        store.replace("com.android.settings", mapOf("node_0001" to listOf(0, 1)))

        assertEquals(
            listOf(0, 1),
            store.resolve("node_0001", currentPackage = "com.android.settings"),
        )
    }

    @Test
    fun windowChangeInvalidatesAllNodeIds() {
        val store = UiSnapshotStore()
        store.replace("com.android.settings", mapOf("node_0001" to listOf(0, 1)))
        store.invalidate()

        val error = assertThrows(DeviceOperationException::class.java) {
            store.resolve("node_0001", currentPackage = "com.android.settings")
        }

        assertEquals("STALE_UI_SNAPSHOT", error.code)
    }

    @Test
    fun unknownNodeIsRejected() {
        val store = UiSnapshotStore()
        store.replace("com.android.settings", emptyMap())

        val error = assertThrows(DeviceOperationException::class.java) {
            store.resolve("node_missing", currentPackage = "com.android.settings")
        }

        assertEquals("NODE_NOT_FOUND", error.code)
    }
}
