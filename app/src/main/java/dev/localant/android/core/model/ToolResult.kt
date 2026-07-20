package dev.localant.android.core.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class ToolResult {
    data class Success(val content: JsonElement) : ToolResult()

    data class Failure(
        val code: String,
        val message: String,
        val details: JsonObject? = null,
    ) : ToolResult()
}
