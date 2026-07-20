package dev.localant.android.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HostPhase {
    STOPPED,
    STARTING,
    AUTH_REQUIRED,
    RUNNING,
    ERROR,
}

data class HostState(
    val phase: HostPhase = HostPhase.STOPPED,
    val publicUrl: String? = null,
    val authUrl: String? = null,
    val message: String? = null,
    val pendingApprovals: Int = 0,
)

fun HostState.safeNotificationText(): String = when (phase) {
    HostPhase.STOPPED -> "LocalAnt is stopped."
    HostPhase.STARTING -> "Starting Tailscale Funnel…"
    HostPhase.AUTH_REQUIRED -> "Tailscale sign-in is required."
    HostPhase.RUNNING -> "MCP endpoint is running."
    HostPhase.ERROR -> message ?: "LocalAnt encountered an error."
}

class HostStateStore(initial: HostState = HostState()) {
    private val mutableState = MutableStateFlow(initial)
    val state: StateFlow<HostState> = mutableState.asStateFlow()

    fun update(state: HostState) {
        mutableState.value = state
    }

    fun stopped() {
        mutableState.value = HostState()
    }

    companion object {
        val shared = HostStateStore()
    }
}
