package dev.localant.android.audit

import dev.localant.android.core.model.RiskLevel

data class AuditEntry(
    val id: String,
    val toolName: String,
    val risk: RiskLevel,
    val timestampMs: Long,
    val callerSessionId: String,
    val approvalResult: String,
    val durationMs: Long,
    val success: Boolean,
    val errorCode: String?,
    val inputSummary: Map<String, String>,
)
