package dev.localant.android.accessibility

data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class RawUiNode(
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val bounds: UiBounds,
    val enabled: Boolean,
    val clickable: Boolean,
    val editable: Boolean,
    val password: Boolean,
    val children: List<RawUiNode>,
)

data class UiNodeSnapshot(
    val id: String,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val bounds: UiBounds,
    val enabled: Boolean,
    val clickable: Boolean,
    val editable: Boolean,
)

data class UiTreeSnapshot(
    val packageName: String,
    val nodes: List<UiNodeSnapshot>,
    val truncated: Boolean,
)

data class NormalizedUiTree(
    val tree: UiTreeSnapshot,
    val nodePaths: Map<String, List<Int>>,
)
