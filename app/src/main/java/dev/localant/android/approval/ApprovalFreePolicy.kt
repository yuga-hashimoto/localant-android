package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel

class ApprovalFreePolicy : ApprovalPolicy {
    override fun requirement(
        risk: RiskLevel,
        sessionId: String,
        toolName: String,
    ): ApprovalRequirement = when (risk.value) {
        in 0..3 -> ApprovalRequirement.ALWAYS_ALLOW
        else -> ApprovalRequirement.DENY
    }
}
