package dev.localant.android.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val risk: RiskLevel,
    val inputSchema: JsonObject,
)
