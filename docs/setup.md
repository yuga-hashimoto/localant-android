# Setup

## Requirements

- Android 11 or newer (`minSdk 30`)
- An arm64 Android device for the default native build
- A Tailscale account whose tailnet has:
  - MagicDNS enabled
  - HTTPS certificates enabled
  - Funnel permitted for the LocalAnt node through the tailnet policy
- ChatGPT developer mode with permission to create a custom connector

LocalAnt embeds its own `tsnet` node. Installing the regular Tailscale Android app is not required for LocalAnt networking, although it can still be useful for other device traffic.

## Install

Build the native APK as described in [Native build](native-build.md), then install it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First run

1. Open **LocalAnt Android**.
2. Allow notifications. The foreground notification provides immediate Pause and Stop actions.
3. Tap **Open Accessibility settings**.
4. Select **LocalAnt Android** and enable the service.
5. Return to LocalAnt. The first card should show **Accessibility enabled**.
6. Tap **Open overlay permission** and enable **Display over other apps** for LocalAnt. This permission allows Android to accept app-launch requests from the background MCP service; LocalAnt does not draw overlay UI.
7. Tap **Open battery settings** and exclude LocalAnt from manufacturer battery restrictions when available.
8. Tap **Start LocalAnt**.
9. When the status changes to `AUTH_REQUIRED`, tap **Open Tailscale sign-in**.
10. Sign in and authorize the new LocalAnt node.
11. Return to LocalAnt. The app automatically continues from authentication to Funnel startup.
12. When the status is `RUNNING`, tap **Copy MCP URL**.

The copied URL contains the MCP token. Treat the complete URL as a password.

## Tailscale configuration

Funnel listens on HTTPS port 443. Tailscale requires MagicDNS, HTTPS certificates, and a policy that grants the `funnel` node attribute to the LocalAnt node or an appropriate tag. Configure the narrowest target suitable for your tailnet instead of granting Funnel to every member.

Example policy fragment for a dedicated tag:

```json
{
  "tagOwners": {
    "tag:localant": ["autogroup:admin"]
  },
  "nodeAttrs": [
    {
      "target": ["tag:localant"],
      "attr": ["funnel"]
    }
  ]
}
```

The current app enrolls interactively rather than advertising a tag automatically. Apply the required attribute to the enrolled node using the policy model appropriate for your tailnet.

## Add to ChatGPT

In ChatGPT Web:

1. Open **Settings**.
2. Open **Apps & Connectors**.
3. Enable **Developer mode** under advanced settings.
4. Create a connector using the complete URL copied from LocalAnt.
5. Save and enable the connector.

The connector should discover the tools through `initialize` and `tools/list`.

## Tool execution

All registered tools run immediately after MCP token authentication. Risk levels remain attached to tool definitions for audit and future policy changes, but the current build does not use an approval round trip. Keep the complete connector URL secret.

`device_launch_app` additionally requires an unlocked device and the **Display over other apps** permission. It returns success only after the requested package is observed in the foreground.

## Stop and rotate

- **Stop LocalAnt** closes the public listener, embedded Tailscale node, HTTP server, and running shell processes.
- **Rotate MCP token** creates a new Keystore-encrypted token and stops hosting. Start LocalAnt again and replace the connector URL.

## Troubleshooting

### Tailscale sign-in never appears

Confirm the phone has internet access and restart LocalAnt. The embedded node writes state under the app-private `filesDir/tailscale` directory.

### Funnel reports HTTPS or certificate errors

Enable MagicDNS and HTTPS certificates in the Tailscale DNS settings, then stop and restart LocalAnt.

### Funnel policy error

Grant the `funnel` node attribute to this node or its dedicated tag. Keep the grant narrowly scoped.

### Connector cannot connect

- Confirm LocalAnt still shows `RUNNING`.
- Re-copy the current URL; token rotation invalidates the old URL.
- Confirm the Tailscale node remains authorized.
- Confirm port 443 Funnel access is permitted.
- Disable aggressive OEM battery restrictions for LocalAnt.

### Accessibility tools fail

- Confirm the Accessibility service is still enabled.
- Secure windows can prevent screenshots.
- Password fields are omitted from UI trees and cannot receive text.
- LocalAnt, authenticators, wallets, password managers, and banking-like packages are blocked by device policy.
- UI-tree and node operations fail with `WINDOW_MISMATCH` or `STALE_UI_SNAPSHOT` instead of controlling a hidden app when Android exposes an outdated accessibility root.

### App launch fails

- Unlock the phone before calling `device_launch_app`.
- Grant LocalAnt **Display over other apps** permission from the app setup screen.
- A successful response means the requested package was observed in the foreground; blocked launches return `APP_LAUNCH_BLOCKED` rather than a false success.

### Shell command rejected

The shell is intentionally conservative. It rejects paths outside the workspace, traversal, redirects outside the workspace, command/parameter expansion, heredocs, link creation, privileged/system-management commands, and other ambiguous constructs.
