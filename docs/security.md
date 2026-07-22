# Security model

LocalAnt Android exposes a public HTTPS endpoint that can request actions on a personal Android device. The design therefore assumes the endpoint is continuously attacked and treats the phone as the final policy authority.

## Trust boundaries

1. **Tailscale Funnel** terminates public TLS and forwards traffic to the embedded `tsnet` listener.
2. **Go MCP server** authenticates requests, validates MCP sessions and protocol methods, applies request-size and rate limits, and forwards JSON to Kotlin.
3. **Kotlin secure executor** applies the registered-tool policy and audit logging before invoking a handler. Current builds execute every registered tool without local approval.
4. **Shell and Accessibility gateways** enforce device-local restrictions independently of the approval-free MCP policy.

No LocalAnt-operated relay receives MCP traffic.

## MCP authentication

- A 256-bit random token is generated on the phone.
- The token is encrypted with an AES-GCM key held by Android Keystore.
- Requests can authenticate with `Authorization: Bearer <token>` or the `?key=<token>` connector URL.
- Token comparison uses a constant-time path.
- The full URL is shown only inside the LocalAnt app and is not placed in the foreground notification.
- Rotating the token stops hosting and invalidates the previous connector URL.

The complete connector URL is a credential. Possession of its token permits every registered MCP tool to run without local approval. Do not publish it, include it in screenshots, paste it into issue reports, or store it in source control. Query tokens can appear in browser history or connector configuration; use bearer authentication where the client supports it.

## MCP transport controls

- Only `/healthz` and `/mcp` are exposed.
- `/mcp` requires authentication.
- Request bodies are limited to 1 MiB.
- Per-client fixed-window rate limiting is applied.
- Invalid non-HTTPS origins are rejected, except local development origins.
- MCP session IDs are random and required after initialization.
- Unsupported protocol versions and methods return stable errors.
- The HTTP server has bounded header, read, write, and idle timeouts.
- Screenshot bytes are returned as MCP `image` content rather than embedded in structured JSON.
- Image content is Base64-decoded, bounded to 4 MiB, MIME-checked, magic-byte-checked, and normalized before being returned.
- Only PNG, JPEG, WebP, and GIF image blocks are accepted; SVG and malformed image payloads are rejected.
- Screenshot `structuredContent` contains metadata such as MIME type and dimensions, not a duplicate binary payload.

## Risk levels

| Risk | Behavior | Examples |
|---|---|---|
| 0 | Immediate execution | status, capabilities, current app, redacted UI tree |
| 1 | Immediate execution | screenshot |
| 2 | Immediate execution | tap, swipe, click, Back, Home |
| 3 | Immediate execution | shell, text input, app launch |
| 4 | Not registered | destructive or privileged operations |

Risk values remain attached to definitions for audit and future policy changes, but they do not currently trigger an approval round trip. Unregistered tools cannot be invoked through MCP.

## Accessibility controls

- Password nodes are removed from normalized UI trees.
- Text cannot be sent to password fields.
- UI trees are bounded in depth and node count.
- Node IDs refer to paths in the most recent snapshot and fail safely when the UI changes.
- LocalAnt protects its own package so the MCP URL cannot be read through its UI tree or screenshot tool.
- Authenticators, wallets, password managers, and banking-like packages are blocked by default.
- Android secure windows can prevent screenshots and are not bypassed.

The protected-package list is a defense-in-depth heuristic, not a complete catalog of every financial or identity app. Add organization-specific packages before production use.

## Shell controls

The shell runs as the application UID, not root. Its current directory must remain inside `filesDir/workspace`.

Controls include:

- Maximum 60-second execution
- Maximum 512 KiB combined stdout/stderr
- Maximum two concurrent processes
- Immediate process cancellation when hosting stops
- Absolute-path and traversal checks
- Redirect destination checks
- Rejection of parameter expansion, command substitution, backticks, backslash escapes, tilde expansion, heredocs, file-descriptor redirects, link creation, privileged package/system commands, block-device operations, and other dangerous patterns

This is a policy-constrained shell, not a kernel sandbox. Android app-UID isolation remains the primary operating-system boundary.

## Persistence and audit

- Tailscale node state is stored in the app-private data directory.
- Redacted audit entries are stored in Room. Legacy approval records are not used by the current approval-free policy and stale pending records are cleared on startup.
- Sensitive key/value patterns, bearer tokens, API keys, passwords, and secrets are redacted before audit persistence.
- Audit input summaries are bounded representations, not full binary payloads.
- The MCP token itself is not logged.

## Known limitations

- A compromised phone or malicious app with equivalent privileges can undermine device-local controls.
- Accessibility is a powerful Android permission and should be disabled when LocalAnt is not needed.
- Funnel makes the endpoint reachable from the public internet; token secrecy is mandatory.
- The current rate limiter is process-local and resets when the app restarts.
- The protected-package heuristic requires maintenance.
- The default native artifact is arm64 only unless additional architectures are requested at build time.
- No Play Store hardening, production signing key, reproducible-build attestation, or external security audit has been completed.
