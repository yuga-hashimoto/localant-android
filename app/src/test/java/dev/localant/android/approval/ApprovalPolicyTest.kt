package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApprovalPolicyTest {

    private lateinit var repository: InMemoryApprovalRepository
    private lateinit var policy: DefaultApprovalPolicy

    @Before
    fun setUp() {
        repository = InMemoryApprovalRepository()
        policy = DefaultApprovalPolicy(repository)
    }

    @Test
    fun risk0_alwaysRequiresNone() {
        assertEquals(ApprovalRequirement.NONE, policy.requirement(RiskLevel(0), "s", "tool"))
    }

    @Test
    fun risk4_alwaysDeny() {
        assertEquals(ApprovalRequirement.DENY, policy.requirement(RiskLevel(4), "s", "tool"))
    }

    @Test
    fun risk1_requiresOnceWhenNoSessionGrant() {
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(1), "s1", "tool"))
    }

    @Test
    fun risk2_requiresOnceWhenNoSessionGrant() {
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(2), "s1", "tool"))
    }

    @Test
    fun risk3_requiresOncePerCall() {
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(3), "s1", "tool"))
    }

    @Test
    fun risk1_requiresNoneWhenSessionGrantExists() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "summary")
        repository.approve(pending.id, sessionGrant = true)
        assertEquals(ApprovalRequirement.NONE, policy.requirement(RiskLevel(1), "s1", "tool"))
    }

    @Test
    fun risk2_requiresNoneWhenSessionGrantExists() {
        val pending = repository.request("tool", RiskLevel(2), "s1", "summary")
        repository.approve(pending.id, sessionGrant = true)
        assertEquals(ApprovalRequirement.NONE, policy.requirement(RiskLevel(2), "s1", "tool"))
    }

    @Test
    fun risk3_requiresOnceEvenWithSessionGrant() {
        val pending = repository.request("tool", RiskLevel(3), "s1", "summary")
        repository.approve(pending.id, sessionGrant = true)
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(3), "s1", "tool"))
    }

    @Test
    fun risk1_requiresOnceWhenGrantWasNonSession() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "summary")
        repository.approve(pending.id, sessionGrant = false)
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(1), "s1", "tool"))
    }

    @Test
    fun risk2_requiresOnceWhenGrantWasNonSession() {
        val pending = repository.request("tool", RiskLevel(2), "s1", "summary")
        repository.approve(pending.id, sessionGrant = false)
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(2), "s1", "tool"))
    }

    @Test
    fun sessionGrantDoesNotCrossSessions() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "summary")
        repository.approve(pending.id, sessionGrant = true)
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(1), "s2", "tool"))
    }

    @Test
    fun sessionGrantForOneTool_doesNotAuthorizeAnotherToolAtSameRisk() {
        val pending = repository.request("tool_a", RiskLevel(2), "s1", "summary")
        repository.approve(pending.id, sessionGrant = true)
        assertEquals(ApprovalRequirement.ONCE, policy.requirement(RiskLevel(2), "s1", "tool_b"))
    }

    @Test
    fun sessionGrantAuthorizesSameToolAgain() {
        val pending = repository.request("tool_a", RiskLevel(2), "s1", "summary")
        repository.approve(pending.id, sessionGrant = true)
        assertEquals(ApprovalRequirement.NONE, policy.requirement(RiskLevel(2), "s1", "tool_a"))
    }

    @Test
    fun differentToolsCanHaveIndependentGrants() {
        repository.request("tool_a", RiskLevel(2), "s1", "summary")
            .also { repository.approve(it.id, sessionGrant = true) }
        repository.request("tool_b", RiskLevel(2), "s1", "summary")
            .also { repository.approve(it.id, sessionGrant = true) }
        assertEquals(ApprovalRequirement.NONE, policy.requirement(RiskLevel(2), "s1", "tool_a"))
        assertEquals(ApprovalRequirement.NONE, policy.requirement(RiskLevel(2), "s1", "tool_b"))
    }
}
