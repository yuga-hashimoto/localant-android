package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApprovalRepositoryTest {

    private lateinit var clock: TestClock
    private lateinit var repository: InMemoryApprovalRepository

    @Before
    fun setUp() {
        clock = TestClock(1_000_000L)
        repository = InMemoryApprovalRepository(clock)
    }

    @Test
    fun request_returnsPendingApprovalWithFields() {
        val pending = repository.request("shell_execute", RiskLevel(3), "s1", "{\"cmd\":\"ls\"}")
        assertNotNull(pending.id)
        assertEquals("shell_execute", pending.toolName)
        assertEquals(RiskLevel(3), pending.risk)
        assertEquals("s1", pending.sessionId)
        assertEquals("{\"cmd\":\"ls\"}", pending.inputSummary)
        assertEquals(1_060_000L, pending.expiresAtMs)
        assertFalse(pending.sessionGrant)
    }

    @Test
    fun approve_validId_returnsTrueAndHidesFromPendingUi() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        assertTrue(repository.approve(pending.id, sessionGrant = false))
        assertTrue(repository.listPending().isEmpty())
        assertEquals(pending, repository.find(pending.id))
    }

    @Test
    fun approve_unknownId_returnsFalse() {
        assertFalse(repository.approve("nonexistent", sessionGrant = false))
    }

    @Test
    fun approve_setsSessionGrant() {
        val pending = repository.request("tool", RiskLevel(2), "s1", "x")
        repository.approve(pending.id, sessionGrant = true)
        assertTrue(repository.getSessionGrants("s1", "tool"))
    }

    @Test
    fun approve_noSessionGrant_doesNotSetGrant() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        repository.approve(pending.id, sessionGrant = false)
        assertFalse(repository.getSessionGrants("s1", "tool"))
    }

    @Test
    fun deny_validId_returnsTrue() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        assertTrue(repository.deny(pending.id))
    }

    @Test
    fun deny_unknownId_returnsFalse() {
        assertFalse(repository.deny("nonexistent"))
    }

    @Test
    fun consume_beforeApprove_returnsNull() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        assertNull(repository.consume(pending.id))
    }

    @Test
    fun consume_afterApprove_returnsAndRemoves() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        repository.approve(pending.id, sessionGrant = false)
        val consumed = repository.consume(pending.id)
        assertNotNull(consumed)
        assertEquals(pending.id, consumed!!.id)
        assertNull(repository.consume(pending.id))
    }

    @Test
    fun consume_afterDeny_returnsNull() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        repository.deny(pending.id)
        assertNull(repository.consume(pending.id))
    }

    @Test
    fun consume_expiredApproval_returnsNull() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        repository.approve(pending.id, sessionGrant = false)
        clock.advance(60_001)
        assertNull(repository.consume(pending.id))
    }

    @Test
    fun expireStale_removesExpiredPendingApprovals() {
        repository.request("tool", RiskLevel(1), "s1", "x")
        clock.advance(60_001)
        repository.expireStale(clock.nowMs())
        assertEquals(0, repository.listPending().size)
    }

    @Test
    fun getSessionGrants_emptyInitially() {
        assertFalse(repository.getSessionGrants("s1", "tool"))
    }

    @Test
    fun request_usesInjectedClockForTtl() {
        val pending = repository.request("t", RiskLevel(1), "s", "x")
        assertEquals(clock.nowMs() + 60_000, pending.expiresAtMs)
    }

    @Test
    fun approve_expiredApproval_returnsFalseAndNoSessionGrant() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        clock.advance(60_001)
        assertFalse(repository.approve(pending.id, sessionGrant = true))
        assertFalse(repository.getSessionGrants("s1", "tool"))
    }

    @Test
    fun approve_atExactlyExpiresAtMs_returnsFalse() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        clock.advance(60_000)
        assertFalse(repository.approve(pending.id, sessionGrant = false))
    }

    @Test
    fun consume_atExactlyExpiresAtMs_returnsNull() {
        val pending = repository.request("tool", RiskLevel(1), "s1", "x")
        repository.approve(pending.id, sessionGrant = false)
        clock.advance(60_000)
        assertNull(repository.consume(pending.id))
    }

    @Test
    fun expireStale_removesEntriesAtCutoff() {
        repository.request("tool", RiskLevel(1), "s1", "x")
        clock.advance(60_000)
        repository.expireStale(clock.nowMs())
        assertEquals(0, repository.listPending().size)
    }

    @Test
    fun getSessionGrants_onlyAuthorizedForGrantedTool() {
        val pending = repository.request("tool_a", RiskLevel(2), "s1", "x")
        repository.approve(pending.id, sessionGrant = true)
        assertTrue(repository.getSessionGrants("s1", "tool_a"))
        assertFalse(repository.getSessionGrants("s1", "tool_b"))
    }

    @Test
    fun getSessionGrants_falseForUnknownSession() {
        assertFalse(repository.getSessionGrants("unknown_session", "tool_a"))
    }
}
