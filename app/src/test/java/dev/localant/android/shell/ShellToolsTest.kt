package dev.localant.android.shell

import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.ToolRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellToolsTest {
    @Test
    fun register_exposesRiskThreeShellTool() {
        val registry = ToolRegistry()
        ShellTools.register(registry, FakeShellEngine())

        val definition = registry.findDefinition("shell_execute")!!

        assertEquals(3, definition.risk.value)
        assertEquals("object", definition.inputSchema.getValue("type").jsonPrimitive.content)
        assertTrue(
            definition.inputSchema.getValue("properties").jsonObject.containsKey("_approvalId"),
        )
    }

    @Test
    fun execute_forwardsValidatedRequest() = runTest {
        val engine = FakeShellEngine(
            result = ShellResult(true, 0, "ok\n", ""),
        )
        val registry = ToolRegistry()
        ShellTools.register(registry, engine)

        val result = registry.execute(
            "shell_execute",
            buildJsonObject {
                put("command", "pwd")
                put("cwd", "nested")
                put("timeoutMs", 1234)
            },
            ToolContext("s1"),
        )

        assertTrue(result is ToolResult.Success)
        assertEquals(ShellRequest("pwd", "nested", 1234), engine.request)
    }

    @Test
    fun execute_missingCommand_returnsInvalidInput() = runTest {
        val registry = ToolRegistry()
        ShellTools.register(registry, FakeShellEngine())

        val result = registry.execute("shell_execute", buildJsonObject {}, ToolContext("s1"))

        assertTrue(result is ToolResult.Failure)
        assertEquals("INVALID_INPUT", (result as ToolResult.Failure).code)
    }

    @Test
    fun execute_engineFailure_preservesErrorAndOutputDetails() = runTest {
        val registry = ToolRegistry()
        ShellTools.register(
            registry,
            FakeShellEngine(
                ShellResult(
                    success = false,
                    exitCode = 2,
                    stdout = "partial",
                    stderr = "failed",
                    errorCode = "NON_ZERO_EXIT",
                    errorMessage = "exit 2",
                ),
            ),
        )

        val result = registry.execute(
            "shell_execute",
            buildJsonObject { put("command", "false") },
            ToolContext("s1"),
        )

        assertTrue(result is ToolResult.Failure)
        result as ToolResult.Failure
        assertEquals("NON_ZERO_EXIT", result.code)
        assertEquals("partial", result.details!!.getValue("stdout").jsonPrimitive.content)
        assertEquals("failed", result.details!!.getValue("stderr").jsonPrimitive.content)
    }

    private class FakeShellEngine(
        private val result: ShellResult = ShellResult(true, 0, "", ""),
    ) : ShellEngine {
        var request: ShellRequest? = null

        override suspend fun execute(request: ShellRequest): ShellResult {
            this.request = request
            return result
        }

        override fun cancelAll() = Unit
    }
}
