package dev.localant.android.persistence

import dev.localant.android.approval.PendingApproval
import dev.localant.android.audit.AuditEntry
import dev.localant.android.core.model.RiskLevel
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal object PersistenceMappers {
    private val json = Json
    private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun ApprovalEntity.toDomain(): PendingApproval = PendingApproval(
        id = id,
        toolName = toolName,
        risk = RiskLevel(riskValue),
        sessionId = sessionId,
        inputSummary = inputSummary,
        expiresAtMs = expiresAtMs,
    )

    fun AuditEntry.toEntity(): AuditEntity = AuditEntity(
        id = id,
        toolName = toolName,
        riskValue = risk.value,
        timestampMs = timestampMs,
        callerSessionId = callerSessionId,
        approvalResult = approvalResult,
        durationMs = durationMs,
        success = success,
        errorCode = errorCode,
        inputSummaryJson = json.encodeToString(stringMapSerializer, inputSummary),
    )

    fun AuditEntity.toDomain(): AuditEntry = AuditEntry(
        id = id,
        toolName = toolName,
        risk = RiskLevel(riskValue),
        timestampMs = timestampMs,
        callerSessionId = callerSessionId,
        approvalResult = approvalResult,
        durationMs = durationMs,
        success = success,
        errorCode = errorCode,
        inputSummary = runCatching {
            json.decodeFromString(stringMapSerializer, inputSummaryJson)
        }.getOrDefault(emptyMap()),
    )
}
