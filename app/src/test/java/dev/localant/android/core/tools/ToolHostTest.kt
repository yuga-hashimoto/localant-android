package dev.localant.android.core.tools

import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolHostTest {

    private fun schema() = buildJsonObject { put("type", "object") }

    @Test
    fun listToolsJson_returnsJsonArray() = runTest {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("t1", "Tool One", RiskLevel(0), schema()),
            ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("ok")) }
        )
        val host = ToolHost(registry)

        val json = host.listToolsJson()
        assertTrue(json is JsonArray)
        assertEquals(1, (json as JsonArray).size)
    }

    @Test
    fun listToolsJson_containsToolNamesInOrder() = runTest {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("b_tool", "B", RiskLevel(0), schema()),
            ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("ok")) }
        )
        registry.register(
            ToolDefinition("a_tool", "A", RiskLevel(0), schema()),
            ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("ok")) }
        )
        val host = ToolHost(registry)

        val array = host.listToolsJson() as JsonArray
        assertEquals(2, array.size)
        val first = array[0] as JsonObject
        val second = array[1] as JsonObject
        assertEquals("a_tool", (first["name"] as JsonPrimitive).content)
        assertEquals("b_tool", (second["name"] as JsonPrimitive).content)
    }

    @Test
    fun listToolsJson_includesRequiredFields() = runTest {
        val registry = ToolRegistry()
        val inputSchema = buildJsonObject { put("type", "object") }
        registry.register(
            ToolDefinition("status", "Device status", RiskLevel(0), inputSchema),
            ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("ok")) }
        )
        val host = ToolHost(registry)

        val obj = (host.listToolsJson() as JsonArray)[0] as JsonObject
        assertTrue(obj.containsKey("name"))
        assertTrue(obj.containsKey("description"))
        assertTrue(obj.containsKey("risk"))
        assertTrue(obj.containsKey("inputSchema"))
    }

    @Test
    fun executeTool_validJson_callsHandler() = runTest {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("echo", "Echo", RiskLevel(0), schema()),
            ToolHandler { input, _ ->
                ToolResult.Success(input["message"] ?: JsonPrimitive("no message"))
            }
        )
        val host = ToolHost(registry)

        val result = host.executeTool("echo", """{"message":"hi"}""", "s1")
        assertTrue(result is ToolResult.Success)
        assertEquals("hi", ((result as ToolResult.Success).content as JsonPrimitive).content)
    }

    @Test
    fun executeTool_unknownTool_returnsFailure() = runTest {
        val host = ToolHost(ToolRegistry())

        val result = host.executeTool("missing", "{}", "s1")
        assertTrue(result is ToolResult.Failure)
        assertEquals("UNKNOWN_TOOL", (result as ToolResult.Failure).code)
    }

    @Test
    fun executeTool_malformedJson_returnsFailure() = runTest {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("t", "T", RiskLevel(0), schema()),
            ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("ok")) }
        )
        val host = ToolHost(registry)

        val result = host.executeTool("t", "{bad json}", "s1")
        assertTrue(result is ToolResult.Failure)
        assertEquals("INVALID_INPUT", (result as ToolResult.Failure).code)
    }

    @Test
    fun executeTool_nonObjectJson_returnsFailure() = runTest {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("t", "T", RiskLevel(0), schema()),
            ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("ok")) }
        )
        val host = ToolHost(registry)

        val result = host.executeTool("t", "[]", "s1")
        assertTrue(result is ToolResult.Failure)
        assertEquals("INVALID_INPUT", (result as ToolResult.Failure).code)
    }

    @Test
    fun executeTool_emptyObject_forwardsObject() = runTest {
        val registry = ToolRegistry()
        var receivedInput: JsonObject? = null
        registry.register(
            ToolDefinition("t", "T", RiskLevel(0), schema()),
            ToolHandler { input, _ ->
                receivedInput = input
                ToolResult.Success(JsonPrimitive("ok"))
            }
        )
        val host = ToolHost(registry)

        host.executeTool("t", "{}", "s1")
        assertTrue(receivedInput != null)
        assertTrue(receivedInput is JsonObject)
    }
}
