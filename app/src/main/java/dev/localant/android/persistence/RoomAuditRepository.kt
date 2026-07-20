package dev.localant.android.persistence

import dev.localant.android.audit.AuditEntry
import dev.localant.android.audit.AuditRepository
import dev.localant.android.audit.InMemoryAuditRepository
import dev.localant.android.core.model.RiskLevel
import dev.localant.android.persistence.PersistenceMappers.toDomain
import dev.localant.android.persistence.PersistenceMappers.toEntity
import java.util.UUID

/** Synchronous Room adapter. Invoke it off the Android main thread. */
class RoomAuditRepository(
    private val database: LocalAntDatabase,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : AuditRepository {
    private val dao = database.auditDao()
    private val redactor = InMemoryAuditRepository()

    override fun record(entry: AuditEntry) {
        val safe = entry.copy(
            inputSummary = entry.inputSummary.mapValues { (_, value) -> redactPlainText(value) },
        )
        dao.insert(safe.toEntity())
    }

    override fun list(): List<AuditEntry> = dao.list().map { it.toDomain() }

    override fun listByIdSubstring(idSubstring: String): AuditEntry? =
        dao.findByIdSubstring(idSubstring)?.toDomain()

    override fun recordForResult(
        toolName: String,
        risk: RiskLevel,
        callerSessionId: String,
        durationMs: Long,
        success: Boolean,
        inputSummary: String,
        errorCode: String?,
        approvalResult: String,
    ): AuditEntry {
        val entry = AuditEntry(
            id = idGenerator(),
            toolName = toolName,
            risk = risk,
            timestampMs = nowMs(),
            callerSessionId = callerSessionId,
            approvalResult = approvalResult,
            durationMs = durationMs,
            success = success,
            errorCode = errorCode,
            inputSummary = mapOf("input" to redactPlainText(inputSummary)),
        )
        dao.insert(entry.toEntity())
        return entry
    }

    override fun redactJson(input: String): String = redactor.redactJson(input)

    override fun redactPlainText(input: String): String = redactor.redactPlainText(input)
}
