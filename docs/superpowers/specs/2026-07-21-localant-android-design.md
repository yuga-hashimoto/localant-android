# LocalAnt Android Design

## Goal

Build an Android application that makes the phone itself a secure MCP host for ChatGPT. The app must run without a PC, VPS, or LocalAnt-owned relay. It publishes a public HTTPS MCP URL through an embedded Tailscale `tsnet` Funnel node, executes sandboxed shell commands under the app Linux UID, and exposes explicitly approved Android UI automation through `AccessibilityService`.

## User experience

1. The user installs and opens LocalAnt Android.
2. The onboarding flow requests notification permission, opens the Accessibility settings page, and guides the user through Tailscale authentication.
3. The user taps Start while the app is foregrounded.
4. A foreground service starts the embedded native bridge and MCP endpoint.
5. The app shows a public `https://<hostname>.<tailnet>.ts.net/mcp?key=<token>` URL and copy action.
6. The user adds the URL to ChatGPT as a custom MCP app.
7. ChatGPT calls tools; the phone enforces authentication, policy, local approval, execution, and audit logging.
8. The persistent notification can pause or stop all remote access immediately.

## Architecture

### Kotlin Android host

The Android application owns UI, lifecycle, Android permissions, approvals, audit storage, shell execution, and Accessibility operations. It exposes a synchronous `ToolHost.execute(toolName, inputJson): String` callback to the Go bridge. The callback dispatches to small tool handlers and returns an MCP content payload or a structured error. Potentially blocking work runs on bounded executors; commands have explicit timeouts and output limits.

### Go native bridge

A Go package is compiled into an Android AAR with `gomobile bind`. The bridge owns `tsnet.Server`, Tailscale authentication state, the Funnel listener, TLS, HTTP serving, MCP JSON-RPC parsing, rate limiting, request authentication, and MCP session identifiers. It calls the Kotlin `ToolHost` only after protocol and token validation.

The bridge exposes: 

- `Start(Config, Host) String` returning a JSON state snapshot
- `Stop()`
- `Status() String`
- callbacks through `Host.onState(json)` and `Host.execute(tool, inputJson)`

For local JVM tests and developer builds where the AAR is unavailable, Kotlin depends on a `NativeBridge` interface and uses a deterministic fake implementation. The release build uses the generated Go binding.

### MCP protocol

The public route is `/mcp`. The MVP supports:

- `initialize`
- `notifications/initialized`
- `ping`
- `tools/list`
- `tools/call`

Requests are JSON-RPC 2.0 over HTTP POST. Successful responses use `application/json`. Session IDs are minted at initialization and accepted through `Mcp-Session-Id`; stateless clients are also supported. GET returns 405 for the MVP because server-to-client notifications are not required.

Authentication accepts `Authorization: Bearer <token>` and the compatibility query parameter `?key=<token>`. Tokens are 256-bit random values generated on-device, stored using Android Keystore-backed encrypted preferences, compared in constant time in Go, and rotatable from the app.

### Tool families

Risk 0 read-only tools:

- `device_status`
- `device_capabilities`
- `device_current_app`
- `device_get_ui_tree`
- `audit_list`

Risk 1 sensitive-read tools:

- `device_screenshot`

Risk 2 direct interaction tools:

- `device_click_node`
- `device_tap`
- `device_swipe`
- `device_press_back`
- `device_press_home`

Risk 3 execution and input tools:

- `device_input_text`
- `device_launch_app`
- `shell_execute`

Risk 4 destructive operations are not part of the MVP.

### Approval model

The phone is the final authority. The default policy is:

- risk 0: allow
- risk 1: allow for the active session after one local approval
- risk 2: require a local approval, with optional session grant
- risk 3: require a local approval for each call
- risk 4: deny

Pending approvals are persisted in Room and surfaced in the app plus a high-priority notification. Each command has a 60-second approval TTL. Calls waiting for approval return an MCP error containing the approval ID; ChatGPT retries after the user approves. No command is executed speculatively.

