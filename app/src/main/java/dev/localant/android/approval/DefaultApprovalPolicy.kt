package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel

class DefaultApprovalPolicy(
    private val repository: ApprovalRepository,
) : ApprovalPolicy {

    override fun requirement(
        risk: RiskLevel,
        sessionId: String,
        toolName: String,
    ): ApprovalRequirement {
        return when (risk.value) {
            0 -> ApprovalRequirement.NONE
            4 -> ApprovalRequirement.DENY
            1, 2 -> {
                if (repository.getSessionGrants(sessionId, toolName)) ApprovalRequirement.NONE
                else ApprovalRequirement.ONCE
            }
            3 -> ApprovalRequirement.ONCE
            else -> ApprovalRequirement.DENY
        }
    }
}
