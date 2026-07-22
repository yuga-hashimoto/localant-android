package dev.localant.android.accessibility

internal class UiSnapshotStore {
    @Volatile
    private var snapshot: Snapshot? = null

    fun replace(packageName: String, nodePaths: Map<String, List<Int>>) {
        snapshot = Snapshot(
            packageName = packageName,
            nodePaths = nodePaths,
        )
    }

    fun invalidate() {
        snapshot = null
    }

    fun resolve(nodeId: String, currentPackage: String): List<Int> {
        val currentSnapshot = snapshot
            ?: throw DeviceOperationException(
                "STALE_UI_SNAPSHOT",
                "No current UI snapshot is available. Call device_get_ui_tree again.",
            )
        if (currentSnapshot.packageName != currentPackage) {
            throw DeviceOperationException(
                "STALE_UI_SNAPSHOT",
                "The UI snapshot belongs to ${currentSnapshot.packageName}, not $currentPackage.",
            )
        }
        return currentSnapshot.nodePaths[nodeId]
            ?: throw DeviceOperationException(
                "NODE_NOT_FOUND",
                "Node $nodeId is not present in the most recent UI snapshot.",
            )
    }

    private data class Snapshot(
        val packageName: String,
        val nodePaths: Map<String, List<Int>>,
    )
}
