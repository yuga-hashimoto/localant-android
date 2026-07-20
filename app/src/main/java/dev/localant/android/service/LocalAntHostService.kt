package dev.localant.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.localant.android.MainActivity
import dev.localant.android.R
import dev.localant.android.accessibility.CurrentAccessibilityGateway
import dev.localant.android.accessibility.DeviceTools
import dev.localant.android.approval.DefaultApprovalPolicy
import dev.localant.android.bridge.BridgeState
import dev.localant.android.bridge.NativeBridge
import dev.localant.android.bridge.NativeBridgeConfig
import dev.localant.android.bridge.NativeBridgeFactory
import dev.localant.android.core.tools.SecureToolExecutor
import dev.localant.android.core.tools.ToolHost
import dev.localant.android.core.tools.ToolRegistry
import dev.localant.android.runtime.LocalAntAppServices
import dev.localant.android.security.DefaultCommandGuard
import dev.localant.android.shell.SandboxShellEngine
import dev.localant.android.shell.ShellTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class LocalAntHostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bridge: NativeBridge? = null
    private var shell: SandboxShellEngine? = null
    private var approvalMonitor: Job? = null
    private var lastApprovalCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP, ACTION_PAUSE -> {
                startForeground(NOTIFICATION_ID, notification(HostState(HostPhase.STOPPED)))
                scope.launch {
                    stopHosting()
                    stopSelf()
                }
            }
            ACTION_START -> {
                val starting = HostState(HostPhase.STARTING, message = "Starting LocalAnt on this phone…")
                HostStateStore.shared.update(starting)
                startForeground(NOTIFICATION_ID, notification(starting))
                scope.launch { startHosting() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runBlocking(Dispatchers.IO) { stopHosting() }
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun startHosting() {
        if (bridge?.status() == BridgeState.RUNNING) return
        try {
            val appServices = LocalAntAppServices.get(applicationContext)
            val workspace = File(filesDir, "workspace").canonicalFile.also { it.mkdirs() }
            val shellEngine = SandboxShellEngine(
                workspaceRoot = workspace,
                commandGuard = DefaultCommandGuard(workspace.path),
            )
            val registry = ToolRegistry().also { toolRegistry ->
                ShellTools.register(toolRegistry, shellEngine)
                DeviceTools.register(toolRegistry, CurrentAccessibilityGateway())
            }
            val secureExecutor = SecureToolExecutor(
                registry = registry,
                approvalPolicy = DefaultApprovalPolicy(appServices.approvals),
                approvals = appServices.approvals,
                audit = appServices.audit,
            )
            val host = ToolHost(registry, secureExecutor)
            val nativeBridge = NativeBridgeFactory.create(applicationContext)
            shell = shellEngine
            bridge = nativeBridge
            nativeBridge.start(
                NativeBridgeConfig(
                    stateDir = File(filesDir, "tailscale").absolutePath,
                    hostname = deviceHostname(),
                    accessToken = appServices.tokenStore.current() ?: appServices.tokenStore.rotate(),
                ),
                host,
            )

            val pending = appServices.approvals.listPending().size
            publish(hostStateForBridge(nativeBridge, pending))
            startApprovalMonitor(appServices)
        } catch (error: Exception) {
            publish(
                HostState(
                    phase = HostPhase.ERROR,
                    message = error.message ?: "LocalAnt failed to start.",
                ),
            )
        }
    }

    private suspend fun stopHosting() {
        approvalMonitor?.cancel()
        approvalMonitor = null
        lastApprovalCount = 0
        getSystemService(NotificationManager::class.java).cancel(APPROVAL_NOTIFICATION_ID)
        shell?.cancelAll()
        shell = null
        runCatching { bridge?.stop() }
        bridge = null
        HostStateStore.shared.stopped()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun startApprovalMonitor(appServices: LocalAntAppServices) {
        approvalMonitor?.cancel()
        approvalMonitor = scope.launch {
            while (isActive) {
                appServices.approvals.expireStale(System.currentTimeMillis())
                val pending = appServices.approvals.listPending().size
                val current = HostStateStore.shared.state.value
                val next = bridge?.let { hostStateForBridge(it, pending) }
                    ?: current.copy(pendingApprovals = pending)
                if (current.phase != HostPhase.STOPPED && next != current) {
                    publish(next)
                }
                if (pending > 0 && pending != lastApprovalCount) {
                    getSystemService(NotificationManager::class.java).notify(
                        APPROVAL_NOTIFICATION_ID,
                        approvalNotification(pending),
                    )
                } else if (pending == 0 && lastApprovalCount > 0) {
                    getSystemService(NotificationManager::class.java).cancel(APPROVAL_NOTIFICATION_ID)
                }
                lastApprovalCount = pending
                delay(APPROVAL_POLL_MS)
            }
        }
    }

    private fun hostStateForBridge(nativeBridge: NativeBridge, pendingApprovals: Int): HostState {
        nativeBridge.authUrl()?.let { authUrl ->
            return HostState(
                phase = HostPhase.AUTH_REQUIRED,
                authUrl = authUrl,
                message = "Sign in to Tailscale to publish the MCP URL.",
                pendingApprovals = pendingApprovals,
            )
        }

        return when (nativeBridge.status()) {
            BridgeState.RUNNING -> {
                val publicUrl = nativeBridge.publicUrl()
                HostState(
                    phase = HostPhase.RUNNING,
                    publicUrl = publicUrl,
                    message = if (publicUrl?.startsWith("http://127.0.0.1") == true) {
                        "Development bridge is running. Build the native tsnet bridge for a public URL."
                    } else {
                        "This phone is available to ChatGPT through MCP."
                    },
                    pendingApprovals = pendingApprovals,
                )
            }
            BridgeState.STARTING -> HostState(
                phase = HostPhase.STARTING,
                message = "Starting Tailscale Funnel…",
                pendingApprovals = pendingApprovals,
            )
            BridgeState.ERROR -> HostState(
                phase = HostPhase.ERROR,
                message = nativeBridge.lastError() ?: "Native bridge failed to start.",
                pendingApprovals = pendingApprovals,
            )
            BridgeState.STOPPED -> HostState(
                phase = HostPhase.ERROR,
                message = nativeBridge.lastError() ?: "Bridge stopped before publishing an MCP endpoint.",
                pendingApprovals = pendingApprovals,
            )
        }
    }

    private fun publish(state: HostState) {
        HostStateStore.shared.update(state)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
    }

    private fun notification(state: HostState) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setContentTitle("LocalAnt Android")
        .setContentText(state.safeNotificationText())
        .setOngoing(state.phase != HostPhase.STOPPED && state.phase != HostPhase.ERROR)
        .setOnlyAlertOnce(true)
        .setContentIntent(activityPendingIntent())
        .addAction(0, "Pause", servicePendingIntent(ACTION_PAUSE, 2))
        .addAction(0, "Stop", servicePendingIntent(ACTION_STOP, 3))
        .build()

    private fun approvalNotification(count: Int) = NotificationCompat.Builder(this, APPROVAL_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("LocalAnt approval required")
        .setContentText("$count ChatGPT operation(s) are waiting for approval on this phone.")
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(activityPendingIntent())
        .build()

    private fun activityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, LocalAntHostService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LocalAnt hosting",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when this phone is hosting the LocalAnt MCP endpoint."
        }
        val approvalChannel = NotificationChannel(
            APPROVAL_CHANNEL_ID,
            "LocalAnt approvals",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alerts when ChatGPT requests a locally approved Android operation."
        }
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(channel, approvalChannel),
        )
    }

    private fun deviceHostname(): String {
        val raw = "localant-${Build.MODEL.orEmpty()}".lowercase()
        return raw
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
            .take(63)
            .ifBlank { "localant-android" }
    }

    companion object {
        const val ACTION_START = "dev.localant.android.action.START"
        const val ACTION_STOP = "dev.localant.android.action.STOP"
        const val ACTION_PAUSE = "dev.localant.android.action.PAUSE"
        private const val CHANNEL_ID = "localant_host"
        private const val APPROVAL_CHANNEL_ID = "localant_approvals"
        private const val NOTIFICATION_ID = 4101
        private const val APPROVAL_NOTIFICATION_ID = 4102
        private const val APPROVAL_POLL_MS = 1_000L
    }
}
