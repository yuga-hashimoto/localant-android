package dev.localant.android.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeStateMonitorTest {
    @Test
    fun publishesStateChangesUntilStopped() = runTest {
        var current = HostState(HostPhase.STARTING, message = "starting")
        val published = mutableListOf<HostState>()
        val monitor = BridgeStateMonitor(
            scope = this,
            pollIntervalMs = 100,
            stateProvider = { current },
            onState = { published.add(it) },
        )

        monitor.start()
        runCurrent()
        assertEquals(listOf(HostPhase.STARTING), published.map { it.phase })

        current = HostState(HostPhase.RUNNING, publicUrl = "https://phone.example/mcp")
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(HostPhase.STARTING, HostPhase.RUNNING), published.map { it.phase })

        monitor.stop()
        current = HostState(HostPhase.ERROR, message = "failed")
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(HostPhase.STARTING, HostPhase.RUNNING), published.map { it.phase })
    }

    @Test
    fun doesNotRepublishUnchangedState() = runTest {
        val state = HostState(HostPhase.STARTING, message = "starting")
        val published = mutableListOf<HostState>()
        val monitor = BridgeStateMonitor(
            scope = this,
            pollIntervalMs = 50,
            stateProvider = { state },
            onState = { published.add(it) },
        )

        monitor.start()
        advanceTimeBy(250)
        runCurrent()
        monitor.stop()

        assertEquals(1, published.size)
    }
}
