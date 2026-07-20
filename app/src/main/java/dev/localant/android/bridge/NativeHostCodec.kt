package dev.localant.android.bridge

import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object NativeHostCodec {
    fun encode(result: ToolResult): String = when (result) {
        is ToolResult.Success -> buildJsonObject {
            put("success", true)
            put("content", result.content)
        }.toString()

        is ToolResult.Failure -> buildJsonObject {
            put("success", false)
            put("code", result.code)
            put("message", result.message)
            result.details?.let { put("details", it) }
        }.toString()
    }
}
