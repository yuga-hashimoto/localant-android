package dev.localant.android.bridge

import android.content.Context
import dev.localant.android.BuildConfig

object NativeBridgeFactory {
    fun create(context: Context): NativeBridge {
        if (!BuildConfig.NATIVE_TSNET_ENABLED) return FakeNativeBridge()
        return runCatching {
            val type = Class.forName("dev.localant.android.bridge.TsnetNativeBridge")
            type.getDeclaredConstructor(Context::class.java)
                .newInstance(context.applicationContext) as NativeBridge
        }.getOrElse { error ->
            ErrorNativeBridge("Native tsnet bridge is unavailable: ${error.message}")
        }
    }

    private class ErrorNativeBridge(
        private val error: String,
    ) : NativeBridge {
        override suspend fun start(config: NativeBridgeConfig, host: dev.localant.android.core.tools.ToolHost) = Unit
        override suspend fun stop() = Unit
        override fun status(): BridgeState = BridgeState.ERROR
        override fun publicUrl(): String? = null
        override fun lastError(): String = error
    }
}
