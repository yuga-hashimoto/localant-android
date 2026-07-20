package dev.localant.android.core.tools

import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    private fun schema() = buildJsonObject { put("type", "object") }

    private fun handlerReturning(result: ToolResult): ToolHandler =
        ToolHandler { _, _ -> result }

    @Test
    fun registerAndList_sortedByName() = runTest {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("b_tool", "B", RiskLevel(1), schema()),
            handlerReturning(ToolResult.Success(JsonPrimitive("ok")))
        )
        registry.register(
            ToolDefinition("a_tool", "A", RiskLevel(1), schema()),
            handlerReturning(ToolResult.Success(JsonPrimitive("ok")))
        )
        registry.register(
            ToolDefinition("c_tool", "C", RiskLevel(1), schema()),
            handlerReturning(ToolResult.Success(JsonPrimitive("ok")))
        )

        val defs = registry.listDefinitions()
        assertEquals(3, defs.size)
        assertEquals("a_tool", defs[0].name)
        assertEquals("b_tool", defs[1].name)
        assertEquals("c_tool", defs[2].name)
    }

    @Test(expected = IllegalStateException::class)
    fun register_duplicateName_rejected() = runTest {
        val registry = ToolRegistry()
        val handler = handlerReturning(ToolResult.Success(JsonPrimitive("ok")))
        registry.register(ToolDefinition("dup", "D", RiskLevel(1), schema()), handler)
        registry.register(ToolDefinition("dup", "D2", RiskLevel(1), schema()), handler)
    }

    @Test
    fun execute_unknownTool_returnsUnknownToolError() = runTest {
        val registry = ToolRegistry()
        val result = registry.execute(
            "nonexistent",
            buildJsonObject {},
            ToolContext("s1")
        )
        assertTrue(result is ToolResult.Failure)
        val f = result as ToolResult.Failure
        assertEquals("UNKNOWN_TOOL", f.code)
    }

    @Test
    fun execute_knownTool_forwardsToHandler() = runTest {
        val registry = ToolRegistry()
        val expectedResult = ToolResult.Success(JsonPrimitive("hello"))
        registry.register(
            ToolDefinition("greet", "g", RiskLevel(1), schema()),
            handlerReturning(expectedResult)
        )

        val result = registry.execute("greet", buildJsonObject {}, ToolContext("s1"))
        assertEquals(expectedResult, result)
    }

    @Test
    fun execute_forwardsInputAndContextToHandler() = runTest {
        val registry = ToolRegistry()
        var capturedInput: JsonObject? = null
        var capturedContext: ToolContext? = null
        val handler = ToolHandler { input, context ->
            capturedInput = input
            capturedContext = context
            ToolResult.Success(JsonPrimitive("ok"))
        }
        val inputJson = buildJsonObject { put("key", "value") }
        val context = ToolContext("sid-1", caller = "test")

        registry.register(
            ToolDefinition("capture", "c", RiskLevel(1), schema()),
            handler
        )
        registry.execute("capture", inputJson, context)

        assertNotNull(capturedInput)
        assertEquals("value", capturedInput!!["key"]?.let {
            (it as? JsonPrimitive)?.content
        })
        assertNotNull(capturedContext)
        assertEquals("sid-1", capturedContext!!.sessionId)
        assertEquals("test", capturedContext!!.caller)
    }

    @Test
    fun listDefinitions_isDefensiveCopy() = runTest {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("only", "o", RiskLevel(1), schema()),
            handlerReturning(ToolResult.Success(JsonPrimitive("ok")))
        )
        val defs = registry.listDefinitions()
        assertEquals(1, defs.size)
        assertEquals(1, registry.listDefinitions().size)
    }
}
