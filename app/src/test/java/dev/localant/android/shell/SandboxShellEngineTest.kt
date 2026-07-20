package dev.localant.android.shell

import dev.localant.android.security.DefaultCommandGuard
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class SandboxShellEngineTest {
    private lateinit var workspace: java.io.File
    private lateinit var engine: SandboxShellEngine

    @Before
    fun setUp() {
        workspace = Files.createTempDirectory("localant-shell").toFile()
        engine = SandboxShellEngine(
            workspaceRoot = workspace,
            commandGuard = DefaultCommandGuard(workspace.canonicalPath),
            shellPath = "/bin/sh",
            maxOutputBytes = 256,
            maxConcurrentProcesses = 2,
        )
    }

    @After
    fun tearDown() {
        engine.cancelAll()
        workspace.deleteRecursively()
    }

    @Test
    fun harmlessCommand_executesInsideWorkspace() = runTest {
        val result = engine.execute(ShellRequest(command = "pwd"))

        assertTrue(result.success)
        assertEquals(0, result.exitCode)
        assertEquals(workspace.canonicalPath, result.stdout.trim())
        assertFalse(result.timedOut)
        assertFalse(result.outputLimited)
    }

    @Test
    fun stdoutAndStderr_areCaptured() = runTest {
        val result = engine.execute(ShellRequest(command = "echo out; ls definitely-missing"))

        assertFalse(result.success)
        assertTrue(result.stdout.contains("out"))
        assertTrue(result.stderr.contains("definitely-missing"))
    }

    @Test
    fun cwdOutsideWorkspace_isRejected() = runTest {
        val result = engine.execute(ShellRequest(command = "pwd", cwd = "/tmp"))

        assertFalse(result.success)
        assertEquals("SHELL_REJECTED", result.errorCode)
    }

    @Test
    fun guardedCommand_isRejectedBeforeProcessStart() = runTest {
        val result = engine.execute(ShellRequest(command = "cat /etc/passwd"))

        assertFalse(result.success)
        assertEquals("SHELL_REJECTED", result.errorCode)
    }

    @Test
    fun timeout_killsProcess() = runTest {
        val result = engine.execute(ShellRequest(command = "sleep 2", timeoutMs = 50))

        assertFalse(result.success)
        assertTrue(result.timedOut)
        assertEquals("TIMEOUT", result.errorCode)
    }

    @Test
    fun combinedOutputLimit_killsProcess() = runTest {
        val result = engine.execute(
            ShellRequest(command = "yes x", timeoutMs = 2_000),
        )

        assertFalse(result.success)
        assertTrue(result.outputLimited)
        assertEquals("OUTPUT_LIMIT", result.errorCode)
        assertTrue(result.stdout.toByteArray().size <= 256)
    }

    @Test
    fun relativeCwdWithinWorkspace_isAllowed() = runTest {
        java.io.File(workspace, "nested").mkdirs()

        val result = engine.execute(ShellRequest(command = "pwd", cwd = "nested"))

        assertTrue(result.success)
        assertEquals(java.io.File(workspace, "nested").canonicalPath, result.stdout.trim())
    }
}
