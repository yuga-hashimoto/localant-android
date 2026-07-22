package dev.localant.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

class LocalAntAccessibilityService : AccessibilityService(), AccessibilityGateway {
    private val normalizer = UiTreeNormalizer()
    private val protectedPackages = ProtectedPackagePolicy()
    private val foregroundWindowTracker = ForegroundWindowTracker()
    private val uiSnapshotStore = UiSnapshotStore()

    override fun onServiceConnected() {
        super.onServiceConnected()
        currentRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            uiSnapshotStore.invalidate()
            foregroundWindowTracker.observe(event.packageName?.toString())
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (currentRef.get() === this) currentRef.clear()
        super.onDestroy()
    }

    override fun isConnected(): Boolean = currentRef.get() === this

    override fun currentPackage(): String? =
        foregroundWindowTracker.currentPackage(
            rootPackage = rootInActiveWindow?.packageName?.toString(),
        )

    override suspend fun snapshotTree(): UiTreeSnapshot {
        val (root, packageName) = requireActiveRoot()
        ensureNotProtected(packageName)
        val raw = toRawNode(root, depth = 0, budget = NodeBudget(MAX_RAW_NODES))
        val normalized = normalizer.normalize(packageName, raw)
        uiSnapshotStore.replace(packageName, normalized.nodePaths)
        return normalized.tree
    }

