package dev.localant.android.persistence

import dev.localant.android.audit.AuditEntry
import dev.localant.android.core.model.RiskLevel
import dev.localant.android.persistence.PersistenceMappers.toDomain
import dev.localant.android.persistence.PersistenceMappers.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceMappersTest {
    @Test
    fun approvalEntity_roundTripsDomainFields() {
        val entity = ApprovalEntity(
            id = "approval-1",
            toolName = "device_tap",
            riskValue = 2,
            sessionId = "session-1",
            inputSummary = "x=10,y=20",
            expiresAtMs = 1234,
            approved = true,
        )

        val domain = with(PersistenceMappers) { entity.toDomain() }

        assertEquals(entity.id, domain.id)
        assertEquals(entity.toolName, domain.toolName)
        assertEquals(entity.riskValue, domain.risk.value)
        assertEquals(entity.sessionId, domain.sessionId)
        assertEquals(entity.inputSummary, domain.inputSummary)
        assertEquals(entity.expiresAtMs, domain.expiresAtMs)
    }

    @Test
    fun auditEntry_roundTripsMapWithEscapedContent() {
        val entry = AuditEntry(
            id = "audit-1",
            toolName = "shell_execute",
            risk = RiskLevel(3),
            timestampMs = 99,
            callerSessionId = "session-1",
            approvalResult = "APPROVED",
            durationMs = 12,
            success = true,
            errorCode = null,
            inputSummary = mapOf("input" to "quoted \"value\"", "other" to "line\n2"),
        )

        val entity = with(PersistenceMappers) { entry.toEntity() }
        val restored = with(PersistenceMappers) { entity.toDomain() }

        assertEquals(entry, restored)
    }

    @Test
    fun corruptAuditSummary_fallsBackToEmptyMap() {
        val entity = AuditEntity(
            id = "audit-1",
            toolName = "status",
            riskValue = 0,
            timestampMs = 1,
            callerSessionId = "s",
            approvalResult = "AUTO",
            durationMs = 1,
            success = true,
            errorCode = null,
            inputSummaryJson = "not-json",
        )

        assertTrue(with(PersistenceMappers) { entity.toDomain() }.inputSummary.isEmpty())
    }
}
