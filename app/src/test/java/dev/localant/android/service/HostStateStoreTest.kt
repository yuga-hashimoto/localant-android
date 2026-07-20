package dev.localant.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostStateStoreTest {
    @Test
    fun initialState_isStopped() {
        val store = HostStateStore()
        assertEquals(HostPhase.STOPPED, store.state.value.phase)
        assertNull(store.state.value.publicUrl)
    }

    @Test
    fun runningState_keepsPublicUrl() {
        val store = HostStateStore()
        store.update(HostState(HostPhase.RUNNING, publicUrl = "https://phone.ts.net/mcp"))
        assertEquals(HostPhase.RUNNING, store.state.value.phase)
        assertEquals("https://phone.ts.net/mcp", store.state.value.publicUrl)
    }

    @Test
    fun stopped_clearsTransientConnectionFields() {
        val store = HostStateStore()
        store.update(
            HostState(
                phase = HostPhase.AUTH_REQUIRED,
                authUrl = "https://login.tailscale.com/a/abc",
                message = "login",
            ),
        )
        store.stopped()
        assertEquals(HostState(), store.state.value)
    }
}
