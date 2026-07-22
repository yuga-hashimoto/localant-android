package dev.localant.android.core.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface ToolContent {
    data class Text(val text: String) : ToolContent

    data class Image(
        val data: String,
        val mimeType: String,
    ) : ToolContent

    data class ResourceLink(
        val uri: String,
        val name: String,
        val mimeType: String? = null,
        val description: String? = null,
    ) : ToolContent
}

sealed class ToolResult {
    data class Success(
        val content: JsonElement,
        val contentBlocks: List<ToolContent> = emptyList(),
    ) : ToolResult()

    data class Failure(
        val code: String,
        val message: String,
        val details: JsonObject? = null,
    ) : ToolResult()
}
