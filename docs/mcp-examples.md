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

## Approval flow

The first sensitive call returns an MCP tool result with `isError: true` and structured content similar to:

```json
{
  "code": "APPROVAL_REQUIRED",
  "message": "Approve this operation on the Android device, then retry with _approvalId.",
  "details": {
    "approvalId": "request-id-from-phone",
    "expiresAtMs": 1780000000000
  }
}
```

Approve the request on the phone, then retry the same tool and session with `_approvalId`:

```bash
export APPROVAL_ID='request-id-from-phone'

curl --silent --show-error \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  -H 'Mcp-Protocol-Version: 2025-11-25' \
  -H 'Content-Type: application/json' \
  --data "{
    \"jsonrpc\": \"2.0\",
    \"id\": 4,
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"shell_execute\",
      \"arguments\": {
        \"command\": \"pwd\",
        \"_approvalId\": \"$APPROVAL_ID\"
      }
    }
  }" \
  "$LOCALANT_HOST/mcp"
```

An approval ID is valid only for the matching tool and MCP session and is consumed once.

## Delete the MCP session

```bash
curl --include \
  --request DELETE \
  -H "Authorization: Bearer $LOCALANT_TOKEN" \
  -H "Mcp-Session-Id: $MCP_SESSION_ID" \
  "$LOCALANT_HOST/mcp"
```
