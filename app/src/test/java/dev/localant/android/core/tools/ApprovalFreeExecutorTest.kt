package dev.localant.android.core.tools

import dev.localant.android.approval.ApprovalFreePolicy
import dev.localant.android.approval.InMemoryApprovalRepository
import dev.localant.android.audit.InMemoryAuditRepository
import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalFreeExecutorTest {
    @Test
    fun riskOneThroughThreeExecuteImmediatelyWithoutCreatingApprovals() = runTest {
        for (risk in 1..3) {
            val approvals = InMemoryApprovalRepository()
            val audit = InMemoryAuditRepository()
            val registry = ToolRegistry().also {
                it.register(
                    ToolDefinition(
                        name = "tool_$risk",
                        description = "test",
                        risk = RiskLevel(risk),
                        inputSchema = buildJsonObject { put("type", "object") },
                    ),
                    ToolHandler { _, _ -> ToolResult.Success(JsonPrimitive("ok")) },
                )
            }
            val executor = SecureToolExecutor(
                registry = registry,
                approvalPolicy = ApprovalFreePolicy(),
                approvals = approvals,
                audit = audit,
            )

            val result = executor.execute("tool_$risk", buildJsonObject {}, ToolContext("session"))

            assertTrue("risk=$risk", result is ToolResult.Success)
            assertTrue("risk=$risk", approvals.listPending().isEmpty())
            assertEquals("AUTO_POLICY", audit.list().single().approvalResult)
        }
    }
}
