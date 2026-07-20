package dev.localant.android.core.tools

import dev.localant.android.approval.ApprovalPolicy
import dev.localant.android.approval.ApprovalRepository
import dev.localant.android.approval.ApprovalRequirement
import dev.localant.android.audit.AuditRepository
import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SecureToolExecutor(
    private val registry: ToolRegistry,
    private val approvalPolicy: ApprovalPolicy,
    private val approvals: ApprovalRepository,
    private val audit: AuditRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ToolExecutor {
    override suspend fun execute(
        name: String,
        input: JsonObject,
        context: ToolContext,
    ): ToolResult {
        val definition = registry.findDefinition(name)
            ?: return registry.execute(name, input, context)
        val startedAt = nowMs()
        val approvalId = input[APPROVAL_ID_FIELD]
            ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        val cleanInput = JsonObject(input.filterKeys { it != APPROVAL_ID_FIELD })

        val approvalResult = if (approvalId != null) {
            val pending = approvals.find(approvalId)
            if (pending == null || pending.toolName != name || pending.sessionId != context.sessionId) {
                return auditedFailure(
                    definition.risk.value,
                    name,
                    context,
                    cleanInput,
                    startedAt,
                    "INVALID_APPROVAL",
                    "The approval does not match this tool and session.",
                    "INVALID",
                )
            }
            if (approvals.consume(approvalId) == null) {
                return auditedFailure(
                    definition.risk.value,
                    name,
                    context,
                    cleanInput,
                    startedAt,
                    "INVALID_APPROVAL",
                    "The approval is not approved or has expired.",
                    "INVALID",
                )
            }
            "APPROVED"
        } else {
            when (approvalPolicy.requirement(definition.risk, context.sessionId, name)) {
                ApprovalRequirement.DENY -> {
                    return auditedFailure(
                        definition.risk.value,
                        name,
                        context,
                        cleanInput,
                        startedAt,
                        "POLICY_DENIED",
                        "This tool is denied by the device policy.",
                        "DENIED",
                    )
                }
                ApprovalRequirement.ONCE -> {
                    val pending = approvals.request(
                        toolName = name,
                        risk = definition.risk,
                        sessionId = context.sessionId,
                        inputSummary = audit.redactJson(cleanInput.toString()),
                    )
                    val failure = ToolResult.Failure(
                        code = "APPROVAL_REQUIRED",
                        message = "Approve this operation on the Android device, then retry with _approvalId.",
                        details = buildJsonObject {
                            put("approvalId", pending.id)
                            put("expiresAtMs", pending.expiresAtMs)
                        },
                    )
                    audit.recordForResult(
                        toolName = name,
                        risk = definition.risk,
                        callerSessionId = context.sessionId,
                        durationMs = elapsed(startedAt),
                        success = false,
                        inputSummary = audit.redactJson(cleanInput.toString()),
                        errorCode = failure.code,
                        approvalResult = "PENDING",
                    )
                    return failure
                }
                ApprovalRequirement.NONE -> if (definition.risk.value == 0) "AUTO" else "SESSION"
            }
        }

        val result = runCatching {
            registry.execute(name, cleanInput, context)
        }.getOrElse { error ->
            ToolResult.Failure(
                code = "INTERNAL",
                message = error.message ?: "Tool execution failed.",
            )
        }
        val errorCode = (result as? ToolResult.Failure)?.code
        audit.recordForResult(
            toolName = name,
            risk = definition.risk,
            callerSessionId = context.sessionId,
            durationMs = elapsed(startedAt),
            success = result is ToolResult.Success,
            inputSummary = audit.redactJson(cleanInput.toString()),
            errorCode = errorCode,
            approvalResult = approvalResult,
        )
        return result
    }

    private fun auditedFailure(
        riskValue: Int,
        name: String,
        context: ToolContext,
        input: JsonObject,
        startedAt: Long,
        code: String,
        message: String,
        approvalResult: String,
    ): ToolResult.Failure {
        val failure = ToolResult.Failure(code, message)
        audit.recordForResult(
            toolName = name,
            risk = dev.localant.android.core.model.RiskLevel(riskValue),
            callerSessionId = context.sessionId,
            durationMs = elapsed(startedAt),
            success = false,
            inputSummary = audit.redactJson(input.toString()),
            errorCode = code,
            approvalResult = approvalResult,
        )
        return failure
    }

    private fun elapsed(startedAt: Long): Long = (nowMs() - startedAt).coerceAtLeast(0)

    private companion object {
        const val APPROVAL_ID_FIELD = "_approvalId"
    }
}