    override suspend fun screenshot(): ScreenshotPayload {
        ensureNotProtected(currentPackage())
        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        try {
                            val wrapped = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                ?: throw DeviceOperationException(
                                    "SCREENSHOT_UNAVAILABLE",
                                    "Android returned an unreadable screenshot buffer.",
                                )
                            val width = wrapped.width
                            val height = wrapped.height
                            val bitmap = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                            wrapped.recycle()
                            val output = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            bitmap.recycle()
                            val bytes = output.toByteArray()
                            if (bytes.size > MAX_SCREENSHOT_BYTES) {
                                throw DeviceOperationException(
                                    "OUTPUT_LIMIT",
                                    "Screenshot exceeds the 4 MiB device limit.",
                                )
                            }
                            if (continuation.isActive) {
                                continuation.resume(
                                    ScreenshotPayload(
                                        mimeType = "image/png",
                                        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                        width = width,
                                        height = height,
                                    ),
                                )
                            }
                        } catch (error: Exception) {
                            if (continuation.isActive) {
                                val deviceError = error as? DeviceOperationException
                                    ?: DeviceOperationException(
                                        "SCREENSHOT_UNAVAILABLE",
                                        error.message ?: "Could not encode the Android screenshot.",
                                    )
                                continuation.resumeWith(Result.failure(deviceError))
                            }
                        } finally {
                            hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resumeWith(
                                Result.failure(
                                    DeviceOperationException(
                                        "SCREENSHOT_UNAVAILABLE",
                                        "Android screenshot failed with code $errorCode.",
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }

    override suspend fun clickNode(nodeId: String): Boolean {
        val (root, packageName) = requireActiveRoot()
        ensureNotProtected(packageName)
        val path = uiSnapshotStore.resolve(nodeId, currentPackage = packageName)
        val node = resolveNode(root, path)
            ?: throw DeviceOperationException("NODE_NOT_FOUND", "The Android UI changed before the node could be clicked.")
        return node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override suspend fun tap(x: Float, y: Float, durationMs: Long): Boolean {
        ensureNotProtected(currentPackage())
        if (x < 0 || y < 0) throw DeviceOperationException("INVALID_INPUT", "Tap coordinates must be non-negative.")
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, durationMs.coerceIn(1, 10_000))
    }

    override suspend fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
    ): Boolean {
        ensureNotProtected(currentPackage())
        if (minOf(x1, y1, x2, y2) < 0) {
            throw DeviceOperationException("INVALID_INPUT", "Swipe coordinates must be non-negative.")
        }
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatch(path, durationMs.coerceIn(1, 10_000))
    }

    override suspend fun inputText(text: String, nodeId: String?): Boolean {
        val (root, packageName) = requireActiveRoot()
        ensureNotProtected(packageName)
        val node = if (nodeId != null) {
            val path = uiSnapshotStore.resolve(nodeId, currentPackage = packageName)
            resolveNode(root, path)
        } else {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } ?: throw DeviceOperationException("NODE_NOT_FOUND", "No editable Android node is available.")

        if (!node.isEditable || node.isPassword) {
            throw DeviceOperationException("PROTECTED_INPUT", "Text can only be set on a non-password editable node.")
        }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override fun pressBack(): Boolean {
        ensureNotProtected(currentPackage())
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun pressHome(): Boolean {
        ensureNotProtected(currentPackage())
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override suspend fun launchApp(packageName: String): Boolean {
        ensureNotProtected(packageName)
        AppLaunchPolicy.requireAllowed(
            deviceLocked = getSystemService(KeyguardManager::class.java).isDeviceLocked,
            overlayPermissionGranted = Settings.canDrawOverlays(this),
        )
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: throw DeviceOperationException("APP_NOT_FOUND", "No launchable app exists for $packageName.")
        intent.addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        startActivity(intent)
        val launched = AppLaunchMonitor(
            currentPackage = ::currentPackage,
            nowUptimeMs = SystemClock::uptimeMillis,
        ).awaitForeground(
            packageName = packageName,
            timeoutMs = APP_LAUNCH_TIMEOUT_MS,
            pollIntervalMs = APP_LAUNCH_POLL_MS,
        )
        if (!launched) {
            throw DeviceOperationException(
                "APP_LAUNCH_BLOCKED",
                "Android accepted the launch request for $packageName but did not bring it to the foreground.",
            )
        }
        return true
    }

    private fun requireRoot(): AccessibilityNodeInfo =
        rootInActiveWindow
            ?: throw DeviceOperationException(
                "ACCESSIBILITY_UNAVAILABLE",
                "Accessibility is enabled but no active Android window is available.",
            )

    private fun requireActiveRoot(): Pair<AccessibilityNodeInfo, String> {
        val root = requireRoot()
        val packageName = foregroundWindowTracker.requireMatchingRoot(
            rootPackage = root.packageName?.toString(),
        )
        return root to packageName
    }

    private fun ensureNotProtected(packageName: String?) {
        if (protectedPackages.isProtected(packageName)) {
            throw DeviceOperationException(
                "PROTECTED_PACKAGE",
                "LocalAnt refuses to inspect or control this protected application.",
            )
        }
    }

    private fun toRawNode(node: AccessibilityNodeInfo, depth: Int, budget: NodeBudget): RawUiNode {
        val rect = Rect().also(node::getBoundsInScreen)
        val children = if (depth >= MAX_TREE_DEPTH || !budget.take()) {
            emptyList()
        } else {
            buildList {
                for (index in 0 until node.childCount) {
                    if (budget.remaining <= 0) break
                    node.getChild(index)?.let { child ->
                        add(toRawNode(child, depth + 1, budget))
                    }
                }
            }
        }
        return RawUiNode(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            className = node.className?.toString().orEmpty(),
            bounds = UiBounds(rect.left, rect.top, rect.right, rect.bottom),
            enabled = node.isEnabled,
            clickable = node.isClickable,
            editable = node.isEditable,
            password = node.isPassword,
            children = children,
        )
    }

    private fun resolveNode(root: AccessibilityNodeInfo, path: List<Int>): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = root
        path.forEach { childIndex ->
            current = current?.getChild(childIndex)
            if (current == null) return null
        }
        return current
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null,
            )
            if (!accepted && continuation.isActive) continuation.resume(false)
        }

    private class NodeBudget(maximum: Int) {
        var remaining: Int = maximum
            private set

        fun take(): Boolean {
            if (remaining <= 0) return false
            remaining--
            return true
        }
    }

    companion object {
        private const val MAX_RAW_NODES = 600
        private const val MAX_TREE_DEPTH = 50
        private const val MAX_SCREENSHOT_BYTES = 4 * 1024 * 1024
        private const val APP_LAUNCH_TIMEOUT_MS = 3_000L
        private const val APP_LAUNCH_POLL_MS = 100L

        @Volatile
        private var currentRef = WeakReference<LocalAntAccessibilityService>(null)

        fun current(): LocalAntAccessibilityService? = currentRef.get()
    }
}
