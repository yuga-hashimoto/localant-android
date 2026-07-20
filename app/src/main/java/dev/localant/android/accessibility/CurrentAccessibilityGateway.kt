package dev.localant.android.accessibility

class CurrentAccessibilityGateway : AccessibilityGateway {
    private fun service(): LocalAntAccessibilityService =
        LocalAntAccessibilityService.current()
            ?: throw DeviceOperationException(
                "ACCESSIBILITY_DISABLED",
                "Enable LocalAnt Android in Accessibility settings before using device tools.",
            )

    override fun isConnected(): Boolean = LocalAntAccessibilityService.current() != null
    override fun currentPackage(): String? = LocalAntAccessibilityService.current()?.currentPackage()
    override suspend fun snapshotTree(): UiTreeSnapshot = service().snapshotTree()
    override suspend fun screenshot(): ScreenshotPayload = service().screenshot()
    override suspend fun clickNode(nodeId: String): Boolean = service().clickNode(nodeId)
    override suspend fun tap(x: Float, y: Float, durationMs: Long): Boolean = service().tap(x, y, durationMs)
    override suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean =
        service().swipe(x1, y1, x2, y2, durationMs)
    override suspend fun inputText(text: String, nodeId: String?): Boolean = service().inputText(text, nodeId)
    override fun pressBack(): Boolean = service().pressBack()
    override fun pressHome(): Boolean = service().pressHome()
    override fun launchApp(packageName: String): Boolean = service().launchApp(packageName)
}
