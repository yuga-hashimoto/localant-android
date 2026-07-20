package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel

interface ApprovalPolicy {
    fun requirement(risk: RiskLevel, sessionId: String, toolName: String): ApprovalRequirement
}

interface ApprovalRepository {
    fun request(
        toolName: String,
        risk: RiskLevel,
        sessionId: String,
        inputSummary: String,
    ): PendingApproval

    fun find(id: String): PendingApproval?
    fun approve(id: String, sessionGrant: Boolean): Boolean
    fun deny(id: String): Boolean
    fun consume(id: String): PendingApproval?
    fun getSessionGrants(sessionId: String, toolName: String): Boolean
    fun expireStale(nowMs: Long)
    fun listPending(): List<PendingApproval>
}
