package dev.localant.android.persistence

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "approvals",
    indices = [Index("sessionId"), Index("expiresAtMs")],
    primaryKeys = ["id"],
)
data class ApprovalEntity(
    val id: String,
    val toolName: String,
    val riskValue: Int,
    val sessionId: String,
    val inputSummary: String,
    val expiresAtMs: Long,
    val approved: Boolean,
)

@Entity(
    tableName = "session_grants",
    primaryKeys = ["sessionId", "toolName"],
)
data class SessionGrantEntity(
    val sessionId: String,
    val toolName: String,
    val createdAtMs: Long,
)

@Entity(
    tableName = "audit_entries",
    indices = [Index("timestampMs"), Index("toolName")],
    primaryKeys = ["id"],
)
data class AuditEntity(
    val id: String,
    val toolName: String,
    val riskValue: Int,
    val timestampMs: Long,
    val callerSessionId: String,
    val approvalResult: String,
    val durationMs: Long,
    val success: Boolean,
    val errorCode: String?,
    val inputSummaryJson: String,
)
