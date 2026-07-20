package dev.localant.android.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTreeNormalizerTest {
    @Test
    fun normalize_assignsStableTraversalIdsAndPaths() {
        val root = node(
            text = "root",
            children = listOf(
                node(text = "first"),
                node(text = "second", children = listOf(node(text = "nested"))),
            ),
        )

        val result = UiTreeNormalizer(maxNodes = 10).normalize("com.example", root)

        assertEquals(listOf("node_0001", "node_0002", "node_0003", "node_0004"), result.tree.nodes.map { it.id })
        assertEquals(emptyList<Int>(), result.nodePaths.getValue("node_0001"))
        assertEquals(listOf(1, 0), result.nodePaths.getValue("node_0004"))
    }

    @Test
    fun passwordNodesAndTheirText_areOmitted() {
        val root = node(
            text = "login",
            children = listOf(
                node(text = "visible-user", editable = true),
                node(text = "super-secret", password = true, editable = true),
            ),
        )

        val result = UiTreeNormalizer().normalize("com.example", root)

        assertEquals(2, result.tree.nodes.size)
        assertFalse(result.tree.nodes.any { it.text == "super-secret" })
    }

    @Test
    fun normalize_enforcesNodeLimit() {
        val root = node(children = (1..20).map { node(text = "item-$it") })

        val result = UiTreeNormalizer(maxNodes = 5).normalize("com.example", root)

        assertEquals(5, result.tree.nodes.size)
        assertTrue(result.tree.truncated)
    }

    @Test
    fun normalize_preservesSafeNodeMetadata() {
        val root = node(
            text = "Open",
            contentDescription = "Open settings",
            className = "android.widget.Button",
            bounds = UiBounds(1, 2, 101, 42),
            clickable = true,
            enabled = true,
        )

        val node = UiTreeNormalizer().normalize("com.example", root).tree.nodes.single()

        assertEquals("Open", node.text)
        assertEquals("Open settings", node.contentDescription)
        assertEquals("android.widget.Button", node.className)
        assertEquals(UiBounds(1, 2, 101, 42), node.bounds)
        assertTrue(node.clickable)
        assertTrue(node.enabled)
    }

    private fun node(
        text: String? = null,
        contentDescription: String? = null,
        className: String = "android.view.View",
        bounds: UiBounds = UiBounds(0, 0, 10, 10),
        enabled: Boolean = true,
        clickable: Boolean = false,
        editable: Boolean = false,
        password: Boolean = false,
        children: List<RawUiNode> = emptyList(),
    ) = RawUiNode(
        text = text,
        contentDescription = contentDescription,
        className = className,
        bounds = bounds,
        enabled = enabled,
        clickable = clickable,
        editable = editable,
        password = password,
        children = children,
    )
}
