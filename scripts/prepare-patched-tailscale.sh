#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE_DIR="$ROOT_DIR/native"
PATCH_FILE="$ROOT_DIR/native/patches/tailscale-android-cert.patch"
OUTPUT_DIR="${1:-$ROOT_DIR/.generated/tailscale}"

if [[ ! -f "$PATCH_FILE" ]]; then
  echo "Missing Tailscale Android certificate patch: $PATCH_FILE" >&2
  exit 1
fi

MODULE_JSON="$(cd "$NATIVE_DIR" && go mod download -json tailscale.com)"
SOURCE_DIR="$(printf '%s' "$MODULE_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Dir"])')"
VERSION="$(printf '%s' "$MODULE_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Version"])')"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "Tailscale module source was not downloaded: $SOURCE_DIR" >&2
  exit 1
fi

python3 - "$OUTPUT_DIR" <<'PY'
import shutil
import sys
shutil.rmtree(sys.argv[1], ignore_errors=True)
PY
mkdir -p "$OUTPUT_DIR"
cp -R "$SOURCE_DIR/." "$OUTPUT_DIR/"
chmod -R u+w "$OUTPUT_DIR"

(
  cd "$OUTPUT_DIR"
  patch --batch --forward -p1 < "$PATCH_FILE"
)

printf '%s\n' "$VERSION" > "$OUTPUT_DIR/.localant-patched-version"
printf '%s\n' "$OUTPUT_DIR"
