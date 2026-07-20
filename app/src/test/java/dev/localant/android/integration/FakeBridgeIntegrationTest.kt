package dev.localant.android.integration

import dev.localant.android.accessibility.AccessibilityGateway
import dev.localant.android.accessibility.DeviceTools
import dev.localant.android.accessibility.ScreenshotPayload
import dev.localant.android.accessibility.UiTreeSnapshot
import dev.localant.android.approval.DefaultApprovalPolicy
import dev.localant.android.approval.InMemoryApprovalRepository
import dev.localant.android.audit.InMemoryAuditRepository
import dev.localant.android.bridge.FakeNativeBridge
import dev.localant.android.bridge.NativeBridgeConfig
import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.SecureToolExecutor
import dev.localant.android.core.tools.ToolHost
import dev.localant.android.core.tools.ToolRegistry
import dev.localant.android.security.DefaultCommandGuard
import dev.localant.android.shell.SandboxShellEngine
import dev.localant.android.shell.ShellTools
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class FakeBridgeIntegrationTest {
    @Test
    fun fakeBridge_runsReadToolAndApprovedShellEndToEnd() = runTest {
        val workspace = Files.createTempDirectory("localant-integration").toFile()
        val approvals = InMemoryApprovalRepository()
        val audit = InMemoryAuditRepository()
        val shell = SandboxShellEngine(
            workspaceRoot = workspace,
            commandGuard = DefaultCommandGuard(workspace.canonicalPath),
            shellPath = "/bin/sh",
        )
        val registry = ToolRegistry().also {
            DeviceTools.register(it, FakeGateway())
            ShellTools.register(it, shell)
        }
        val executor = SecureToolExecutor(
            registry = registry,
            approvalPolicy = DefaultApprovalPolicy(approvals),
            approvals = approvals,
            audit = audit,
        )
        val host = ToolHost(registry, executor)
        val bridge = FakeNativeBridge()

        try {
            bridge.start(
                NativeBridgeConfig(
                    stateDir = workspace.resolve("state").path,
                    hostname = "integration-phone",
                    accessToken = "abcdefghijklmnopqrstuvwxyz012345",
                ),
                host,
            )

            val status = bridge.executeForTest("device_status", "{}", "session-1")
            assertTrue(status is ToolResult.Success)
            val statusJson = (status as ToolResult.Success).content.jsonObject
            assertTrue(statusJson.getValue("accessibilityConnected").jsonPrimitive.content.toBoolean())

            val firstShell = bridge.executeForTest(
                "shell_execute",
                "{\"command\":\"pwd\"}",
                "session-1",
            )
            assertTrue(firstShell is ToolResult.Failure)
            firstShell as ToolResult.Failure
            assertEquals("APPROVAL_REQUIRED", firstShell.code)
            val approvalId = firstShell.details!!
                .getValue("approvalId")
                .jsonPrimitive
                .content
            assertTrue(approvals.approve(approvalId, sessionGrant = false))

            val approvedShell = bridge.executeForTest(
                "shell_execute",
                "{\"command\":\"pwd\",\"_approvalId\":\"$approvalId\"}",
                "session-1",
            )
            assertTrue(approvedShell is ToolResult.Success)
            val shellJson = (approvedShell as ToolResult.Success).content.jsonObject
            assertEquals(0, shellJson.getValue("exitCode").jsonPrimitive.content.toInt())
            assertEquals(workspace.canonicalPath, shellJson.getValue("stdout").jsonPrimitive.content.trim())
            assertEquals(3, audit.list().size)
        } finally {
            bridge.stop()
            shell.cancelAll()
            workspace.deleteRecursively()
        }
    }

    private class FakeGateway : AccessibilityGateway {
        override fun isConnected(): Boolean = true
        override fun currentPackage(): String = "com.example.notes"
        override suspend fun snapshotTree(): UiTreeSnapshot = UiTreeSnapshot(
            packageName = "com.example.notes",
            nodes = emptyList(),
            truncated = false,
        )
        override suspend fun screenshot(): ScreenshotPayload = ScreenshotPayload("image/png", "", 1, 1)
        override suspend fun clickNode(nodeId: String): Boolean = true
        override suspend fun tap(x: Float, y: Float, durationMs: Long): Boolean = true
        override suspend fun swipe(
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            durationMs: Long,
        ): Boolean = true
        override suspend fun inputText(text: String, nodeId: String?): Boolean = true
        override fun pressBack(): Boolean = true
        override fun pressHome(): Boolean = true
        override fun launchApp(packageName: String): Boolean = true
    }
}
