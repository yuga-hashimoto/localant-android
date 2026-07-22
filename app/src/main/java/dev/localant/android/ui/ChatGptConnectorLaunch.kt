package dev.localant.android.ui

internal const val CHATGPT_CONNECTOR_CREATE_URL =
    "https://chatgpt.com/plugins#settings/Connectors?create-connector=true"

internal data class ChatGptConnectorLaunch(
    val clipboardText: String,
    val browserUrl: String,
)

internal fun prepareChatGptConnectorLaunch(mcpUrl: String): ChatGptConnectorLaunch =
    ChatGptConnectorLaunch(
        clipboardText = mcpUrl,
        browserUrl = CHATGPT_CONNECTOR_CREATE_URL,
    )
