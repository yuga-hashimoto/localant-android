package dev.localant.android.audit

interface AuditRepository {
    fun record(entry: AuditEntry)
    fun list(): List<AuditEntry>
    fun listByIdSubstring(idSubstring: String): AuditEntry?
    fun recordForResult(
        toolName: String,
        risk: dev.localant.android.core.model.RiskLevel,
        callerSessionId: String,
        durationMs: Long,
        success: Boolean,
        inputSummary: String,
        errorCode: String? = null,
        approvalResult: String = "AUTO",
    ): AuditEntry
    fun redactJson(input: String): String
    fun redactPlainText(input: String): String
}
