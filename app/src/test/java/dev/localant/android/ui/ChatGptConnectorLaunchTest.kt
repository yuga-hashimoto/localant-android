package dev.localant.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatGptConnectorLaunchTest {
    @Test
    fun copiesMcpUrlAndOpensConnectorCreationPage() {
        val mcpUrl = "https://phone.example.ts.net/mcp?key=secret"

        val launch = prepareChatGptConnectorLaunch(mcpUrl)

        assertEquals(mcpUrl, launch.clipboardText)
        assertEquals(
            "https://chatgpt.com/plugins#settings/Connectors?create-connector=true",
            launch.browserUrl,
        )
    }
}
