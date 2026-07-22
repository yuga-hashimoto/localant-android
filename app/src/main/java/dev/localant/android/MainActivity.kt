package dev.localant.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localant.android.approval.PendingApproval
import dev.localant.android.service.HostPhase
import dev.localant.android.service.HostState
import dev.localant.android.ui.LocalAntViewModel
import dev.localant.android.ui.prepareChatGptConnectorLaunch
import dev.localant.android.ui.redactMcpUrl

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<LocalAntViewModel>()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            LocalAntTheme {
                val state by viewModel.hostState.collectAsStateWithLifecycle()
                val approvals by viewModel.approvals.collectAsStateWithLifecycle()
                val accessibilityConnected by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
                val overlayPermissionGranted by viewModel.overlayPermissionGranted.collectAsStateWithLifecycle()
                LocalAntDashboard(
                    state = state,
                    approvals = approvals,
                    accessibilityConnected = accessibilityConnected,
                    overlayPermissionGranted = overlayPermissionGranted,
                    onOpenAccessibility = ::openAccessibilitySettings,
                    onOpenOverlay = ::openOverlaySettings,
                    onOpenBattery = ::openBatterySettings,
                    onStart = viewModel::startHosting,
                    onStop = viewModel::stopHosting,
                    onOpenUrl = ::openUrl,
                    onOpenBrowser = ::openBrowserUrl,
                    onApproveOnce = { viewModel.approve(it, forSession = false) },
                    onApproveSession = { viewModel.approve(it, forSession = true) },
                    onDeny = viewModel::deny,
                    onRotateToken = viewModel::rotateToken,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Could not open the URL.", Toast.LENGTH_SHORT).show() }
    }

    private fun openBrowserUrl(url: String) {
        val uri = Uri.parse(url)
        val browserIntent = Intent.makeMainSelectorActivity(
            Intent.ACTION_MAIN,
            Intent.CATEGORY_APP_BROWSER,
        ).apply {
            data = uri
        }
        runCatching { startActivity(browserIntent) }
            .recoverCatching {
                startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            }
            .onFailure { Toast.makeText(this, "Could not open the browser.", Toast.LENGTH_SHORT).show() }
    }
}

@Composable
fun LocalAntTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun LocalAntDashboard(
    state: HostState,
    approvals: List<PendingApproval>,
    accessibilityConnected: Boolean,
    overlayPermissionGranted: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenBattery: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onApproveOnce: (String) -> Unit,
    onApproveSession: (String) -> Unit,
    onDeny: (String) -> Unit,
    onRotateToken: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("LocalAnt Android", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("This phone is the MCP host, shell environment, and Android device controller.")

        StatusCard(state)

        SetupCard(
            number = "1",
            title = "Enable Android control",
            description = if (accessibilityConnected) {
                "AccessibilityService is connected. Password fields and protected apps remain blocked."
            } else {
                "Android requires you to enable LocalAnt manually in Accessibility settings."
            },
        ) {
            OutlinedButton(onClick = onOpenAccessibility) {
                Text(if (accessibilityConnected) "Accessibility enabled" else "Open Accessibility settings")
            }
        }

        SetupCard(
            number = "2",
            title = "Allow remote app launch",
            description = if (overlayPermissionGranted) {
                "Background app launch permission is enabled. LocalAnt does not draw overlay UI."
            } else {
                "Android requires Display over other apps permission before a background service can launch apps."
            },
        ) {
            OutlinedButton(onClick = onOpenOverlay) {
                Text(if (overlayPermissionGranted) "App launch enabled" else "Open overlay permission")
            }
        }

        SetupCard(
            number = "3",
            title = "Allow reliable background hosting",
            description = "Exclude LocalAnt from aggressive battery restrictions so the MCP endpoint remains reachable.",
        ) {
            OutlinedButton(onClick = onOpenBattery) { Text("Open battery settings") }
        }

        SetupCard(
            number = "4",
            title = "Start LocalAnt",
            description = "Starting creates the on-device shell workspace and launches the embedded MCP bridge.",
        ) {
            val active = state.phase in setOf(
                HostPhase.STARTING,
                HostPhase.AUTH_REQUIRED,
                HostPhase.RUNNING,
            )
            if (active) {
                Button(onClick = onStop) { Text("Stop LocalAnt") }
            } else {
                Button(
                    onClick = onStart,
                    enabled = accessibilityConnected && overlayPermissionGranted,
                ) { Text("Start LocalAnt") }
            }
        }

        state.authUrl?.let { authUrl ->
            SetupCard(
                number = "5",
                title = "Sign in to Tailscale",
                description = "Authentication happens in your browser. The private tsnet state stays on this phone.",
            ) {
                Button(onClick = { onOpenUrl(authUrl) }) { Text("Open Tailscale sign-in") }
            }
        }

        state.publicUrl?.let { url ->
            SetupCard(
                number = "6",
                title = "Add the MCP URL to ChatGPT",
                description = redactMcpUrl(url),
            ) {
                Button(
                    onClick = {
                        val launch = prepareChatGptConnectorLaunch(url)
                        clipboard.setText(AnnotatedString(launch.clipboardText))
                        onOpenBrowser(launch.browserUrl)
                    },
                ) { Text("Copy URL & open ChatGPT") }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(url))
                    },
                ) { Text("Copy MCP URL only") }
                Text(
                    "The MCP URL is copied before ChatGPT opens. Paste it into the Server URL field.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (approvals.isNotEmpty()) {
            HorizontalDivider()
            Text("Pending approvals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            approvals.forEach { approval ->
                ApprovalCard(approval, onApproveOnce, onApproveSession, onDeny)
            }
        }

        HorizontalDivider()
        Text("Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "All registered MCP tools run immediately without local approval. Protected apps, password fields, " +
                "workspace boundaries, dangerous shell syntax, and unregistered risk 4 operations remain blocked. " +
                "Rotating the token immediately invalidates the URL and stops hosting.",
        )
        OutlinedButton(onClick = onRotateToken) { Text("Rotate MCP token") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusCard(state: HostState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Status: ${state.phase.name}", fontWeight = FontWeight.Bold)
            state.message?.let { Text(it) }
            if (state.pendingApprovals > 0) Text("${state.pendingApprovals} approval(s) waiting on this phone")
        }
    }
}

@Composable
private fun SetupCard(
    number: String,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$number. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description)
            content()
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    onApproveOnce: (String) -> Unit,
    onApproveSession: (String) -> Unit,
    onDeny: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(approval.toolName, fontWeight = FontWeight.Bold)
            Text("Risk ${approval.risk.value} · ${approval.inputSummary}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApproveOnce(approval.id) }) { Text("Allow once") }
                if (approval.risk.value <= 2) {
                    OutlinedButton(onClick = { onApproveSession(approval.id) }) { Text("Session") }
                }
                OutlinedButton(onClick = { onDeny(approval.id) }) { Text("Deny") }
            }
        }
    }
}
