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