### Shell security

`/system/bin/sh -c` runs under the application UID. The working directory must be the LocalAnt workspace or a user-selected Storage Access Framework tree mapped by the app. The engine rejects commands containing blocked destructive tokens after shell-aware tokenization. It enforces:

- maximum timeout: 60 seconds
- maximum combined output: 512 KiB
- maximum concurrent processes: 2
- no privilege escalation, device reboot, block-device write, filesystem format, mount, SELinux mode change, package installation, or factory-reset commands
- no access to another app sandbox

The initial tool is one-shot execution. Interactive PTY and Shizuku are later phases.

### Accessibility implementation

`LocalAntAccessibilityService` stores a process-local weak singleton while connected. It declares `canRetrieveWindowContent`, gesture capability, and screenshot capability. UI trees are normalized into bounded JSON containing stable per-snapshot node IDs, class, text, content description, bounds, enabled/clickable/editable flags, and package. Password nodes are omitted.

Screenshots use `AccessibilityService.takeScreenshot` on API 30+. PNG bytes are returned as base64 MCP image content with a 4 MiB cap. Secure windows may return an explicit unavailable error. Gesture and text actions verify the service is active and reject operations on packages in the configurable protected-package list. Banking, authenticator, password-manager, wallet, and LocalAnt itself are protected by default.

### Foreground lifecycle

The user starts hosting from the visible app. `LocalAntHostService` is a foreground service with a persistent notification showing connection state and Stop/Pause actions. It owns the bridge lifetime. Android 14+ foreground-service declarations and permissions are included. The service never silently starts from `BOOT_COMPLETED` in the MVP. Network reconnection is delegated to tsnet; the service publishes state changes to the UI through `StateFlow`.

### Tailscale authentication and Funnel

The bridge stores tsnet state under the app-private files directory. Without an auth key, tsnet emits an authentication URL through its logger; the bridge parses it and reports it to Kotlin, which opens the browser. After authentication, the bridge calls `ListenFunnel("tcp", ":443", tsnet.FunnelOnly())`. The app reports prerequisite errors clearly when MagicDNS, HTTPS certificates, or Funnel policy are not enabled.

The displayed public URL comes from the tsnet status certificate domain and always appends `/mcp?key=<token>`. Funnel traffic is end-to-end TLS terminated in the on-device tsnet process.

## Data storage

- encrypted preferences: access token, hostname, onboarding state
- app-private files: tsnet state and shell workspace
- Room: audit entries, pending approvals, session grants
- no screenshots, UI trees, shell output, or typed text are uploaded to any LocalAnt service

Audit records store tool name, risk, timestamp, caller session, approval result, duration, success/error, and redacted input summary. Raw typed text and shell output are not persisted by default.

## Error handling

All public errors use stable codes: `UNAUTHENTICATED`, `APPROVAL_REQUIRED`, `ACCESSIBILITY_DISABLED`, `PROTECTED_PACKAGE`, `SHELL_REJECTED , `TIMEOUT`, `OUTPUT_LIMIT`, `FUNNEL_NOT_READY`, and `INTERNAL`. The UI maps native bridge errors to actionable onboarding steps. Stopping the service closes listeners and cancels running shell processes.

## Testing

- JVM unit tests for MCP schemas, command guard, approval policy, UI-tree normalization, token handling, and router behavior
- Go tests for JSON-RPC, authentication, rate limits, session handling, and tool forwarding using a fake Host
- Android instrumentation tests for service binding, encrypted settings, Room stores, and Compose state rendering
- emulator smoke test: start local fake bridge, call `/mcp`, execute `device_status` and sandbox `shell_execute`
- optional native integration test gated by `LOCALANT_BUILD_TSNET=1` and Tailscale test credentials

## MVP exclusions

Shizuku, root, app installation/uninstallation, notification reading, camera, microphone, location, arbitrary shared-storage access, boot auto-start, interactive shells, multiple devices, and Play Store distribution are outside this implementation.
