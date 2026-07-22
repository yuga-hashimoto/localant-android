package dev.localant.android.accessibility

import dev.localant.android.core.model.RiskLevel
import dev.localant.android.core.model.ToolDefinition
import dev.localant.android.core.model.ToolResult
import dev.localant.android.core.tools.ToolHandler
import dev.localant.android.core.tools.ToolRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object DeviceTools {
    fun register(registry: ToolRegistry, gateway: AccessibilityGateway) {
        registerReadTools(registry, gateway)
        registerInteractionTools(registry, gateway)
    }

    private fun registerReadTools(registry: ToolRegistry, gateway: AccessibilityGateway) {
        registry.register(
            definition("device_status", "Get LocalAnt Accessibility connection status.", 0),
            ToolHandler { _, _ ->
                safeCall {
                    ToolResult.Success(
                        buildJsonObject {
                            put("accessibilityConnected", gateway.isConnected())
                            gateway.currentPackage()?.let { put("currentPackage", it) }
                        },
                    )
                }
            },
        )
        registry.register(
            definition("device_capabilities", "List Android device-control capabilities.", 0),
            ToolHandler { _, _ ->
                ToolResult.Success(
                    buildJsonObject {
                        put("uiTree", gateway.isConnected())
                        put("screenshot", gateway.isConnected())
                        put("gestures", gateway.isConnected())
                        put("textInput", gateway.isConnected())
                        put("sandboxShell", true)
                    },
                )
            },
        )
        registry.register(
            definition("device_current_app", "Get the foreground Android package name.", 0),
            ToolHandler { _, _ ->
                ToolResult.Success(
                    buildJsonObject {
                        gateway.currentPackage()?.let { put("packageName", it) }
                    },
                )
            },
        )
        registry.register(
            definition("device_get_ui_tree", "Get a bounded, password-redacted UI tree.", 0),
            ToolHandler { _, _ -> safeCall { ToolResult.Success(gateway.snapshotTree().toJson()) } },
        )
        registry.register(
            definition("device_screenshot", "Capture the current Android screen as PNG.", 1),
            ToolHandler { _, _ ->
                safeCall {
                    val screenshot = gateway.screenshot()
                    ToolResult.Success(
                        buildJsonObject {
                            put("mimeType", screenshot.mimeType)
                            put("data", screenshot.base64Data)
                            put("width", screenshot.width)
                            put("height", screenshot.height)
                        },
                    )
                }
            },
        )
    }

    private fun registerInteractionTools(registry: ToolRegistry, gateway: AccessibilityGateway) {
        registry.register(
            definition(
                "device_click_node",
                "Click a node returned by the most recent device_get_ui_tree call.",
                2,
                stringProperty("nodeId", required = true),
            ),
            ToolHandler { input, _ ->
                val nodeId = input.string("nodeId")
                    ?: return@ToolHandler invalid("nodeId is required.")
                safeBoolean { gateway.clickNode(nodeId) }
            },
        )
        registry.register(
            definition(
                "device_tap",
                "Tap an absolute screen coordinate.",
                2,
                numberProperties(listOf("x", "y"), required = setOf("x", "y")),
            ),
            ToolHandler { input, _ ->
                val x = input.number("x") ?: return@ToolHandler invalid("x is required.")
                val y = input.number("y") ?: return@ToolHandler invalid("y is required.")
                safeBoolean { gateway.tap(x, y) }
            },
        )
        registry.register(
            definition(
                "device_swipe",
                "Swipe between absolute screen coordinates.",
                2,
                numberProperties(
                    listOf("x1", "y1", "x2", "y2", "durationMs"),
                    required = setOf("x1", "y1", "x2", "y2"),
                ),
            ),
            ToolHandler { input, _ ->
                val x1 = input.number("x1") ?: return@ToolHandler invalid("x1 is required.")
                val y1 = input.number("y1") ?: return@ToolHandler invalid("y1 is required.")
                val x2 = input.number("x2") ?: return@ToolHandler invalid("x2 is required.")
                val y2 = input.number("y2") ?: return@ToolHandler invalid("y2 is required.")
                val duration = input["durationMs"]?.jsonPrimitive?.longOrNull ?: 300L
                if (duration !in 1..10_000) return@ToolHandler invalid("durationMs must be 1-10000.")
                safeBoolean { gateway.swipe(x1, y1, x2, y2, duration) }
            },
        )
        registry.register(
            definition("device_press_back", "Press Android Back.", 2),
            ToolHandler { _, _ -> safeBoolean { gateway.pressBack() } },
        )
        registry.register(
            definition("device_press_home", "Press Android Home.", 2),
            ToolHandler { _, _ -> safeBoolean { gateway.pressHome() } },
        )
        registry.register(
            definition(
                "device_input_text",
                "Set text on an editable node or the focused input.",
                3,
                buildJsonObject {
                    put(
                        "properties",
                        buildJsonObject {
                            put("text", buildJsonObject { put("type", "string") })
                            put("nodeId", buildJsonObject { put("type", "string") })
                        },
                    )
                    put("required", buildJsonArray { add(JsonPrimitive("text")) })
                },
            ),
            ToolHandler { input, _ ->
                val text = input.string("text") ?: return@ToolHandler invalid("text is required.")
                safeBoolean { gateway.inputText(text, input.string("nodeId")) }
            },
        )
        registry.register(
            definition(
                "device_launch_app",
                "Launch an installed Android app by package name.",
                3,
                stringProperty("packageName", required = true),
            ),
            ToolHandler { input, _ ->
                val packageName = input.string("packageName")
                    ?: return@ToolHandler invalid("packageName is required.")
                safeBoolean { gateway.launchApp(packageName) }
            },
        )
    }

    private fun definition(
        name: String,
        description: String,
        risk: Int,
        schemaBody: JsonObject = buildJsonObject {},
    ): ToolDefinition = ToolDefinition(
        name = name,
        description = description,
        risk = RiskLevel(risk),
        inputSchema = buildJsonObject {
            put("type", "object")
            val declaredProperties = schemaBody["properties"] as? JsonObject ?: buildJsonObject {}
            put(
                "properties",
                buildJsonObject {
                    declaredProperties.forEach { (key, value) -> put(key, value) }
                },
            )
            schemaBody
                .filterKeys { it != "properties" }
                .forEach { (key, value) -> put(key, value) }
            put("additionalProperties", false)
        },
    )

    private fun stringProperty(name: String, required: Boolean): JsonObject = buildJsonObject {
        put("properties", buildJsonObject { put(name, buildJsonObject { put("type", "string") }) })
        if (required) put("required", buildJsonArray { add(JsonPrimitive(name)) })
    }

    private fun numberProperties(names: List<String>, required: Set<String>): JsonObject = buildJsonObject {
        put(
            "properties",
            buildJsonObject {
                names.forEach { name ->
                    put(name, buildJsonObject { put("type", if (name == "durationMs") "integer" else "number") })
                }
            },
        )
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.number(name: String): Float? =
        this[name]?.jsonPrimitive?.doubleOrNull?.toFloat()

    private fun invalid(message: String): ToolResult.Failure =
        ToolResult.Failure("INVALID_INPUT", message)

    private suspend fun safeBoolean(block: suspend () -> Boolean): ToolResult = safeCall {
        if (block()) {
            ToolResult.Success(buildJsonObject { put("executed", true) })
        } else {
            ToolResult.Failure("OPERATION_FAILED", "Android did not execute the requested operation.")
        }
    }

    private suspend fun safeCall(block: suspend () -> ToolResult): ToolResult = try {
        block()
    } catch (error: DeviceOperationException) {
        ToolResult.Failure(error.code, error.message)
    } catch (error: Exception) {
        ToolResult.Failure("INTERNAL", error.message ?: "Android device operation failed.")
    }

    private fun UiTreeSnapshot.toJson(): JsonObject = buildJsonObject {
        put("packageName", packageName)
        put("truncated", truncated)
        put(
            "nodes",
            buildJsonArray {
                nodes.forEach { node ->
                    add(
                        buildJsonObject {
                            put("id", node.id)
                            node.text?.let { put("text", it) }
                            node.contentDescription?.let { put("contentDescription", it) }
                            put("className", node.className)
                            put(
                                "bounds",
                                buildJsonObject {
                                    put("left", node.bounds.left)
                                    put("top", node.bounds.top)
                                    put("right", node.bounds.right)
                                    put("bottom", node.bounds.bottom)
                                },
                            )
                            put("enabled", node.enabled)
                            put("clickable", node.clickable)
                            put("editable", node.editable)
                        },
                    )
                }
            },
        )
    }
}
