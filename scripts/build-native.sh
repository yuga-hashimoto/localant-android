#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE_DIR="$ROOT_DIR/native"
OUTPUT_DIR="$ROOT_DIR/app/libs"
TOOL_DIR="$ROOT_DIR/.tools/bin"
GENERATED_DIR="$ROOT_DIR/.generated"
PATCHED_TAILSCALE_DIR="$GENERATED_DIR/tailscale"
NATIVE_BUILD_DIR="$GENERATED_DIR/native-build"
TARGETS="${LOCALANT_NATIVE_TARGETS:-android/arm64}"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
  export ANDROID_NDK_HOME
fi
if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@17 ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
fi

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "ANDROID_HOME does not exist: $ANDROID_HOME" >&2
  exit 1
fi
if [[ -z "${ANDROID_NDK_HOME:-}" || ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "ANDROID_NDK_HOME is not set to an installed Android NDK." >&2
  exit 1
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/javac" ]]; then
  echo "JAVA_HOME must point to JDK 17 or newer." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR" "$TOOL_DIR" "$GENERATED_DIR"
cd "$NATIVE_DIR"

MOBILE_VERSION="$(go list -m -f '{{.Version}}' golang.org/x/mobile)"
GOBIN="$TOOL_DIR" go install "golang.org/x/mobile/cmd/gomobile@$MOBILE_VERSION"
GOBIN="$TOOL_DIR" go install "golang.org/x/mobile/cmd/gobind@$MOBILE_VERSION"

"$ROOT_DIR/scripts/prepare-patched-tailscale.sh" "$PATCHED_TAILSCALE_DIR" >/dev/null
python3 - "$NATIVE_BUILD_DIR" <<'PY'
import shutil
import sys
shutil.rmtree(sys.argv[1], ignore_errors=True)
PY
mkdir -p "$NATIVE_BUILD_DIR"
cp -R "$NATIVE_DIR/bridge" "$NATIVE_BUILD_DIR/bridge"
cp "$NATIVE_DIR/go.mod" "$NATIVE_DIR/go.sum" "$NATIVE_BUILD_DIR/"
(
  cd "$NATIVE_BUILD_DIR"
  go mod edit -replace="tailscale.com=$PATCHED_TAILSCALE_DIR"
)

cd "$NATIVE_BUILD_DIR"
PATH="$TOOL_DIR:$PATH" "$TOOL_DIR/gomobile" bind \
  -target="$TARGETS" \
  -androidapi=30 \
  -javapkg=dev.localant.nativebridge \
  -trimpath \
  -o "$OUTPUT_DIR/localant-native.aar" \
  ./bridge

echo "Built $OUTPUT_DIR/localant-native.aar for $TARGETS"
