package dev.localant.android.accessibility

data class ScreenshotPayload(
    val mimeType: String,
    val base64Data: String,
    val width: Int,
    val height: Int,
)

class DeviceOperationException(
    val code: String,
    override val message: String,
) : Exception(message)

interface AccessibilityGateway {
    fun isConnected(): Boolean
    fun currentPackage(): String?
    suspend fun snapshotTree(): UiTreeSnapshot
    suspend fun screenshot(): ScreenshotPayload
    suspend fun clickNode(nodeId: String): Boolean
    suspend fun tap(x: Float, y: Float, durationMs: Long = 50): Boolean
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean
    suspend fun inputText(text: String, nodeId: String? = null): Boolean
    fun pressBack(): Boolean
    fun pressHome(): Boolean
    fun launchApp(packageName: String): Boolean
}
