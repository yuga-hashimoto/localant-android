package dev.localant.android.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BridgeStateMonitor(
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 1_000L,
    private val stateProvider: () -> HostState,
    private val onState: (HostState) -> Unit,
) {
    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch {
            var lastPublished: HostState? = null
            while (isActive) {
                val current = stateProvider()
                if (current != lastPublished) {
                    onState(current)
                    lastPublished = current
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
