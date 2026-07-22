package dev.localant.android.bridge

import dev.localant.android.core.model.ToolContent
import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object NativeHostCodec {
    fun encode(result: ToolResult): String = when (result) {
        is ToolResult.Success -> buildJsonObject {
            put("success", true)
            put("content", result.content)
            if (result.contentBlocks.isNotEmpty()) {
                put(
                    "contentBlocks",
                    buildJsonArray {
                        result.contentBlocks.forEach { add(it.toJson()) }
                    },
                )
            }
        }.toString()

        is ToolResult.Failure -> buildJsonObject {
            put("success", false)
            put("code", result.code)
            put("message", result.message)
            result.details?.let { put("details", it) }
        }.toString()
    }

    private fun ToolContent.toJson(): JsonObject = when (this) {
        is ToolContent.Text -> buildJsonObject {
            put("type", "text")
            put("text", text)
        }

        is ToolContent.Image -> buildJsonObject {
            put("type", "image")
            put("data", data)
            put("mimeType", mimeType)
        }

        is ToolContent.ResourceLink -> buildJsonObject {
            put("type", "resource_link")
            put("uri", uri)
            put("name", name)
            mimeType?.let { put("mimeType", it) }
            description?.let { put("description", it) }
        }
    }
}
