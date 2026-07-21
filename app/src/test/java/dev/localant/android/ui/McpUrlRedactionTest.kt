package dev.localant.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class McpUrlRedactionTest {
    @Test
    fun redactsQueryKeyWithoutChangingEndpoint() {
        assertEquals(
            "https://phone.example.ts.net/mcp?key=••••••••",
            redactMcpUrl("https://phone.example.ts.net/mcp?key=secret-token"),
        )
    }

    @Test
    fun redactsKeyWhenOtherQueryParametersArePresent() {
        assertEquals(
            "https://phone.example.ts.net/mcp?mode=stream&key=••••••••&v=1",
            redactMcpUrl("https://phone.example.ts.net/mcp?mode=stream&key=secret-token&v=1"),
        )
    }

    @Test
    fun leavesUrlsWithoutKeyUnchanged() {
        assertEquals(
            "https://phone.example.ts.net/mcp",
            redactMcpUrl("https://phone.example.ts.net/mcp"),
        )
    }
}
