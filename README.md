# LocalAnt Android

LocalAnt Android turns an Android phone into a self-hosted MCP endpoint for ChatGPT. The phone runs the MCP host, executes sandboxed shell commands, exposes approved Android UI automation tools, and publishes the endpoint through an embedded Tailscale Funnel connection.

## Target experience

1. Install and open the app.
2. Complete the guided Android and Tailscale permissions.
3. Start LocalAnt.
4. Copy the generated `https://<device>.<tailnet>.ts.net/mcp?key=...` URL.
5. Add the URL as a custom MCP app in ChatGPT.
6. Approve device actions locally when required.

No PC, VPS, or LocalAnt-owned relay service is required during normal use. Tailscale Funnel supplies the public encrypted transport.

## MVP capabilities

- MCP `initialize`, `tools/list`, and `tools/call` over Streamable HTTP-compatible JSON-RPC
- Android status and capability inspection
- Accessibility tree, screenshot, tap, swipe, text input, Back, and Home tools
- Sandboxed `/system/bin/sh` execution inside the app UID
- Device-side bearer/query-token authentication, risk classification, approval, and audit log
- Foreground service with an immediate stop action
- Embedded `tsnet.Server.ListenFunnel` bridge built with `gomobile`

## Project status

Initial implementation in progress. See:

- `docs/superpowers/specs/2026-07-21-localant-android-design.md`
- `docs/superpowers/plans/2026-07-21-localant-android-mvp.md`
