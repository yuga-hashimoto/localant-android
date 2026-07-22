package dev.localant.android.integration

import dev.localant.android.accessibility.AccessibilityGateway
import dev.localant.android.accessibility.DeviceTools
import dev.localant.android.accessibility.ScreenshotPayload
import dev.localant.android.accessibility.UiTreeSnapshot
import dev.localant.android.approval.ApprovalFreePolicy
import dev.localant.android.approval.InMemoryApprovalRepository
import dev.localant.android.audit.InMemoryAuditRepository
import dev.localant.android.bridge.FakeNativeBridge
import dev.localant.android.bridge.NativeBridgeConfig
import dev.localant.android.core.model.ToolContent
import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.SecureToolExecutor
import dev.localant.android.core.tools.ToolHost
import dev.localant.android.core.tools.ToolRegistry
import dev.localant.android.security.DefaultCommandGuard
import dev.localant.android.shell.SandboxShellEngine
import dev.localant.android.shell.ShellTools
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ApprovalFreeBridgeIntegrationTest {
    @Test
    fun screenshotTapAndShellExecuteWithoutAnyApprovalRoundTrip() = runTest {
        val workspace = Files.createTempDirectory("localant-approval-free").toFile()
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
            approvalPolicy = ApprovalFreePolicy(),
            approvals = approvals,
            audit = audit,
        )
        val bridge = FakeNativeBridge()

        try {
            bridge.start(
                NativeBridgeConfig(
                    stateDir = workspace.resolve("state").path,
                    hostname = "approval-free-phone",
                    accessToken = "abcdefghijklmnopqrstuvwxyz012345",
                ),
                ToolHost(registry, executor),
            )

            val screenshot = bridge.executeForTest("device_screenshot", "{}", "session")
            assertTrue(screenshot is ToolResult.Success)
            assertEquals(
                listOf(ToolContent.Image(data = "iVBORw0KGgo=", mimeType = "image/png")),
                (screenshot as ToolResult.Success).contentBlocks,
            )
            assertTrue(
                bridge.executeForTest(
                    "device_tap",
                    "{\"x\":100,\"y\":200}",
                    "session",
                ) is ToolResult.Success,
            )
            assertTrue(
                bridge.executeForTest(
                    "shell_execute",
                    "{\"command\":\"pwd\"}",
                    "session",
                ) is ToolResult.Success,
            )

            assertTrue(approvals.listPending().isEmpty())
            assertEquals(3, audit.list().size)
            assertTrue(audit.list().all { it.approvalResult == "AUTO_POLICY" })
        } finally {
            bridge.stop()
            shell.cancelAll()
            workspace.deleteRecursively()
        }
    }

    private class FakeGateway : AccessibilityGateway {
        override fun isConnected(): Boolean = true
        override fun currentPackage(): String = "com.openai.chatgpt"
        override suspend fun snapshotTree(): UiTreeSnapshot = UiTreeSnapshot(
            packageName = "com.openai.chatgpt",
            nodes = emptyList(),
            truncated = false,
        )
        override suspend fun screenshot(): ScreenshotPayload =
            ScreenshotPayload("image/png", "iVBORw0KGgo=", 1, 1)
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
        override suspend fun launchApp(packageName: String): Boolean = true
    }
}
