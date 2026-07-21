package dev.localant.android.ui

private val MCP_KEY_QUERY_PATTERN = Regex("([?&]key=)[^&\\s]+")

internal fun redactMcpUrl(url: String): String = MCP_KEY_QUERY_PATTERN.replace(url) { match ->
    match.groupValues[1] + "••••••••"
}
