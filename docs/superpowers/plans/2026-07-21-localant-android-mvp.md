# LocalAnt Android MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-hosted Android MCP endpoint that publishes through embedded Tailscale Funnel, exposes locally approved device automation, and executes sandboxed shell commands without a PC or LocalAnt-owned server.

**Architecture:** Kotlin owns Android lifecycle, UI, approvals, audit, shell, and Accessibility. A Go package compiled with gomobile owns tsnet, Funnel, HTTPS, MCP JSON-RPC, authentication, and forwarding to a Kotlin Host callback. A fake NativeBridge keeps normal debug/test builds independent of the native AAR.

**Tech Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, Room, Android Keystore-backed preferences, JUnit, AndroidX Test, Go, gomobile, tailscale.com/tsnet, Go net/http.

## Global Constraints

- `minSdk = 30`, `compileSdk = 36`, `targetSdk = 36`, Java/Kotlin 17.
- No PC, VPS, or LocalAnt-owned relay at runtime.
- MVP excludes Shizuku, root, boot auto-start, interactive PTY, app install/uninstall, notification access, camera, microphone, location, and Play Store work.
- Risk 4 operations are not registered.
- The ordinary `assembleDebug` and JVM test path must work when the generated tsnet AAR is absent.
- Native Funnel verification is a separate opt-in build requiring Go, gomobile, Android SDK/NDK, and a Tailscale test account.

---

### Task 1: Buildable Android scaffold and CI

