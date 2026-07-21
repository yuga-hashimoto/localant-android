# Signed release builds

LocalAnt Android release APKs are signed with a persistent project release key. The private key and passwords must never be committed to Git.

## Required environment variables

- `LOCALANT_RELEASE_STORE_FILE`: absolute path to the PKCS12/JKS keystore
- `LOCALANT_RELEASE_STORE_PASSWORD`: keystore password
- `LOCALANT_RELEASE_KEY_ALIAS`: signing key alias
- `LOCALANT_RELEASE_KEY_PASSWORD`: signing key password
- `LOCALANT_BUILD_TSNET=1`: include the embedded Tailscale bridge
- `LOCALANT_NATIVE_TARGETS=android/arm64`: build the phone release ABI
- `LOCALANT_VERSION_NAME`: semantic version without the `v` prefix
- `LOCALANT_VERSION_CODE`: monotonically increasing Android version code

## Local build

```bash
export LOCALANT_BUILD_TSNET=1
export LOCALANT_NATIVE_TARGETS=android/arm64
export LOCALANT_VERSION_NAME=0.1.0
export LOCALANT_VERSION_CODE=1
export LOCALANT_RELEASE_STORE_FILE=/absolute/path/localant-android-release.p12
export LOCALANT_RELEASE_STORE_PASSWORD='...'
export LOCALANT_RELEASE_KEY_ALIAS=localant-release
export LOCALANT_RELEASE_KEY_PASSWORD='...'
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"

./gradlew clean lintRelease testReleaseUnitTest assembleRelease --no-daemon
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## GitHub release

The `Release` workflow runs for `v*` tags and can also be started manually. It restores the signing key from GitHub Actions secrets, creates a signed tsnet-enabled arm64 APK, verifies the signature and publishes the APK plus its SHA-256 checksum to GitHub Releases.

Required repository secrets:

- `LOCALANT_RELEASE_KEYSTORE_BASE64`
- `LOCALANT_RELEASE_STORE_PASSWORD`
- `LOCALANT_RELEASE_KEY_ALIAS`
- `LOCALANT_RELEASE_KEY_PASSWORD`

The same release key must be retained for every update. Losing it prevents installing future builds as an update over existing installations.
