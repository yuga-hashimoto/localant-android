package dev.localant.android.shell

import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.ToolHandler
import dev.localant.android.core.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object ShellTools {
    fun register(registry: ToolRegistry, engine: ShellEngine) {
        registry.register(
            definition = ToolDefinition(
                name = "shell_execute",
                description = "Execute a one-shot sandboxed shell command inside the LocalAnt app workspace.",
                risk = RiskLevel(3),
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "command",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "Shell command to execute under the LocalAnt app UID.")
                                },
                            )
                            put(
                                "cwd",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "Optional workspace-relative working directory.")
                                },
                            )
                            put(
                                "timeoutMs",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("minimum", 1)
                                    put("maximum", 60_000)
                                },
                            )
                            put("_approvalId", buildJsonObject { put("type", "string") })
                        },
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("command")) })
                    put("additionalProperties", false)
                },
            ),
            handler = ToolHandler { input, _ -> execute(input, engine) },
        )
    }

    private suspend fun execute(input: JsonObject, engine: ShellEngine): ToolResult {
        val command = input["command"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure(
                code = "INVALID_INPUT",
                message = "command is required and must be a non-blank string.",
            )
        val cwd = input["cwd"]?.jsonPrimitive?.contentOrNull
        val timeoutMs = input["timeoutMs"]?.jsonPrimitive?.longOrNull ?: 30_000L
        if (timeoutMs !in 1..60_000) {
            return ToolResult.Failure(
                code = "INVALID_INPUT",
                message = "timeoutMs must be between 1 and 60000.",
            )
        }

        val result = engine.execute(ShellRequest(command, cwd, timeoutMs))
        val payload = result.toJson()
        return if (result.success) {
            ToolResult.Success(payload)
        } else {
            ToolResult.Failure(
                code = result.errorCode ?: "SHELL_FAILED",
                message = result.errorMessage ?: "Shell execution failed.",
                details = payload,
            )
        }
    }

    private fun ShellResult.toJson(): JsonObject = buildJsonObject {
        put("success", success)
        exitCode?.let { put("exitCode", it) }
        put("stdout", stdout)
        put("stderr", stderr)
        put("timedOut", timedOut)
        put("outputLimited", outputLimited)
        errorCode?.let { put("errorCode", it) }
        errorMessage?.let { put("errorMessage", it) }
    }
}
