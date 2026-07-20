package dev.localant.android.shell

data class ShellRequest(
    val command: String,
    val cwd: String? = null,
    val timeoutMs: Long = 30_000,
)

data class ShellResult(
    val success: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val outputLimited: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

interface ShellEngine {
    suspend fun execute(request: ShellRequest): ShellResult
    fun cancelAll()
}
