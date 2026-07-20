package dev.localant.android.bridge

import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.ToolHandler
import dev.localant.android.core.tools.ToolHost
import dev.localant.android.core.tools.ToolRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeTest {

    private fun testHost(): ToolHost {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("ping", "p", RiskLevel(0), buildJsonObject { put("type", "object") }),
            ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("pong")) }
        )
        return ToolHost(registry)
    }

    private fun testConfig() = NativeBridgeConfig(
        stateDir = "/tmp/test-localant",
        hostname = "test-phone",
        accessToken = "test-token-123",
    )

    @Test
    fun fakeBridge_initialStateIsStopped() {
        val bridge = FakeNativeBridge()
        assertEquals(BridgeState.STOPPED, bridge.status())
    }

    @Test
    fun fakeBridge_startTransitionsToRunning() = runTest {
        val bridge = FakeNativeBridge()
        bridge.start(testConfig(), testHost())
        assertEquals(BridgeState.RUNNING, bridge.status())
    }

    @Test
    fun fakeBridge_stopReturnsToStopped() = runTest {
        val bridge = FakeNativeBridge()
        bridge.start(testConfig(), testHost())
        bridge.stop()
        assertEquals(BridgeState.STOPPED, bridge.status())
        assertEquals("", bridge.devUrl())
    }

    @Test
    fun fakeBridge_doubleStartKeepsRunning() = runTest {
        val bridge = FakeNativeBridge()
        bridge.start(testConfig(), testHost())
        bridge.start(testConfig(), testHost())
        assertEquals(BridgeState.RUNNING, bridge.status())
    }

    @Test
    fun fakeBridge_doubleStopKeepsStopped() = runTest {
        val bridge = FakeNativeBridge()
        bridge.start(testConfig(), testHost())
        bridge.stop()
        bridge.stop()
        assertEquals(BridgeState.STOPPED, bridge.status())
    }

    @Test
    fun fakeBridge_stopBeforeStartKeepsStopped() = runTest {
        val bridge = FakeNativeBridge()
        bridge.stop()
        assertEquals(BridgeState.STOPPED, bridge.status())
    }

    @Test
    fun fakeBridge_devUrlIsDeterministic() = runTest {
        val bridge = FakeNativeBridge()
        bridge.start(testConfig(), testHost())
        assertEquals(
            "http://127.0.0.1:8787/mcp?key=test-token-123",
            bridge.devUrl(),
        )
    }

    @Test
    fun fakeBridge_exposesPassedHost() = runTest {
        val bridge = FakeNativeBridge()
        bridge.start(testConfig(), testHost())

        val result = bridge.executeForTest("ping", "{}", "session-1")
        assertTrue(result is ToolResult.Success)
        assertEquals("pong", ((result as ToolResult.Success).content as JsonPrimitive).content)
    }
}
