package dev.localant.android.security

interface CommandGuard {
    fun validate(command: String): GuardResult

    companion object {
        const val SHELL_REJECTED = "SHELL_REJECTED"
        const val MAX_TIMEOUT_MS = 60_000L
        const val MAX_OUTPUT_BYTES = 512 * 1024
        const val MAX_CONCURRENT_PROCESSES = 2
    }
}

data class GuardResult(
    val allowed: Boolean,
    val code: String? = null,
    val message: String? = null,
)
