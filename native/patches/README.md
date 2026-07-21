# Tailscale Android certificate patch

LocalAnt Android embeds `tsnet.Server.ListenFunnel` to expose its MCP server.
Tailscale v1.100.0 excludes `ipn/localapi/cert.go` from Android builds and routes
certificate requests to a 404 stub. `ListenFunnel` obtains its TLS certificate
through that in-process LocalAPI, so the public endpoint cannot complete a TLS
handshake without an Android-specific handler.

`tailscale-android-cert.patch` restores the same certificate request flow used
by Tailscale on desktop platforms. It calls the existing
`LocalBackend.GetCertPEMWithValidity` implementation, which is included in
Android builds, and does not add a second ACME client.

The original module cache is never modified. `scripts/prepare-patched-tailscale.sh`
copies the exact version pinned by `native/go.mod` into `.generated/`, applies
the patch there, and `scripts/build-native.sh` builds against that temporary
copy with a Go module `replace` directive.

When updating Tailscale:

1. Run `scripts/test-prepare-patched-tailscale.sh`.
2. Build the arm64 AAR and APK from a clean tree.
3. Confirm public TLS handshakes and MCP initialization on a real tailnet.
4. Review the upstream Android certificate implementation to determine whether
   this patch can be removed.
