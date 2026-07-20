package dev.localant.android.core.tools

import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.JsonObject

fun interface ToolHandler {
    suspend fun execute(input: JsonObject, context: ToolContext): ToolResult
}
