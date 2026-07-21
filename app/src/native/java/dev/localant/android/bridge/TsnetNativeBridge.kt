package dev.localant.android.bridge

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import dev.localant.android.core.tools.ToolHost
import dev.localant.nativebridge.nativebridge.Bridge
import dev.localant.nativebridge.nativebridge.Host
import dev.localant.nativebridge.nativebridge.Nativebridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TsnetNativeBridge(context: Context) : NativeBridge {
    private val applicationContext = context.applicationContext
    private val networkStateProvider = AndroidNetworkStateProvider(applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var nativeBridge: Bridge? = null

    @Volatile
    private var hostCallback: Host? = null

    @Volatile
    private var startupError: String? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkSnapshot: AndroidNetworkSnapshot? = null

    override suspend fun start(config: NativeBridgeConfig, host: ToolHost) = mutex.withLock {
        if (nativeBridge != null) return@withLock

        val callback = object : Host {
            override fun listToolsJSON(): String = host.listToolsJson().toString()

            override fun executeTool(tool: String, inputJSON: String, sessionID: String): String =
                runBlocking {
                    NativeHostCodec.encode(
                        host.executeTool(
                            tool = tool,
                            inputJson = inputJSON,
                            sessionId = sessionID,
                        ),
                    )
                }
        }
        val bridge = Nativebridge.newBridge(callback)
        hostCallback = callback
        nativeBridge = bridge
        startupError = null

        try {
            updateNativeNetworkState(bridge, force = true)
            withContext(Dispatchers.IO) {
                bridge.start(config.stateDir, config.hostname, config.accessToken)
            }
            networkCallback = networkStateProvider.registerDefaultNetworkCallback {
                scope.launch { refreshNetworkState() }
            }
        } catch (error: Exception) {
            startupError = error.message ?: "The native tsnet bridge failed to start."
            networkStateProvider.unregister(networkCallback)
            networkCallback = null
            nativeBridge = null
            hostCallback = null
            lastNetworkSnapshot = null
            throw error
        }
    }

    override suspend fun stop() = mutex.withLock {
        networkStateProvider.unregister(networkCallback)
        networkCallback = null
        val bridge = nativeBridge
        nativeBridge = null
        hostCallback = null
        lastNetworkSnapshot = null
        startupError = null
        if (bridge != null) {
            withContext(Dispatchers.IO) { bridge.stop() }
        }
        scope.cancel()
    }

    override fun status(): BridgeState = when {
        startupError != null -> BridgeState.ERROR
        else -> when (nativeBridge?.status()) {
            "RUNNING" -> BridgeState.RUNNING
            "STARTING" -> BridgeState.STARTING
            "AUTH_REQUIRED" -> BridgeState.STOPPED
            "ERROR" -> BridgeState.ERROR
            else -> BridgeState.STOPPED
        }
    }

    override fun publicUrl(): String? = nativeBridge
        ?.publicURL()
        ?.takeIf { it.isNotBlank() }

    override fun authUrl(): String? = nativeBridge
        ?.authURL()
        ?.takeIf { it.isNotBlank() }

    override fun lastError(): String? = startupError
        ?: nativeBridge?.lastError()?.takeIf { it.isNotBlank() }

    private fun updateNativeNetworkState(bridge: Bridge, force: Boolean = false) {
        val state = networkStateProvider.snapshot()
        if (!force && state == lastNetworkSnapshot) return

        bridge.updateNetworkState(
            state.interfacesJson,
            state.defaultInterface,
            state.gateway,
        )
        lastNetworkSnapshot = state
    }

    private suspend fun refreshNetworkState() = mutex.withLock {
        val bridge = nativeBridge ?: return@withLock
        runCatching { updateNativeNetworkState(bridge) }
            .onFailure { error ->
                Log.w(
                    "LocalAntTsnet",
                    "Could not refresh Android network state; keeping the current Funnel alive.",
                    error,
                )
            }
    }

}
