package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalFreePolicyTest {
    private val policy = ApprovalFreePolicy()

    @Test
    fun allRegisteredRiskLevelsRunWithoutApproval() {
        for (risk in 0..3) {
            assertEquals(
                "risk=$risk",
                ApprovalRequirement.ALWAYS_ALLOW,
                policy.requirement(RiskLevel(risk), "session", "tool"),
            )
        }
    }

    @Test
    fun riskFourRemainsDeniedBecauseDestructiveToolsAreNotRegistered() {
        assertEquals(
            ApprovalRequirement.DENY,
            policy.requirement(RiskLevel(4), "session", "tool"),
        )
    }
}
