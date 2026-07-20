package dev.localant.android.audit

import dev.localant.android.core.model.RiskLevel
import java.util.UUID

class InMemoryAuditRepository : AuditRepository {

    private val entries = mutableListOf<AuditEntry>()

    override fun record(entry: AuditEntry) {
        val redactedEntry = entry.copy(
            inputSummary = entry.inputSummary.mapValues { (_, v) -> redactPlainText(v) }
        )
        entries.add(redactedEntry)
    }

    override fun list(): List<AuditEntry> =
        entries.toList()

    override fun listByIdSubstring(idSubstring: String): AuditEntry? =
        entries.firstOrNull { it.id.contains(idSubstring) }

    override fun recordForResult(
        toolName: String,
        risk: RiskLevel,
        callerSessionId: String,
        durationMs: Long,
        success: Boolean,
        inputSummary: String,
        errorCode: String?,
        approvalResult: String,
    ): AuditEntry {
        val entry = AuditEntry(
            id = UUID.randomUUID().toString(),
            toolName = toolName,
            risk = risk,
            timestampMs = System.currentTimeMillis(),
            callerSessionId = callerSessionId,
            approvalResult = approvalResult,
            durationMs = durationMs,
            success = success,
            errorCode = errorCode,
            inputSummary = mapOf("input" to redactPlainText(inputSummary)),
        )
        entries.add(entry)
        return entry
    }

    override fun redactJson(input: String): String {
        val sensitiveKeys = setOf(
            "password", "passwd", "pass", "token", "secret", "key",
            "api_key", "apikey", "auth", "authorization", "credentials",
            "credential", "private_key", "access_key", "access_token",
            "refresh_token", "bearer", "jwt", "session_key",
        )
        return redactJsonRecursive(input, sensitiveKeys)
    }

    private fun redactJsonRecursive(input: String, sensitiveKeys: Set<String>): String {
        val sb = StringBuilder()
        var i = 0
        var insideString = false
        var lastKey: String? = null
        val nextSignificant = { pos: Int ->
            var j = pos
            while (j < input.length && input[j].isWhitespace()) j++
            j
        }

        while (i < input.length) {
            val c = input[i]
            if (c == '"' && !isEscaped(input, i)) {
                if (insideString) {
                    insideString = false
                    sb.append(c)
                    i++
                    val after = nextSignificant(i)
                    if (after < input.length && input[after] == ':') {
                        lastKey = extractLastKey(sb)
                    }
                } else {
                    if (i + 1 < input.length && lastKey != null && sensitiveKeys.contains(lastKey.lowercase())) {
                        val endQuote = findClosingQuote(input, i + 1)
                        if (endQuote >= 0) {
                            sb.append("\"***\"")
                            i = endQuote + 1
                            lastKey = null
                            continue
                        }
                    }
                    insideString = true
                    sb.append(c)
                    i++
                    continue
                }
            } else if (c == '{' && !insideString) {
                val nested = findMatchingBrace(input, i)
                if (nested > i) {
                    val nestedContent = input.substring(i + 1, nested)
                    val redactedContent = redactJsonRecursive(nestedContent, sensitiveKeys)
                    sb.append('{').append(redactedContent).append('}')
                    i = nested + 1
                    continue
                }
            } else if (c == '[' && !insideString) {
                val nested = findMatchingBracket(input, i)
                if (nested > i) {
                    val nestedContent = input.substring(i + 1, nested)
                    val redactedContent = redactJsonRecursive(nestedContent, sensitiveKeys)
                    sb.append('[').append(redactedContent).append(']')
                    i = nested + 1
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun findClosingQuote(input: String, start: Int): Int {
        var i = start
        while (i < input.length) {
            if (input[i] == '"' && !isEscaped(input, i)) return i
            i++
        }
        return -1
    }

    private fun findMatchingBrace(input: String, start: Int): Int {
        var depth = 0
        var i = start
        var insideStr = false
        while (i < input.length) {
            val c = input[i]
            if (c == '"' && !isEscaped(input, i)) insideStr = !insideStr
            if (!insideStr) {
                when (c) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun findMatchingBracket(input: String, start: Int): Int {
        var depth = 0
        var i = start
        var insideStr = false
        while (i < input.length) {
            val c = input[i]
            if (c == '"' && !isEscaped(input, i)) insideStr = !insideStr
            if (!insideStr) {
                when (c) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun isEscaped(input: String, index: Int): Boolean {
        if (index == 0) return false
        var backslashes = 0
        var j = index - 1
        while (j >= 0 && input[j] == '\\') {
            backslashes++
            j--
        }
        return backslashes % 2 == 1
    }

    private fun extractLastKey(sb: StringBuilder): String? {
        val text = sb.toString()
        val lastQuote = text.lastIndexOf('"')
        if (lastQuote < 0) return null
        val prevQuote = text.lastIndexOf('"', lastQuote - 1)
        if (prevQuote < 0) return null
        return text.substring(prevQuote + 1, lastQuote)
    }

    override fun redactPlainText(input: String): String {
        val sensitivePatterns = listOf(
            """(?i)(\w*(?:token|TOKEN))\s*[:=]\s*(\S+)""".toRegex() to "$1: ***",
            """(?i)(\w*(?:password|passwd|pass))\s*[:=]\s*(\S+)""".toRegex() to "$1: ***",
            """(?i)(\w*(?:secret|SECRET))\s*[:=]\s*(\S+)""".toRegex() to "$1: ***",
            """(?i)(\w*(?:api[_-]?key|apikey))\s*[:=]\s*(\S+)""".toRegex() to "$1: ***",
            """(?i)(\w*(?:auth(?:orization)?))\s*[:=]\s*([^']+)""".toRegex() to "$1: ***",
            """(?i)(bearer)\s+(\S+)""".toRegex() to "$1 ***",
        )

        var result = input
        for ((pattern, replacement) in sensitivePatterns) {
            result = pattern.replace(result, replacement)
        }
        return result
    }
}
