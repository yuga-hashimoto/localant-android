package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel

data class PendingApproval(
    val id: String,
    val toolName: String,
    val risk: RiskLevel,
    val sessionId: String,
    val inputSummary: String,
    val expiresAtMs: Long,
    val sessionGrant: Boolean = false,
)
