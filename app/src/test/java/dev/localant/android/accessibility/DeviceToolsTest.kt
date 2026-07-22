package dev.localant.android.accessibility

import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.ToolRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolsTest {
    @Test
    fun register_assignsExpectedRiskLevels() {
        val registry = ToolRegistry()
        DeviceTools.register(registry, FakeGateway())

        assertEquals(0, registry.findDefinition("device_status")!!.risk.value)
        assertEquals(0, registry.findDefinition("device_get_ui_tree")!!.risk.value)
        assertEquals(1, registry.findDefinition("device_screenshot")!!.risk.value)
        assertEquals(2, registry.findDefinition("device_tap")!!.risk.value)
        assertEquals(3, registry.findDefinition("device_input_text")!!.risk.value)
        assertEquals(3, registry.findDefinition("device_launch_app")!!.risk.value)
        val properties = registry.findDefinition("device_tap")!!.inputSchema
            .getValue("properties").jsonObject
        assertFalse(properties.containsKey("_approvalId"))
    }

    @Test
    fun uiTree_returnsNormalizedNodes() = runTest {
        val registry = ToolRegistry()
        DeviceTools.register(
            registry,
            FakeGateway(
                tree = UiTreeSnapshot(
                    packageName = "com.example",
                    nodes = listOf(
                        UiNodeSnapshot(
                            id = "node_0001",
                            text = "Open",
                            contentDescription = null,
                            className = "Button",
                            bounds = UiBounds(1, 2, 3, 4),
                            enabled = true,
                            clickable = true,
                            editable = false,
                        ),
                    ),
                    truncated = false,
                ),
            ),
        )

        val result = registry.execute("device_get_ui_tree", buildJsonObject {}, ToolContext("s1"))

        assertTrue(result is ToolResult.Success)
        val json = (result as ToolResult.Success).content
        assertEquals("com.example", json.jsonObject.getValue("packageName").jsonPrimitive.content)
    }

    @Test
    fun tap_validatesCoordinatesAndForwards() = runTest {
        val gateway = FakeGateway()
        val registry = ToolRegistry()
        DeviceTools.register(registry, gateway)

        val result = registry.execute(
            "device_tap",
            buildJsonObject {
                put("x", 120)
                put("y", 240)
            },
            ToolContext("s1"),
        )

        assertTrue(result is ToolResult.Success)
        assertEquals(120f, gateway.lastTapX)
        assertEquals(240f, gateway.lastTapY)
    }

    @Test
    fun gatewayException_becomesStableToolFailure() = runTest {
        val registry = ToolRegistry()
        DeviceTools.register(
            registry,
            FakeGateway(failure = DeviceOperationException("PROTECTED_PACKAGE", "blocked")),
        )

        val result = registry.execute("device_screenshot", buildJsonObject {}, ToolContext("s1"))

        assertTrue(result is ToolResult.Failure)
        assertEquals("PROTECTED_PACKAGE", (result as ToolResult.Failure).code)
    }

    private class FakeGateway(
        private val tree: UiTreeSnapshot = UiTreeSnapshot("com.example", emptyList(), false),
        private val failure: DeviceOperationException? = null,
    ) : AccessibilityGateway {
        var lastTapX: Float? = null
        var lastTapY: Float? = null

        private fun failIfNeeded() {
            failure?.let { throw it }
        }

        override fun isConnected(): Boolean = true
        override fun currentPackage(): String = "com.example"
        override suspend fun snapshotTree(): UiTreeSnapshot {
            failIfNeeded()
            return tree
        }
        override suspend fun screenshot(): ScreenshotPayload {
            failIfNeeded()
            return ScreenshotPayload("image/png", "AA", 10, 20)
        }
        override suspend fun clickNode(nodeId: String): Boolean = true
        override suspend fun tap(x: Float, y: Float, durationMs: Long): Boolean {
            lastTapX = x
            lastTapY = y
            return true
        }
        override suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean = true
        override suspend fun inputText(text: String, nodeId: String?): Boolean = true
        override fun pressBack(): Boolean = true
        override fun pressHome(): Boolean = true
        override fun launchApp(packageName: String): Boolean = true
    }
}
