package dev.localant.android.shell

import dev.localant.android.security.CommandGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SandboxShellEngine(
    workspaceRoot: File,
    private val commandGuard: CommandGuard,
    private val shellPath: String = "/system/bin/sh",
    private val maxOutputBytes: Int = CommandGuard.MAX_OUTPUT_BYTES,
    maxConcurrentProcesses: Int = CommandGuard.MAX_CONCURRENT_PROCESSES,
) : ShellEngine {
    private val root = workspaceRoot.canonicalFile.also { it.mkdirs() }
    private val semaphore = Semaphore(maxConcurrentProcesses)
    private val running = Collections.synchronizedSet(mutableSetOf<Process>())

    override suspend fun execute(request: ShellRequest): ShellResult = semaphore.withPermit {
        val guard = commandGuard.validate(request.command)
        if (!guard.allowed) {
            return@withPermit rejected(guard.message ?: "Command rejected by device policy.")
        }

        val cwd = resolveCwd(request.cwd)
            ?: return@withPermit rejected("Working directory must stay inside the LocalAnt workspace.")
        if (!cwd.isDirectory) {
            return@withPermit rejected("Working directory does not exist or is not a directory.")
        }

        val process = try {
            ProcessBuilder(shellPath, "-c", request.command)
                .directory(cwd)
                .start()
        } catch (error: Exception) {
            return@withPermit ShellResult(
                success = false,
                exitCode = null,
                stdout = "",
                stderr = "",
                errorCode = "SHELL_START_FAILED",
                errorMessage = error.message ?: "Could not start the sandbox shell.",
            )
        }

        running.add(process)
        try {
            collectProcess(
                process = process,
                timeoutMs = request.timeoutMs.coerceIn(1, CommandGuard.MAX_TIMEOUT_MS),
            )
        } finally {
            running.remove(process)
            if (process.isAlive) process.destroyForcibly()
        }
    }

    override fun cancelAll() {
        val snapshot = synchronized(running) { running.toList() }
        snapshot.forEach { process ->
            if (process.isAlive) process.destroyForcibly()
        }
        running.clear()
    }

    private suspend fun collectProcess(process: Process, timeoutMs: Long): ShellResult = coroutineScope {
        val budget = OutputBudget(maxOutputBytes)
        val outputLimited = AtomicBoolean(false)
        val stdoutDeferred = async(Dispatchers.IO) {
            readBounded(process.inputStream, process, budget, outputLimited)
        }
        val stderrDeferred = async(Dispatchers.IO) {
            readBounded(process.errorStream, process, budget, outputLimited)
        }

        val finished = withContext(Dispatchers.IO) {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
        val timedOut = !finished && !outputLimited.get()
        if (!finished && process.isAlive) {
            process.destroyForcibly()
            withContext(Dispatchers.IO) { runCatching { process.waitFor() } }
        }

        val stdout = stdoutDeferred.await().toString(StandardCharsets.UTF_8)
        val stderr = stderrDeferred.await().toString(StandardCharsets.UTF_8)
        val limited = outputLimited.get()
        val actualExit = runCatching { process.exitValue() }.getOrNull()

        when {
            limited -> ShellResult(
                success = false,
                exitCode = actualExit,
                stdout = stdout,
                stderr = stderr,
                outputLimited = true,
                errorCode = "OUTPUT_LIMIT",
                errorMessage = "Combined shell output exceeded $maxOutputBytes bytes.",
            )
            timedOut -> ShellResult(
                success = false,
                exitCode = actualExit,
                stdout = stdout,
                stderr = stderr,
                timedOut = true,
                errorCode = "TIMEOUT",
                errorMessage = "Shell command exceeded the timeout.",
            )
            actualExit == 0 -> ShellResult(
                success = true,
                exitCode = actualExit,
                stdout = stdout,
                stderr = stderr,
            )
            else -> ShellResult(
                success = false,
                exitCode = actualExit,
                stdout = stdout,
                stderr = stderr,
                errorCode = "NON_ZERO_EXIT",
                errorMessage = "Shell command exited with code $actualExit.",
            )
        }
    }

    private fun resolveCwd(requested: String?): File? {
        val candidate = when {
            requested.isNullOrBlank() -> root
            File(requested).isAbsolute -> File(requested)
            else -> File(root, requested)
        }.canonicalFile
        val rootPath = root.path
        return if (candidate.path == rootPath || candidate.path.startsWith(rootPath + File.separator)) {
            candidate
        } else {
            null
        }
    }

    private fun rejected(message: String): ShellResult = ShellResult(
        success = false,
        exitCode = null,
        stdout = "",
        stderr = "",
        errorCode = CommandGuard.SHELL_REJECTED,
        errorMessage = message,
    )

    private fun readBounded(
        input: InputStream,
        process: Process,
        budget: OutputBudget,
        limited: AtomicBoolean,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val accepted = budget.reserve(read)
                if (accepted > 0) output.write(buffer, 0, accepted)
                if (accepted < read) {
                    limited.set(true)
                    if (process.isAlive) process.destroyForcibly()
                    break
                }
            }
        } catch (_: Exception) {
            // Process termination closes streams. The state flags determine the result.
        }
        return output.toByteArray()
    }

    private class OutputBudget(private val maximum: Int) {
        private var used = 0

        @Synchronized
        fun reserve(requested: Int): Int {
            val remaining = (maximum - used).coerceAtLeast(0)
            val accepted = requested.coerceAtMost(remaining)
            used += accepted
            return accepted
        }
    }
}
