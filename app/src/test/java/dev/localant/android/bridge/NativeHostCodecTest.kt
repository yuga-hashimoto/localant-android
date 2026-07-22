package dev.localant.android.bridge

import dev.localant.android.core.model.ToolContent
import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeHostCodecTest {
    @Test
    fun successPreservesStructuredContent() {
        val encoded = NativeHostCodec.encode(
            ToolResult.Success(buildJsonObject { put("status", "ok") }),
        )
        val json = Json.parseToJsonElement(encoded).jsonObject

        assertTrue(json.getValue("success").jsonPrimitive.content.toBoolean())
        assertEquals("ok", json.getValue("content").jsonObject.getValue("status").jsonPrimitive.content)
    }

    @Test
    fun successPreservesMcpContentBlocks() {
        val encoded = NativeHostCodec.encode(
            ToolResult.Success(
                content = buildJsonObject {
                    put("mimeType", "image/png")
                    put("width", 10)
                    put("height", 20)
                },
                contentBlocks = listOf(
                    ToolContent.Image(data = "iVBORw0KGgo=", mimeType = "image/png"),
                    ToolContent.Text(text = "Android screenshot"),
                    ToolContent.ResourceLink(
                        uri = "file:///screen.png",
                        name = "screen.png",
                        mimeType = "image/png",
                    ),
                ),
            ),
        )
        val json = Json.parseToJsonElement(encoded).jsonObject
        val blocks = json.getValue("contentBlocks").jsonArray

        assertEquals("image", blocks[0].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("iVBORw0KGgo=", blocks[0].jsonObject.getValue("data").jsonPrimitive.content)
        assertEquals("image/png", blocks[0].jsonObject.getValue("mimeType").jsonPrimitive.content)
        assertEquals("text", blocks[1].jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("resource_link", blocks[2].jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun failurePreservesApprovalDetails() {
        val encoded = NativeHostCodec.encode(
            ToolResult.Failure(
                code = "APPROVAL_REQUIRED",
                message = "Approve on phone",
                details = buildJsonObject { put("approvalId", "abc") },
            ),
        )
        val json = Json.parseToJsonElement(encoded).jsonObject

        assertFalse(json.getValue("success").jsonPrimitive.content.toBoolean())
        assertEquals("APPROVAL_REQUIRED", json.getValue("code").jsonPrimitive.content)
        assertEquals("abc", json.getValue("details").jsonObject.getValue("approvalId").jsonPrimitive.content)
    }
}
