#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE_DIR="$ROOT_DIR/native"
TEST_DIR="$(mktemp -d)"
trap 'python3 - "$TEST_DIR" <<'"'"'PY'"'"'
import shutil
import sys
shutil.rmtree(sys.argv[1], ignore_errors=True)
PY' EXIT

OUTPUT_DIR="$TEST_DIR/tailscale"
"$ROOT_DIR/scripts/prepare-patched-tailscale.sh" "$OUTPUT_DIR" >/dev/null

ANDROID_CERT="$OUTPUT_DIR/ipn/localapi/cert_android.go"
DISABLED_STUB="$OUTPUT_DIR/ipn/localapi/disabled_stubs.go"

[[ -f "$ANDROID_CERT" ]]
grep -q '//go:build android && !ts_omit_acme' "$ANDROID_CERT"
grep -q 'Register("cert/", (\*Handler).serveCert)' "$ANDROID_CERT"
grep -q 'GetCertPEMWithValidity' "$ANDROID_CERT"

if grep -q '//go:build ios || android || js' "$DISABLED_STUB"; then
  echo "Android is still routed to the 404 certificate stub." >&2
  exit 1
fi
grep -q '//go:build ios || js' "$DISABLED_STUB"

SOURCE_DIR="$(cd "$NATIVE_DIR" && go mod download -json tailscale.com | python3 -c 'import json,sys; print(json.load(sys.stdin)["Dir"])')"
grep -q '//go:build ios || android || js' "$SOURCE_DIR/ipn/localapi/disabled_stubs.go"

printf 'Patched Tailscale Android certificate source verified.\n'
