package dev.localant.android.core.tools

import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.JsonObject

class ToolRegistry : ToolExecutor {

    private val tools = mutableMapOf<String, Pair<ToolDefinition, ToolHandler>>()

    fun register(definition: ToolDefinition, handler: ToolHandler) {
        check(!tools.containsKey(definition.name)) {
            "Tool '${definition.name}' is already registered"
        }
        tools[definition.name] = definition to handler
    }

    fun findDefinition(name: String): ToolDefinition? = tools[name]?.first

    fun listDefinitions(): List<ToolDefinition> =
        tools.values
            .map { it.first }
            .sortedBy { it.name }
            .toList()

    override suspend fun execute(
        name: String,
        input: JsonObject,
        context: ToolContext,
    ): ToolResult {
        val entry = tools[name] ?: return ToolResult.Failure(
            code = "UNKNOWN_TOOL",
            message = "Tool '$name' not found",
        )
        return entry.second.execute(input, context)
    }
}
