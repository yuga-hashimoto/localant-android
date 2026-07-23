# LocalAnt Android

> **Test PR** — This is a test pull request for verification purposes.

LocalAnt Android turns one Android phone into a self-hosted MCP endpoint for ChatGPT. The phone runs the MCP server, Tailscale Funnel, a sandboxed app-UID shell workspace, audit storage, and Accessibility-based Android controls. No PC, VPS, or LocalAnt-operated relay is required at runtime.

## Capabilities

- Embedded Tailscale `tsnet` node with HTTPS Funnel on port 443
- Streamable HTTP MCP endpoint with token authentication and MCP sessions
- Android status, verified foreground app, bounded UI tree, screenshot, tap, swipe, Back/Home, text input, and app launch tools
- Screenshots returned as first-class MCP `image` content with separate structured width/height metadata
- App-UID shell confined to `filesDir/workspace`, with timeout, output, concurrency, and command-policy limits
- Approval-free execution for all registered tools, with risk metadata and audit logging retained
- Android Keystore-backed MCP token
- Room-persisted redacted audit history
- Protected-app and password-field blocking
- Foreground-window verification that rejects stale UI trees and node IDs from hidden apps
- App-launch verification that reports success only after the target package reaches the foreground

## Build

Ordinary development build, without the native Tailscale AAR:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Native arm64 build with the embedded Tailscale Funnel bridge:

```bash
LOCALANT_BUILD_TSNET=1 \
LOCALANT_NATIVE_TARGETS=android/arm64 \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/28.2.13676358" \
./gradlew clean lintDebug testDebugUnitTest assembleDebug
```

The generated AAR is intentionally not committed. `scripts/build-native.sh` rebuilds it from the pinned Go module and gomobile tool versions.

## Use

1. Install the native APK on an arm64 Android 11+ device.
2. Enable **LocalAnt Android** in Android Accessibility settings.
3. Grant **Display over other apps** to permit verified remote app launching. LocalAnt does not draw overlay UI.
4. Exclude the app from aggressive battery restrictions.
5. Tap **Start LocalAnt**.
6. Open the displayed Tailscale sign-in URL and authorize the embedded node.
7. Copy the MCP URL after Funnel becomes ready.
8. Add the URL as a custom connector in ChatGPT developer mode.
9. Keep the connector URL secret; possession of its token permits all registered tools to run without local approval.

See [Setup](docs/setup.md), [Security](docs/security.md), [Native build](docs/native-build.md), and [MCP examples](docs/mcp-examples.md).

## Scope

The MVP intentionally excludes root, Shizuku, app installation/removal, notification access, microphone, camera, location, interactive PTY, boot auto-start, and risk-4 operations.

## License

A license has not yet been selected. Do not redistribute this repository as an independently licensed package until one is added.
