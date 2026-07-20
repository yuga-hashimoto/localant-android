package dev.localant.android.accessibility

class UiTreeNormalizer(
    private val maxNodes: Int = 500,
) {
    init {
        require(maxNodes > 0) { "maxNodes must be positive." }
    }

    fun normalize(packageName: String, root: RawUiNode): NormalizedUiTree {
        val nodes = mutableListOf<UiNodeSnapshot>()
        val paths = linkedMapOf<String, List<Int>>()
        var truncated = false

        fun visit(node: RawUiNode, path: List<Int>) {
            if (node.password) return
            if (nodes.size >= maxNodes) {
                truncated = true
                return
            }
            val id = "node_${(nodes.size + 1).toString().padStart(4, '0')}"
            nodes += UiNodeSnapshot(
                id = id,
                text = node.text?.take(MAX_TEXT_LENGTH),
                contentDescription = node.contentDescription?.take(MAX_TEXT_LENGTH),
                className = node.className.take(MAX_CLASS_LENGTH),
                bounds = node.bounds,
                enabled = node.enabled,
                clickable = node.clickable,
                editable = node.editable,
            )
            paths[id] = path
            node.children.forEachIndexed { index, child ->
                if (nodes.size >= maxNodes) {
                    truncated = true
                    return@forEachIndexed
                }
                visit(child, path + index)
            }
        }

        visit(root, emptyList())
        return NormalizedUiTree(
            tree = UiTreeSnapshot(packageName, nodes.toList(), truncated),
            nodePaths = paths.toMap(),
        )
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 500
        const val MAX_CLASS_LENGTH = 200
    }
}
