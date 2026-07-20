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
2. Allow notifications. The foreground notification provides immediate Pause and Stop actions, and approval notifications appear for sensitive operations.
3. Tap **Open Accessibility settings**.
4. Select **LocalAnt Android** and enable the service.
5. Return to LocalAnt. The first card should show **Accessibility enabled**.
6. Tap **Open battery settings** and exclude LocalAnt from manufacturer battery restrictions when available.
7. Tap **Start LocalAnt**.
8. When the status changes to `AUTH_REQUIRED`, tap **Open Tailscale sign-in**.
9. Sign in and authorize the new LocalAnt node.
10. Return to LocalAnt. The app automatically continues from authentication to Funnel startup.
11. When the status is `RUNNING`, tap **Copy MCP URL**.

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

## Approvals

Sensitive calls return `APPROVAL_REQUIRED` to ChatGPT and appear in the app within about one second.

- **Allow once** authorizes that exact request ID and session once.
- **Session** is available only for risk 1–2 tools and authorizes the same tool for the current MCP session.
- **Deny** removes the request.
- Risk 3 actions, including shell and text input, always require a new local approval.

After approval, ChatGPT must retry the same tool call with the supplied `_approvalId` field.

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

### Shell command rejected

The shell is intentionally conservative. It rejects paths outside the workspace, traversal, redirects outside the workspace, command/parameter expansion, heredocs, link creation, privileged/system-management commands, and other ambiguous constructs.
