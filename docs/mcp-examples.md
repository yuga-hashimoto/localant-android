# MCP request examples

These examples use bearer authentication so the token does not appear in the request URL. Replace the placeholders locally; never commit a real token or connector URL.

```bash
export LOCALANT_HOST='https://your-device.your-tailnet.ts.net'
export LOCALANT_TOKEN='replace-with-the-phone-token'
```

## Health

The health endpoint does not expose tool or device state.

```bash
curl --fail --silent --show-error \
  "$LOCALANT_HOST/healthz"
```

## Initialize

```bash
curl --include \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  --data '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-11-25",
      "capabilities": {},
      "clientInfo": {
        "name": "localant-curl",
        "version": "1.0"
      }
    }
  }' \
  "$LOCALANT_HOST/mcp"
```

Copy the `Mcp-Session-Id` response header for subsequent calls:

```bash
export MCP_SESSION_ID='replace-with-response-header'
```

## Initialized notification

```bash
curl --include \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'Mcp-Protocol-Version: 2025-11-25' \
  -H 'Content-Type: application/json' \
  --data '{
    "jsonrpc": "2.0",
    "method": "notifications/initialized"
  }' \
  "$LOCALANT_HOST/mcp"
```

## List tools

```bash
curl --silent --show-error \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'Mcp-Protocol-Version: 2025-11-25' \
  -H 'Content-Type: application/json' \
  --data '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }' \
  "$LOCALANT_HOST/mcp"
```

## Call a risk-0 tool

```bash
curl --silent --show-error \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'Mcp-Protocol-Version: 2025-11-25' \
  -H 'Content-Type: application/json' \
  --data '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "device_status",
      "arguments": {}
    }
  }' \
  "$LOCALANT_HOST/mcp"
```

## Call a registered action tool

The current build executes registered tools immediately after token and MCP-session validation. No `_approvalId` retry is required.

```bash
curl --silent --show-error \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'Mcp-Protocol-Version: 2025-11-25' \
  -H 'Content-Type: application/json' \
  --data '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "shell_execute",
      "arguments": {
        "command": "pwd"
      }
    }
  }' \
  "$LOCALANT_HOST/mcp"
```

`device_launch_app` can still return stable tool errors such as `DEVICE_LOCKED`, `OVERLAY_PERMISSION_REQUIRED`, or `APP_LAUNCH_BLOCKED`. These indicate that Android did not permit or complete the foreground transition; they are not approval requests.

## Delete the MCP session

```bash
curl --include \
  --request DELETE \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  "$LOCALANT_HOST/mcp"
```
