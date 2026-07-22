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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.localant.android.approval.PendingApproval
import dev.localant.android.service.HostPhase
import dev.localant.android.service.HostState
import dev.localant.android.ui.LocalAntTheme
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Text(
                "LocalAnt",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Your phone is the MCP host — shell, device control, and bridge to ChatGPT.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // Status banner
            StatusBanner(state)

            Spacer(Modifier.height(4.dp))

            // Setup steps
            SectionLabel("Setup")

            SetupStep(
                icon = Icons.Filled.Accessibility,
                title = "Android control",
                isComplete = accessibilityConnected,
                completeText = "Accessibility service connected",
                pendingText = "Enable LocalAnt in Accessibility settings",
                actionLabel = if (accessibilityConnected) "Enabled" else "Open settings",
                onAction = onOpenAccessibility,
            )

            SetupStep(
                icon = Icons.Filled.Layers,
                title = "Remote app launch",
                isComplete = overlayPermissionGranted,
                completeText = "Background launch permission granted",
                pendingText = "Grant \"Display over other apps\" permission",
                actionLabel = if (overlayPermissionGranted) "Granted" else "Grant permission",
                onAction = onOpenOverlay,
            )

            SetupStep(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Background reliability",
                isComplete = false,
                completeText = "",
                pendingText = "Exclude from battery optimization for stable hosting",
                actionLabel = "Battery settings",
                onAction = onOpenBattery,
            )

            // Start / Stop
            Spacer(Modifier.height(4.dp))
            val isRunning = state.phase in setOf(HostPhase.STARTING, HostPhase.AUTH_REQUIRED, HostPhase.RUNNING)
            if (isRunning) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Stop LocalAnt", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = accessibilityConnected && overlayPermissionGranted,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start LocalAnt", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Auth URL card
            AnimatedVisibility(
                visible = state.authUrl != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                state.authUrl?.let { authUrl ->
                    ActionCard(
                        icon = Icons.Filled.Link,
                        title = "Sign in to Tailscale",
                        description = "Authenticate in your browser. Private tsnet state stays on-device.",
                    ) {
                        Button(onClick = { onOpenUrl(authUrl) }) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open sign-in")
                        }
                    }
                }
            }

            // MCP URL card
            AnimatedVisibility(
                visible = state.publicUrl != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                state.publicUrl?.let { url ->
                    ActionCard(
                        icon = Icons.Filled.OpenInBrowser,
                        title = "Connect ChatGPT",
                        description = redactMcpUrl(url),
                    ) {
                        Button(
                            onClick = {
                                val launch = prepareChatGptConnectorLaunch(url)
                                clipboard.setText(AnnotatedString(launch.clipboardText))
                                onOpenBrowser(launch.browserUrl)
                            },
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy & open ChatGPT")
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { clipboard.setText(AnnotatedString(url)) },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy URL")
                        }
                    }
                }
            }

            // Approvals
            AnimatedVisibility(
                visible = approvals.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Pending approvals")
                    approvals.forEach { approval ->
                        ApprovalCard(approval, onApproveOnce, onApproveSession, onDeny)
                    }
                }
            }

            // Security section
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(4.dp))
            SectionLabel("Security")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Protected apps, password fields, workspace boundaries, dangerous shell syntax, " +
                                "and risk-4 operations remain blocked at all times.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = onRotateToken) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rotate MCP token")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun StatusBanner(state: HostState) {
    val statusColor by animateColorAsState(
        targetValue = when (state.phase) {
            HostPhase.RUNNING -> MaterialTheme.colorScheme.tertiary
            HostPhase.STARTING, HostPhase.AUTH_REQUIRED -> MaterialTheme.colorScheme.primary
            HostPhase.ERROR -> MaterialTheme.colorScheme.error
            HostPhase.STOPPED -> MaterialTheme.colorScheme.outline
        },
        label = "statusColor",
    )

    val statusLabel = when (state.phase) {
        HostPhase.RUNNING -> "Running"
        HostPhase.STARTING -> "Starting…"
        HostPhase.AUTH_REQUIRED -> "Sign-in required"
        HostPhase.ERROR -> "Error"
        HostPhase.STOPPED -> "Stopped"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                state.message?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.pendingApprovals > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "${state.pendingApprovals}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    icon: ImageVector,
    title: String,
    isComplete: Boolean,
    completeText: String,
    pendingText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isComplete) 0.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isComplete) completeText else pendingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isComplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isComplete) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Complete",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            } else {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    actions: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
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
    val riskColor = when {
        approval.risk.value >= 3 -> MaterialTheme.colorScheme.error
        approval.risk.value == 2 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(riskColor),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    approval.toolName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = riskColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        "Risk ${approval.risk.value}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = riskColor,
                    )
                }
            }
            Text(
                approval.inputSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApproveOnce(approval.id) }) { Text("Allow once") }
                if (approval.risk.value <= 2) {
                    FilledTonalButton(onClick = { onApproveSession(approval.id) }) { Text("Session") }
                }
                OutlinedButton(onClick = { onDeny(approval.id) }) { Text("Deny") }
            }
        }
    }
}
