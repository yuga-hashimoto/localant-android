package dev.localant.android.approval

import dev.localant.android.core.model.RiskLevel
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface Clock {
    fun nowMs(): Long
}

class InMemoryApprovalRepository(
    private val clock: Clock = RealClock(),
) : ApprovalRepository {

    private val pendingApprovals = ConcurrentHashMap<String, PendingApproval>()
    private val approvedIds = ConcurrentHashMap<String, Boolean>()
    private val sessionGrantsStore = ConcurrentHashMap<String, MutableSet<String>>()

    override fun request(
        toolName: String,
        risk: RiskLevel,
        sessionId: String,
        inputSummary: String,
    ): PendingApproval {
        val id = UUID.randomUUID().toString()
        val approval = PendingApproval(
            id = id,
            toolName = toolName,
            risk = risk,
            sessionId = sessionId,
            inputSummary = inputSummary,
            expiresAtMs = clock.nowMs() + TTL_MS,
        )
        pendingApprovals[id] = approval
        return approval
    }

    override fun approve(id: String, sessionGrant: Boolean): Boolean {
        val pending = pendingApprovals[id] ?: return false
        if (clock.nowMs() >= pending.expiresAtMs) return false
        approvedIds[id] = true
        if (sessionGrant) {
            sessionGrantsStore.getOrPut(pending.sessionId) { mutableSetOf() }
                .add(pending.toolName)
        }
        return true
    }

    override fun deny(id: String): Boolean {
        if (!pendingApprovals.containsKey(id)) return false
        pendingApprovals.remove(id)
        return true
    }

    override fun consume(id: String): PendingApproval? {
        val pending = pendingApprovals[id] ?: return null
        if (!approvedIds.containsKey(id)) return null
        if (clock.nowMs() >= pending.expiresAtMs) {
            pendingApprovals.remove(id)
            approvedIds.remove(id)
            return null
        }
        pendingApprovals.remove(id)
        approvedIds.remove(id)
        return pending
    }

    override fun getSessionGrants(sessionId: String, toolName: String): Boolean =
        sessionGrantsStore[sessionId]?.contains(toolName) ?: false

    override fun expireStale(nowMs: Long) {
        pendingApprovals.values.removeAll { it.expiresAtMs <= nowMs }
        approvedIds.keys.removeAll { key ->
            pendingApprovals[key] == null
        }
    }

    override fun listPending(): List<PendingApproval> =
        pendingApprovals.values.toList()

    companion object {
        const val TTL_MS = 60_000L
    }
}

class RealClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
