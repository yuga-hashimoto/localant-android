package dev.localant.android.core.tools

import dev.localant.android.approval.DefaultApprovalPolicy
import dev.localant.android.approval.InMemoryApprovalRepository
import dev.localant.android.approval.TestClock
import dev.localant.android.audit.InMemoryAuditRepository
import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolContext
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureToolExecutorTest {
    private fun schema() = buildJsonObject { put("type", "object") }

    @Test
    fun riskZero_executesWithoutApprovalAndAudits() = runTest {
        val fixture = fixture(risk = 0)

        val result = fixture.executor.execute("test_tool", buildJsonObject {}, ToolContext("s1"))

        assertTrue(result is ToolResult.Success)
        assertEquals(1, fixture.executions)
        assertEquals(1, fixture.audit.list().size)
        assertEquals("AUTO", fixture.audit.list().single().approvalResult)
    }

    @Test
    fun riskThree_withoutApproval_returnsApprovalIdWithoutExecuting() = runTest {
        val fixture = fixture(risk = 3)

        val result = fixture.executor.execute("test_tool", buildJsonObject { put("command", "id") }, ToolContext("s1"))

        assertTrue(result is ToolResult.Failure)
        result as ToolResult.Failure
        assertEquals("APPROVAL_REQUIRED", result.code)
        assertTrue(result.details?.get("approvalId")?.jsonPrimitive?.content?.isNotBlank() == true)
        assertEquals(0, fixture.executions)
        assertEquals(1, fixture.approvals.listPending().size)
    }

    @Test
    fun approvedOnce_retryConsumesMatchingApprovalAndStripsControlField() = runTest {
        val fixture = fixture(risk = 3)
        val first = fixture.executor.execute("test_tool", buildJsonObject { put("value", "safe") }, ToolContext("s1"))
        val approvalId = (first as ToolResult.Failure).details!!.getValue("approvalId").jsonPrimitive.content
        assertTrue(fixture.approvals.approve(approvalId, sessionGrant = false))

        val second = fixture.executor.execute(
            "test_tool",
            buildJsonObject {
                put("value", "safe")
                put("_approvalId", approvalId)
            },
            ToolContext("s1"),
        )

        assertTrue(second is ToolResult.Success)
        assertEquals(1, fixture.executions)
        assertFalse(fixture.lastInput!!.containsKey("_approvalId"))
        assertEquals("APPROVED", fixture.audit.list().last().approvalResult)
    }

    @Test
    fun approvalCannotCrossToolOrSession() = runTest {
        val fixture = fixture(risk = 3)
        val first = fixture.executor.execute("test_tool", buildJsonObject {}, ToolContext("s1"))
        val approvalId = (first as ToolResult.Failure).details!!.getValue("approvalId").jsonPrimitive.content
        fixture.approvals.approve(approvalId, sessionGrant = false)

        val wrongSession = fixture.executor.execute(
            "test_tool",
            buildJsonObject { put("_approvalId", approvalId) },
            ToolContext("s2"),
        )

        assertTrue(wrongSession is ToolResult.Failure)
        assertEquals("INVALID_APPROVAL", (wrongSession as ToolResult.Failure).code)
        assertEquals(0, fixture.executions)
    }

    @Test
    fun sessionGrant_onlyAllowsSameRiskTwoTool() = runTest {
        val fixture = fixture(risk = 2)
        val first = fixture.executor.execute("test_tool", buildJsonObject {}, ToolContext("s1"))
        val approvalId = (first as ToolResult.Failure).details!!.getValue("approvalId").jsonPrimitive.content
        fixture.approvals.approve(approvalId, sessionGrant = true)

        val result = fixture.executor.execute("test_tool", buildJsonObject {}, ToolContext("s1"))

        assertTrue(result is ToolResult.Success)
        assertEquals(1, fixture.executions)
        assertEquals("SESSION", fixture.audit.list().last().approvalResult)
    }

    @Test
    fun sensitiveInput_isRedactedInAudit() = runTest {
        val fixture = fixture(risk = 0)

        fixture.executor.execute(
            "test_tool",
            buildJsonObject { put("authorization", "Bearer top-secret") },
            ToolContext("s1"),
        )

        val summary = fixture.audit.list().single().inputSummary.getValue("input")
        assertFalse(summary.contains("top-secret"))
    }

    private fun fixture(risk: Int): Fixture {
        val clock = TestClock(1_000)
        val approvals = InMemoryApprovalRepository(clock)
        val audit = InMemoryAuditRepository()
        val registry = ToolRegistry()
        var executions = 0
        var lastInput: kotlinx.serialization.json.JsonObject? = null
        registry.register(
            ToolDefinition("test_tool", "test", RiskLevel(risk), schema()),
            ToolHandler { input, _ ->
                executions++
                lastInput = input
                ToolResult.Success(JsonPrimitive("ok"))
            },
        )
        val executor = SecureToolExecutor(
            registry = registry,
            approvalPolicy = DefaultApprovalPolicy(approvals),
            approvals = approvals,
            audit = audit,
            nowMs = clock::nowMs,
        )
        return Fixture(executor, approvals, audit, { executions }, { lastInput })
    }

    private class Fixture(
        val executor: SecureToolExecutor,
        val approvals: InMemoryApprovalRepository,
        val audit: InMemoryAuditRepository,
        private val executionCount: () -> Int,
        private val input: () -> kotlinx.serialization.json.JsonObject?,
    ) {
        val executions: Int get() = executionCount()
        val lastInput: kotlinx.serialization.json.JsonObject? get() = input()
    }
}
