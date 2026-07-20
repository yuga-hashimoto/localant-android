package dev.localant.android.bridge

import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.ToolHost
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class FakeNativeBridge : NativeBridge {

    @Volatile
    private var currentState: BridgeState = BridgeState.STOPPED

    @Volatile
    private var currentUrl: String = ""

    private var activeHost: ToolHost? = null

    override suspend fun start(config: NativeBridgeConfig, host: ToolHost) {
        if (currentState == BridgeState.RUNNING) return

        currentState = BridgeState.STARTING
        activeHost = host
        val token = URLEncoder.encode(
            config.accessToken,
            StandardCharsets.UTF_8.toString(),
        )
        currentUrl = "http://127.0.0.1:8787/mcp?key=$token"
        currentState = BridgeState.RUNNING
    }

    override suspend fun stop() {
        activeHost = null
        currentUrl = ""
        currentState = BridgeState.STOPPED
    }

    override fun status(): BridgeState = currentState

    override fun publicUrl(): String? = currentUrl.takeIf { it.isNotBlank() }

    fun devUrl(): String = currentUrl

    internal suspend fun executeForTest(
        tool: String,
        inputJson: String,
        sessionId: String,
    ): ToolResult =
        activeHost?.executeTool(tool, inputJson, sessionId)
            ?: ToolResult.Failure(
                code = "BRIDGE_NOT_RUNNING",
                message = "The fake native bridge is not running.",
            )
}
