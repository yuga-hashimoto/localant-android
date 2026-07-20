package dev.localant.android.persistence

import dev.localant.android.approval.ApprovalRepository
import dev.localant.android.approval.Clock
import dev.localant.android.approval.InMemoryApprovalRepository
import dev.localant.android.approval.PendingApproval
import dev.localant.android.approval.RealClock
import dev.localant.android.core.model.RiskLevel
import dev.localant.android.persistence.PersistenceMappers.toDomain
import java.util.UUID

/**
 * Synchronous repository matching the domain contract. Call it from a bounded
 * background dispatcher; Room main-thread access is intentionally not enabled.
 */
class RoomApprovalRepository(
    private val database: LocalAntDatabase,
    private val clock: Clock = RealClock(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : ApprovalRepository {
    private val dao = database.approvalDao()

    override fun request(
        toolName: String,
        risk: RiskLevel,
        sessionId: String,
        inputSummary: String,
    ): PendingApproval {
        val entity = ApprovalEntity(
            id = idGenerator(),
            toolName = toolName,
            riskValue = risk.value,
            sessionId = sessionId,
            inputSummary = inputSummary,
            expiresAtMs = clock.nowMs() + InMemoryApprovalRepository.TTL_MS,
            approved = false,
        )
        dao.insertApproval(entity)
        return entity.toDomain()
    }

    override fun approve(id: String, sessionGrant: Boolean): Boolean {
        var approved = false
        database.runInTransaction {
            val entity = dao.findApproval(id) ?: return@runInTransaction
            val now = clock.nowMs()
            if (now >= entity.expiresAtMs) {
                dao.deleteApproval(id)
                return@runInTransaction
            }
            if (dao.markApproved(id) == 0) return@runInTransaction
            if (sessionGrant) {
                dao.insertSessionGrant(
                    SessionGrantEntity(
                        sessionId = entity.sessionId,
                        toolName = entity.toolName,
                        createdAtMs = now,
                    ),
                )
            }
            approved = true
        }
        return approved
    }

    override fun deny(id: String): Boolean = dao.deleteApproval(id) > 0

    override fun consume(id: String): PendingApproval? {
        var result: PendingApproval? = null
        database.runInTransaction {
            val entity = dao.findApproval(id) ?: return@runInTransaction
            val now = clock.nowMs()
            if (!entity.approved || now >= entity.expiresAtMs) {
                if (now >= entity.expiresAtMs) dao.deleteApproval(id)
                return@runInTransaction
            }
            if (dao.deleteApproval(id) > 0) result = entity.toDomain()
        }
        return result
    }

    override fun getSessionGrants(sessionId: String, toolName: String): Boolean =
        dao.hasSessionGrant(sessionId, toolName) > 0

    override fun expireStale(nowMs: Long) {
        dao.deleteExpired(nowMs)
    }

    override fun listPending(): List<PendingApproval> =
        dao.listApprovals().map { it.toDomain() }
}
