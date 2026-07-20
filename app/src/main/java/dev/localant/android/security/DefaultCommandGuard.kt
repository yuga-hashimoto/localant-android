package dev.localant.android.security

class DefaultCommandGuard(
    workspaceRoot: String,
) : CommandGuard {

    private val normalizedRoot: String = normalizePath(workspaceRoot)

    private val redirectOperators = setOf(
        ">", ">>", "<",
    )

    private val blockedTokens = listOf(
        "reboot",
        "shutdown",
        "mkfs",
        "mkswap",
        "mknod",
        "mount",
        "umount",
        "su",
        "sudo",
        "setenforce",
        "restorecon",
        "chcon",
        "pm install",
        "pm uninstall",
        "am broadcast",
        "pm clear",
        "dumpsys",
        "svc wifi",
        "svc data",
        "input keyevent",
        "settings put secure",
        "settings put global",
        "content insert",
        "content delete",
        "content update",
        "cmd package",
        "cmd appops",
        "cmd wifi",
        "cmd bluetooth",
        "cmd notification",
        "cmd stats",
        "cmd shortcut",
        "ln ",
        "link ",
    )

    private val dangerousPrefixes = listOf(
        "dd if=",
        "dd of=",
        "rm -rf /",
        "rm -r /",
        "rm -rf --no-preserve-root",
        "fdisk",
        "parted",
        "fsck",
        "resize2fs",
        "tune2fs",
        "e2fsck",
        "mke2fs",
        "mkfs.",
        "fastboot",
        "heimdall",
        "odin",
        "adb ",
        "chmod 777",
        "chown root",
        ":(){ :|:& };:",
    )

    override fun validate(command: String): GuardResult {
        if (command.isBlank()) {
            return GuardResult(
                allowed = false,
                code = CommandGuard.SHELL_REJECTED,
                message = "Empty command is not allowed.",
            )
        }

        val syntaxError = restrictedSyntaxReason(command)
        if (syntaxError != null) {
            return GuardResult(
                allowed = false,
                code = CommandGuard.SHELL_REJECTED,
                message = syntaxError,
            )
        }

        val lower = command.lowercase().trim()

        for (token in blockedTokens) {
            if (lower.contains(token)) {
                return GuardResult(
                    allowed = false,
                    code = CommandGuard.SHELL_REJECTED,
                    message = "Blocked token '$token' detected in command.",
                )
            }
        }

        for (prefix in dangerousPrefixes) {
            if (lower.contains(prefix)) {
                return GuardResult(
                    allowed = false,
                    code = CommandGuard.SHELL_REJECTED,
                    message = "Dangerous operation detected: '$prefix'.",
                )
            }
        }

        val tokens = tokenize(command)

        var expectRedirectPath = false
        for (token in tokens) {
            val unquoted = unquote(token)

            if (expectRedirectPath) {
                if (!isPathWithinWorkspace(unquoted)) {
                    return GuardResult(
                        allowed = false,
                        code = CommandGuard.SHELL_REJECTED,
                        message = "Path '$unquoted' resolves outside workspace root.",
                    )
                }
                expectRedirectPath = false
                continue
            }

            val stripped = unquote(token)
            if (stripped in redirectOperators) {
                expectRedirectPath = true
            }

            if (isAbsolutePath(unquoted) || hasPathTraversal(unquoted)) {
                if (!isPathWithinWorkspace(unquoted)) {
                    return GuardResult(
                        allowed = false,
                        code = CommandGuard.SHELL_REJECTED,
                        message = "Path '$unquoted' resolves outside workspace root.",
                    )
                }
            }
        }

        if (expectRedirectPath) {
            return GuardResult(
                allowed = false,
                code = CommandGuard.SHELL_REJECTED,
                message = "Missing file path after redirect operator.",
            )
        }

        return GuardResult(allowed = true)
    }

    private fun restrictedSyntaxReason(command: String): String? {
        if (command.contains('$')) return "Shell parameter and command expansion are not allowed."
        if (command.contains('`')) return "Backtick command substitution is not allowed."
        if (command.contains('\\')) return "Backslash escapes are not allowed."
        if (command.contains('~')) return "Tilde path expansion is not allowed."
        if (!quotesAreBalanced(command)) return "Unmatched shell quote is not allowed."
        if (command.contains("<<")) return "Heredoc and here-string syntax is not allowed."
        if (Regex("(^|[\\s;&|])\\d+(>>?|<)").containsMatchIn(command)) {
            return "File-descriptor redirection is not allowed."
        }
        return null
    }

    private fun quotesAreBalanced(command: String): Boolean {
        var inSingle = false
        var inDouble = false
        for (c in command) {
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
            }
        }
        return !inSingle && !inDouble
    }

    private fun isPathWithinWorkspace(path: String): Boolean {
        val resolved = resolvePath(path)
        return resolved.startsWith(normalizedRoot + "/") || resolved == normalizedRoot
    }

    private fun resolvePath(path: String): String {
        val absolute = if (path.startsWith("/")) {
            path
        } else {
            "$normalizedRoot/$path"
        }
        return normalizePath(absolute)
    }

    private fun isAbsolutePath(token: String): Boolean = token.startsWith("/")

    private fun hasPathTraversal(token: String): Boolean = token.contains("..")

    companion object {
        fun normalizePath(path: String): String {
            val parts = mutableListOf<String>()
            for (part in path.split("/")) {
                if (part.isEmpty()) continue
                when (part) {
                    "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeLast()
                    else -> parts.add(part)
                }
            }
            return "/" + parts.joinToString("/")
        }

        fun tokenize(command: String): List<String> {
            val tokens = mutableListOf<String>()
            val buffer = StringBuilder()
            var inSingle = false
            var inDouble = false
            var index = 0

            fun flush() {
                if (buffer.isNotEmpty()) {
                    tokens.add(buffer.toString())
                    buffer.clear()
                }
            }

            while (index < command.length) {
                val c = command[index]
                when {
                    c == '\'' && !inDouble -> {
                        buffer.append(c)
                        inSingle = !inSingle
                    }
                    c == '"' && !inSingle -> {
                        buffer.append(c)
                        inDouble = !inDouble
                    }
                    !inSingle && !inDouble && c.isWhitespace() -> flush()
                    !inSingle && !inDouble && (c == '>' || c == '<') -> {
                        flush()
                        if (c == '>' && index + 1 < command.length && command[index + 1] == '>') {
                            tokens.add(">>")
                            index++
                        } else {
                            tokens.add(c.toString())
                        }
                    }
                    else -> buffer.append(c)
                }
                index++
            }
            flush()
            return tokens
        }

        fun unquote(token: String): String {
            if (token.length >= 2 && token[0] == '\'' && token.last() == '\'') {
                return token.substring(1, token.length - 1)
            }
            if (token.length >= 2 && token[0] == '"' && token.last() == '"') {
                return token.substring(1, token.length - 1)
            }
            return token
        }
    }
}
