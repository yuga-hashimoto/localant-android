package dev.localant.android.core.model

data class ToolContext(
    val sessionId: String,
    val caller: String = "mcp",
)
