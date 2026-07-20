package dev.localant.android.core.tools

import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreModelTest {

    @Test
    fun riskLevel_validatesRange() {
        assertEquals(0, RiskLevel(0).value)
        assertEquals(4, RiskLevel(4).value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun riskLevel_rejectsFive() {
        RiskLevel(5)
    }

    @Test
    fun toolDefinition_isImmutableData() {
        val schema = buildJsonObject { put("type", "object") }
        val def = ToolDefinition(
            name = "test",
            description = "a test tool",
            risk = RiskLevel(0),
            inputSchema = schema,
        )
        assertEquals("test", def.name)
        assertEquals("a test tool", def.description)
        assertEquals(RiskLevel(0), def.risk)
        assertEquals(schema, def.inputSchema)
    }

    @Test
    fun toolContext_defaultCallerIsMcp() {
        val ctx = ToolContext(sessionId = "abc-123")
        assertEquals("abc-123", ctx.sessionId)
        assertEquals("mcp", ctx.caller)
    }

    @Test
    fun toolContext_customCaller() {
        val ctx = ToolContext(sessionId = "x", caller = "admin")
        assertEquals("x", ctx.sessionId)
        assertEquals("admin", ctx.caller)
    }

    @Test
    fun toolResult_successContainsContent() {
        val content = JsonPrimitive("done")
        val result = ToolResult.Success(content)
        assertEquals(content, result.content)
    }

    @Test
    fun toolResult_failureHasCodeAndMessage() {
        val result = ToolResult.Failure(code = "ERR", message = "something went wrong")
        assertEquals("ERR", result.code)
        assertEquals("something went wrong", result.message)
        assertNull(result.details)
    }

    @Test
    fun toolResult_failureWithDetails() {
        val details = buildJsonObject { put("reason", "extra info") }
        val result = ToolResult.Failure(code = "E2", message = "ouch", details = details)
        assertEquals("E2", result.code)
        assertEquals("ouch", result.message)
        assertEquals(details, result.details)
    }
}
