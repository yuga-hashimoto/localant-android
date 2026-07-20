package dev.localant.android.bridge

import dev.localant.android.core.tools.ToolHost

interface NativeBridge {
    suspend fun start(config: NativeBridgeConfig, host: ToolHost)
    suspend fun stop()
    fun status(): BridgeState
    fun publicUrl(): String?
    fun authUrl(): String? = null
    fun lastError(): String? = null
}