**Files:** Create Gradle wrapper/configuration, `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, manifest/resources, `MainActivity.kt`, and `.github/workflows/android.yml`.

**Interfaces:** Produces a single `:app` module, application ID `dev.localant.android`, Compose entry point, and `BuildConfig.NATIVE_TSNET_ENABLED`.

- [ ] Copy a known-good Gradle wrapper, set Android Gradle Plugin/Kotlin/Compose versions compatible with API 36 and JDK 17.
- [ ] Add a JVM smoke test that references the app package and fails before the scaffold exists.
- [ ] Implement the minimal Compose app and manifest.
- [ ] Run `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew testDebugUnitTest assembleDebug`.
- [ ] Commit `build: scaffold LocalAnt Android app`.

### Task 2: Core tool contracts, router, and fake native bridge

**Files:** Create `core/model/*`, `core/tools/ToolDefinition.kt`, `ToolRegistry.kt`, `ToolHost.kt`, `bridge/NativeBridge.kt`, `FakeNativeBridge.kt`, and unit tests.

**Interfaces:** `ToolHandler.execute(input: JsonObject, context: ToolContext): ToolResult`; `ToolRegistry.listDefinitions()`; `ToolRegistry.execute(name,input,context)`; `NativeBridge.start(config,host)`, `stop()`, `status()`.

- [ ] Write failing tests for deterministic tool listing, unknown-tool errors, handler forwarding, and fake bridge state transitions.
- [ ] Implement immutable tool definitions with JSON Schemas and risk levels.
- [ ] Implement router validation and stable error codes.
- [ ] Implement fake bridge that exposes a localhost-style development URL and calls the same ToolHost.
- [ ] Run focused tests and commit `feat: add MCP tool routing contracts`.

### Task 3: Token, policy, approvals, and audit

**Files:** Create `security/TokenStore.kt`, `security/CommandGuard.kt`, `approval/*`, `audit/*`, Room database/entities/DAOs, and tests.

**Interfaces:** `TokenStore.current()/rotate()`; `ApprovalPolicy.requirement(risk,session)`; `ApprovalRepository.request/approve/deny/consume`; `AuditRepository.record/list`; `CommandGuard.validate(command): GuardResult`.

- [ ] Write failing tests for 256-bit tokens, policy matrix, approval TTL/session grants, audit redaction, and blocked shell operations.
- [ ] Implement Android Keystore-backed token storage with an injectable JVM test store.
- [ ] Implement Room persistence and repository boundaries.
- [ ] Implement shell-aware blocked-token checks, timeout/output/concurrency constants, and safe input summaries.
- [ ] Run tests and commit `feat: add local security approvals and audit`.

### Task 4: Sandboxed shell tool

**Files:** Create `shell/SandboxShellEngine.kt`, `shell/ShellExecuteTool.kt`, tests, and register the tool.

**Interfaces:** `ShellEngine.execute(command,cwd,timeoutMs): ShellResult`; workspace root is `filesDir/workspace`; maximum timeout 60 seconds; output cap 512 KiB; maximum two processes.

- [ ] Write failing tests for working-directory confinement, rejected commands, timeout, stdout/stderr capture, output cap, and success exit code.
- [ ] Implement `/system/bin/sh -c` execution under the app UID with bounded executors and process cleanup.
- [ ] Wrap execution with risk-3 approval and redacted audit.
- [ ] Run tests and commit `feat: add sandbox shell execution`.

### Task 5: Accessibility tools

**Files:** Create `accessibility/LocalAntAccessibilityService.kt`, `AccessibilityGateway.kt`, `UiTreeNormalizer.kt`, tool handlers, `res/xml/accessibility_service_config.xml`, manifest declarations, and tests.

**Interfaces:** service singleton state; `snapshotTree()`, `takeScreenshot()`, `clickNode(id)`, `tap`, `swipe`, `inputText`, `globalAction`, and `currentPackage()`.

- [ ] Write JVM tests for bounded tree normalization, password-node omission, stable node IDs, and protected-package enforcement.
- [ ] Implement service metadata with window retrieval, gestures, and screenshot capability.
- [ ] Implement API 30 screenshot callback with PNG/base64 output and 4 MiB limit.
- [ ] Implement click/gesture/text/global actions and risk mapping.
- [ ] Register device tools, run tests, and commit `feat: add approved Android accessibility tools`.

### Task 6: Foreground host service and Compose UX

**Files:** Create `service/LocalAntHostService.kt`, notification actions/receiver, `HostStateStore.kt`, onboarding/dashboard/approvals Compose screens, view model, and UI tests.

**Interfaces:** `HostState` includes stopped/starting/auth-required/running/error, auth URL, public MCP URL, active session count, and pending approval count.

- [ ] Write failing tests for host-state reducers, start/stop behavior, URL copy visibility, onboarding readiness, and pending approval actions.
- [ ] Implement notification channel and Android 14+ foreground service declarations.
- [ ] Start only from a visible user action; Stop closes bridge and shell processes.
- [ ] Add buttons for Accessibility settings, Tailscale auth URL, Start, Stop, token rotation, URL copy, approval allow-once/session/deny, and audit list.
- [ ] Run unit/instrumentation tests and commit `feat: add host lifecycle and setup UI`.

### Task 7: Go MCP and tsnet Funnel bridge

**Files:** Create `native/go.mod`, `native/bridge/*.go`, Go tests, `scripts/build-native.sh`, Gradle native task/configuration, and Kotlin generated-binding adapter.

**Interfaces:** gomobile-exported `Config`, `Host`, `Start`, `Stop`, `Status`; HTTP `/healthz` and `/mcp`; bearer and query-token auth; JSON-RPC initialize/ping/tools/list/tools/call; `Mcp-Session-Id`; `tsnet.Server.ListenFunnel("tcp", ":443", tsnet.FunnelOnly())`.

- [ ] Write Go tests first using a fake Host for auth rejection, constant-time match path, initialize response, tools list, call forwarding, session IDs, method rejection, rate limiting, and malformed JSON.
- [ ] Implement the protocol without starting tsnet in unit tests.
- [ ] Implement tsnet lifecycle, auth URL extraction, app-private state directory, Funnel listener, certificate-domain URL discovery, and actionable prerequisite errors.
- [ ] Add `buildNativeBridge` using pinned `golang.org/x/mobile/cmd/gomobile`; keep it opt-in via `LOCALANT_BUILD_TSNET=1`.
- [ ] Run `go test ./...`; run gomobile AAR build; run Android build with generated AAR; commit `feat: embed MCP server with Tailscale Funnel`.

### Task 8: End-to-end verification, docs, and release artifact

**Files:** Add integration tests, `docs/setup.md`, `docs/security.md`, `docs/native-build.md`, sample MCP requests, CI updates, and release workflow if signing is configured.

**Interfaces:** documented install/start/auth/copy/register/approve/stop flow and troubleshooting for MagicDNS, HTTPS certificates, Funnel policy, Accessibility, OEM battery restrictions, secure windows, and token rotation.

- [ ] Add a fake-bridge emulator test that starts hosting and calls initialize, tools/list, device_status, and a harmless workspace shell command.
- [ ] Add Go/Android test jobs and an opt-in native build job.
- [ ] Run full verification: `./gradlew lintDebug testDebugUnitTest assembleDebug`, `go test ./...`, and native AAR build when credentials/tooling permit.
- [ ] Review diff for secrets, placeholders, protected-package defaults, and unsafe command paths.
- [ ] Commit `docs: complete LocalAnt Android MVP verification`.
