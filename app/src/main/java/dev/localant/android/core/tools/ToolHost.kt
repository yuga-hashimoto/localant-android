package dev.localant.android.core.tools

import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ToolHost(
    private val registry: ToolRegistry,
    private val executor: ToolExecutor = registry,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun listToolsJson(): JsonElement =
        buildJsonArray {
            registry.listDefinitions().forEach { definition ->
                add(
                    buildJsonObject {
                        put("name", definition.name)
                        put("description", definition.description)
                        put("risk", definition.risk.value)
                        put("inputSchema", definition.inputSchema)
                    }
                )
            }
        }

    suspend fun executeTool(
        tool: String,
        inputJson: String,
        sessionId: String,
    ): ToolResult {
        val parsed = try {
            json.parseToJsonElement(inputJson)
        } catch (error: Exception) {
            return ToolResult.Failure(
                code = "INVALID_INPUT",
                message = "Failed to parse input JSON: ${error.message}",
            )
        }

        val input = parsed as? JsonObject
            ?: return ToolResult.Failure(
                code = "INVALID_INPUT",
                message = "Tool input must be a JSON object.",
            )

        return executor.execute(
            name = tool,
            input = input,
            context = ToolContext(sessionId = sessionId),
        )
    }
}
