# Native Tailscale bridge build

The native bridge is a Go package compiled into an Android AAR with gomobile. The AAR contains the embedded Tailscale `tsnet` node, HTTPS Funnel listener, MCP HTTP server, and generated Java bindings.

## Pinned toolchain

- Go toolchain: `go1.26.5`
- Tailscale: `v1.100.0`
- `golang.org/x/mobile`: `v0.0.0-20260709172247-6129f5bee9d5`
- Android API floor for gomobile: 30
- Tested Android NDK: `28.2.13676358`
- Android application JDK: 17

The exact Go dependencies and mobile tools are recorded in `native/go.mod` and `native/go.sum`.

## Build only the AAR

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/28.2.13676358" \
LOCALANT_NATIVE_TARGETS=android/arm64 \
./scripts/build-native.sh
```

Output:

```text
app/libs/localant-native.aar
```

The AAR and downloaded tool binaries are ignored by Git. They are generated artifacts, not source dependencies.

## Build the Android APK with tsnet

```bash
LOCALANT_BUILD_TSNET=1 \
LOCALANT_NATIVE_TARGETS=android/arm64 \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/28.2.13676358" \
./gradlew clean lintDebug testDebugUnitTest compileDebugAndroidTestSources assembleDebug
```

When `LOCALANT_BUILD_TSNET` is absent, Gradle excludes `app/src/native/java`, does not depend on the AAR, and builds with `FakeNativeBridge`. This keeps ordinary JVM tests and Android development independent of Go and NDK installation.

## Architectures

The default script target is `android/arm64`. Override it with a comma-separated gomobile target list:

```bash
LOCALANT_NATIVE_TARGETS='android/arm64,android/amd64' ./scripts/build-native.sh
```

Adding architectures increases AAR and APK size substantially because `libgojni.so` is generated for each ABI.

## Go tests

```bash
cd native
go test -race ./...
go vet ./...
```

The HTTP/MCP tests do not start tsnet or contact Tailscale. They use an in-memory fake Kotlin Host boundary.

## Binding boundary

The generated Java package is:

```text
dev.localant.nativebridge.nativebridge
```

The exported API is intentionally small:

- `Nativebridge.newBridge(Host)`
- `Bridge.start(stateDir, hostname, accessToken)`
- `Bridge.stop()`
- `Bridge.status()`
- `Bridge.authURL()`
- `Bridge.publicURL()`
- `Bridge.lastError()`
- `Host.listToolsJSON()`
- `Host.executeTool(tool, inputJSON, sessionID)`

JSON is used across the Go/Kotlin boundary to avoid binding Android domain models into the AAR API.

## Funnel lifecycle

1. Kotlin creates a `Bridge` and calls `start`.
2. Go starts a persistent `tsnet.Server` using the app-private state directory.
3. If enrollment is required, Go extracts the Tailscale authorization URL from the user log and exposes `AUTH_REQUIRED`.
4. After authorization, `ListenFunnel("tcp", ":443", tsnet.FunnelOnly())` starts the HTTPS listener.
5. The first Tailscale certificate domain becomes the public MCP URL.
6. Kotlin polls bridge status and updates the foreground service UI.
7. Stop closes HTTP, Funnel, listeners, tsnet, and shell processes.

## Common build failures

### `gobind was not found`

Use `scripts/build-native.sh`. It installs the pinned `gomobile` and `gobind` binaries into `.tools/bin` and places that directory on `PATH` for the build.

### Java runtime unavailable

Set `JAVA_HOME` to a JDK containing `javac`. JDK 17 is used by the Android project.

### NDK not found

Install the pinned NDK or set `ANDROID_NDK_HOME` to another compatible installed NDK.

### Go toolchain version error

The Tailscale version requires Go 1.26.4 or newer. The module's `toolchain go1.26.5` directive allows the Go command to download and use the required toolchain.
