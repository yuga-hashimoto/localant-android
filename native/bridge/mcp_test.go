package nativebridge

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

type fakeHost struct {
	tools       string
	result      string
	lastTool    string
	lastInput   string
	lastSession string
}

type panicHost struct{}

func (panicHost) ListToolsJSON() string {
	panic("list failed")
}

func (panicHost) ExecuteTool(tool, inputJSON, sessionID string) string {
	panic("execute failed")
}

func (f *fakeHost) ListToolsJSON() string {
	if f.tools == "" {
		return `[]`
	}
	return f.tools
}

func (f *fakeHost) ExecuteTool(tool, inputJSON, sessionID string) string {
	f.lastTool = tool
	f.lastInput = inputJSON
	f.lastSession = sessionID
	if f.result == "" {
		return `{"success":true,"content":{"ok":true}}`
	}
	return f.result
}

func TestRejectsMissingOrInvalidAuth(t *testing.T) {
	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{})
	for _, auth := range []string{"", "Bearer wrong"} {
		req := httptest.NewRequest(http.MethodPost, mcpPath, strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"initialize"}`))
		if auth != "" {
			req.Header.Set("Authorization", auth)
		}
		res := httptest.NewRecorder()
		h.ServeHTTP(res, req)
		if res.Code != http.StatusUnauthorized {
			t.Fatalf("auth %q: got %d, want %d", auth, res.Code, http.StatusUnauthorized)
		}
	}
}

func TestConstantTimeEqual(t *testing.T) {
	if !constantTimeEqual("same-value", "same-value") {
		t.Fatal("equal strings must match")
	}
	if constantTimeEqual("short", "much-longer-value") {
		t.Fatal("different strings must not match")
	}
}

func TestInitializeNegotiatesVersionAndCreatesSession(t *testing.T) {
	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 24))})
	res := post(t, h, `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}`, "", "")
	if res.Code != http.StatusOK {
		t.Fatalf("status=%d body=%s", res.Code, res.Body.String())
	}
	if got := res.Header().Get("MCP-Session-Id"); got == "" {
		t.Fatal("missing session header")
	}
	var body map[string]any
	decodeJSON(t, res, &body)
	result := body["result"].(map[string]any)
	if result["protocolVersion"] != "2025-06-18" {
		t.Fatalf("protocolVersion=%v", result["protocolVersion"])
	}
	serverInfo := result["serverInfo"].(map[string]any)
	if serverInfo["version"] != "0.1.8" {
		t.Fatalf("serverInfo=%v", serverInfo)
	}
	instructions := result["instructions"].(string)
	if strings.Contains(instructions, "APPROVAL_REQUIRED") ||
		!strings.Contains(instructions, "without local approval") ||
		!strings.Contains(instructions, "Display over other apps") {
		t.Fatalf("instructions=%q", instructions)
	}
}

func TestInitializeFallsBackToLatestSupportedVersion(t *testing.T) {
	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 24))})
	res := post(t, h, `{"jsonrpc":"2.0","id":"x","method":"initialize","params":{"protocolVersion":"2099-01-01"}}`, "", "")
	var body map[string]any
	decodeJSON(t, res, &body)
	result := body["result"].(map[string]any)
	if result["protocolVersion"] != latestProtocol {
		t.Fatalf("protocolVersion=%v want=%s", result["protocolVersion"], latestProtocol)
	}
}

func TestToolsListRequiresSessionAndReturnsHostTools(t *testing.T) {
	host := &fakeHost{tools: `[{"name":"device_status","description":"Status","risk":0,"inputSchema":{"type":"object","properties":{}}}]`}
	h := newMCPServer(host, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})

	missing := post(t, h, `{"jsonrpc":"2.0","id":2,"method":"tools/list"}`, "", "")
	if missing.Code != http.StatusBadRequest {
		t.Fatalf("missing session status=%d", missing.Code)
	}

	session := initialize(t, h)
	res := post(t, h, `{"jsonrpc":"2.0","id":2,"method":"tools/list"}`, session, latestProtocol)
	if res.Code != http.StatusOK {
		t.Fatalf("status=%d body=%s", res.Code, res.Body.String())
	}
	var body map[string]any
	decodeJSON(t, res, &body)
	tools := body["result"].(map[string]any)["tools"].([]any)
	tool := tools[0].(map[string]any)
	if tool["name"] != "device_status" {
		t.Fatalf("tool=%v", tool)
	}
	if _, exists := tool["risk"]; exists {
		t.Fatal("risk is an internal field and must not leak into MCP tool metadata")
	}
}

func TestToolsCallForwardsArgumentsAndSession(t *testing.T) {
	host := &fakeHost{result: `{"success":true,"content":{"status":"ok"}}`}
	h := newMCPServer(host, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)
	res := post(t, h, `{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"device_status","arguments":{"verbose":true}}}`, session, latestProtocol)
	if res.Code != http.StatusOK {
		t.Fatalf("status=%d body=%s", res.Code, res.Body.String())
	}
	if host.lastTool != "device_status" || host.lastSession != session {
		t.Fatalf("forwarded tool=%q session=%q", host.lastTool, host.lastSession)
	}
	if host.lastInput != `{"verbose":true}` {
		t.Fatalf("input=%s", host.lastInput)
	}
	var body map[string]any
	decodeJSON(t, res, &body)
	result := body["result"].(map[string]any)
	if result["isError"] != false {
		t.Fatalf("result=%v", result)
	}
	structured := result["structuredContent"].(map[string]any)
	if structured["status"] != "ok" {
		t.Fatalf("structured=%v", structured)
	}
}

func TestToolsCallReturnsMCPImageContentBlock(t *testing.T) {
	host := &fakeHost{result: `{"success":true,"content":{"mimeType":"image/png","width":10,"height":20},"contentBlocks":[{"type":"image","data":"iVBORw0KGgo=","mimeType":"image/png"}]}`}
	h := newMCPServer(host, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)
	res := post(t, h, `{"jsonrpc":"2.0","id":31,"method":"tools/call","params":{"name":"device_screenshot","arguments":{}}}`, session, latestProtocol)

	var body map[string]any
	decodeJSON(t, res, &body)
	result := body["result"].(map[string]any)
	content := result["content"].([]any)
	image := content[0].(map[string]any)
	if image["type"] != "image" || image["data"] != "iVBORw0KGgo=" || image["mimeType"] != "image/png" {
		t.Fatalf("image content=%v", image)
	}
	structured := result["structuredContent"].(map[string]any)
	if _, exists := structured["data"]; exists {
		t.Fatalf("structured content must not duplicate image data: %v", structured)
	}
	if structured["width"] != float64(10) || structured["height"] != float64(20) {
		t.Fatalf("structured=%v", structured)
	}
}

func TestNormalizeHostContentBlocksSupportsTextAndResourceLink(t *testing.T) {
	blocks, err := normalizeHostContentBlocks([]json.RawMessage{
		json.RawMessage(`{"type":"text","text":"ready"}`),
		json.RawMessage(`{"type":"resource_link","uri":"file:///screen.png","name":"screen.png","mimeType":"image/png","description":"Latest Android screen"}`),
	})
	if err != nil {
		t.Fatal(err)
	}
	if blocks[0]["type"] != "text" || blocks[0]["text"] != "ready" {
		t.Fatalf("text=%v", blocks[0])
	}
	if blocks[1]["type"] != "resource_link" || blocks[1]["uri"] != "file:///screen.png" || blocks[1]["name"] != "screen.png" {
		t.Fatalf("resource=%v", blocks[1])
	}
}

func TestToolsCallRejectsUnsafeImageMIME(t *testing.T) {
	host := &fakeHost{result: `{"success":true,"content":{"mimeType":"image/svg+xml"},"contentBlocks":[{"type":"image","data":"PHN2Zz48L3N2Zz4=","mimeType":"image/svg+xml"}]}`}
	h := newMCPServer(host, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)
	res := post(t, h, `{"jsonrpc":"2.0","id":33,"method":"tools/call","params":{"name":"device_screenshot","arguments":{}}}`, session, latestProtocol)

	var body map[string]any
	decodeJSON(t, res, &body)
	if body["error"].(map[string]any)["code"] != float64(-32603) {
		t.Fatalf("response=%v", body)
	}
}

func TestToolsCallRejectsMalformedHostImageContent(t *testing.T) {
	host := &fakeHost{result: `{"success":true,"content":{"mimeType":"image/png"},"contentBlocks":[{"type":"image","data":"not-base64","mimeType":"image/png"}]}`}
	h := newMCPServer(host, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)
	res := post(t, h, `{"jsonrpc":"2.0","id":32,"method":"tools/call","params":{"name":"device_screenshot","arguments":{}}}`, session, latestProtocol)

	var body map[string]any
	decodeJSON(t, res, &body)
	if body["error"].(map[string]any)["code"] != float64(-32603) {
		t.Fatalf("response=%v", body)
	}
}

func TestToolFailurePreservesApprovalDetails(t *testing.T) {
	host := &fakeHost{result: `{"success":false,"code":"APPROVAL_REQUIRED","message":"Approve on phone","details":{"approvalId":"abc"}}`}
	h := newMCPServer(host, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)
	res := post(t, h, `{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"device_tap","arguments":{"x":1,"y":2}}}`, session, latestProtocol)
	var body map[string]any
	decodeJSON(t, res, &body)
	result := body["result"].(map[string]any)
	if result["isError"] != true {
		t.Fatalf("result=%v", result)
	}
	structured := result["structuredContent"].(map[string]any)
	if structured["code"] != "APPROVAL_REQUIRED" {
		t.Fatalf("structured=%v", structured)
	}
	details := structured["details"].(map[string]any)
	if details["approvalId"] != "abc" {
		t.Fatalf("details=%v", details)
	}
}

func TestInitializedNotificationReturnsAccepted(t *testing.T) {
	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)
	res := post(t, h, `{"jsonrpc":"2.0","method":"notifications/initialized"}`, session, latestProtocol)
	if res.Code != http.StatusAccepted || res.Body.Len() != 0 {
		t.Fatalf("status=%d body=%q", res.Code, res.Body.String())
	}
}

func TestMalformedJSONAndUnknownMethod(t *testing.T) {
	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	bad := post(t, h, `{bad`, "", "")
	if bad.Code != http.StatusBadRequest {
		t.Fatalf("bad json status=%d", bad.Code)
	}
	session := initialize(t, h)
	unknown := post(t, h, `{"jsonrpc":"2.0","id":9,"method":"unknown"}`, session, latestProtocol)
	var body map[string]any
	decodeJSON(t, unknown, &body)
	errObject := body["error"].(map[string]any)
	if errObject["code"] != float64(-32601) {
		t.Fatalf("error=%v", errObject)
	}
}

func TestOriginPolicy(t *testing.T) {
	allowed := []string{
		"",
		"null",
		"https://chatgpt.com",
		"https://connector.chatgpt.com",
		"https://openai.com",
		"https://api.openai.com",
		"http://localhost:3000",
		"http://127.0.0.1:3000",
	}
	for _, origin := range allowed {
		if !validOrigin(origin) {
			t.Fatalf("origin %q should be allowed", origin)
		}
	}
	for _, origin := range []string{
		"http://evil.example",
		"https://evil.example",
		"https://chatgpt.com.evil.example",
		"file:///tmp/test",
	} {
		if validOrigin(origin) {
			t.Fatalf("origin %q should be rejected", origin)
		}
	}

	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{})
	req := httptest.NewRequest(http.MethodPost, mcpPath, strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"initialize"}`))
	req.Header.Set("Authorization", "Bearer abcdefghijklmnopqrstuvwxyz012345")
	req.Header.Set("Origin", "https://evil.example")
	res := httptest.NewRecorder()
	h.ServeHTTP(res, req)
	if res.Code != http.StatusForbidden {
		t.Fatalf("status=%d", res.Code)
	}
}

func TestRateLimiting(t *testing.T) {
	now := time.Unix(100, 0)
	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{
		now:       func() time.Time { return now },
		rateLimit: 1,
		rateBurst: 1,
	})
	for i, want := range []int{http.StatusOK, http.StatusOK, http.StatusTooManyRequests} {
		res := post(t, h, `{"jsonrpc":"2.0","id":1,"method":"initialize"}`, "", "")
		if res.Code != want {
			t.Fatalf("request %d status=%d want=%d", i, res.Code, want)
		}
	}
}

func TestHostPanicsBecomeInternalErrors(t *testing.T) {
	h := newMCPServer(panicHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)

	list := post(t, h, `{"jsonrpc":"2.0","id":2,"method":"tools/list"}`, session, latestProtocol)
	var listBody map[string]any
	decodeJSON(t, list, &listBody)
	if listBody["error"].(map[string]any)["code"] != float64(-32603) {
		t.Fatalf("tools/list response=%v", listBody)
	}

	call := post(t, h, `{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"device_status","arguments":{}}}`, session, latestProtocol)
	var callBody map[string]any
	decodeJSON(t, call, &callBody)
	if callBody["error"].(map[string]any)["code"] != float64(-32603) {
		t.Fatalf("tools/call response=%v", callBody)
	}
}

func TestSessionStoreExpiresAndBoundsEntries(t *testing.T) {
	now := time.Unix(100, 0)
	store := newSessionStore(func() time.Time { return now })
	store.Add("old", latestProtocol)
	now = now.Add(sessionTTL)
	if store.Exists("old") {
		t.Fatal("expired session must not exist")
	}

	for i := 0; i < maxSessions+1; i++ {
		store.Add(fmt.Sprintf("session-%04d", i), latestProtocol)
		now = now.Add(time.Millisecond)
	}
	if len(store.sessions) != maxSessions {
		t.Fatalf("sessions=%d want=%d", len(store.sessions), maxSessions)
	}
	if store.Exists("session-0000") {
		t.Fatal("oldest session should be evicted at capacity")
	}
}

func TestDeleteSession(t *testing.T) {
	h := newMCPServer(&fakeHost{}, "abcdefghijklmnopqrstuvwxyz012345", serverOptions{rand: bytes.NewReader(make([]byte, 48))})
	session := initialize(t, h)
	req := httptest.NewRequest(http.MethodDelete, mcpPath, nil)
	req.Header.Set("Authorization", "Bearer abcdefghijklmnopqrstuvwxyz012345")
	req.Header.Set("MCP-Session-Id", session)
	res := httptest.NewRecorder()
	h.ServeHTTP(res, req)
	if res.Code != http.StatusNoContent {
		t.Fatalf("status=%d", res.Code)
	}
	after := post(t, h, `{"jsonrpc":"2.0","id":2,"method":"ping"}`, session, latestProtocol)
	if after.Code != http.StatusBadRequest {
		t.Fatalf("after delete status=%d", after.Code)
	}
}

func initialize(t *testing.T, h http.Handler) string {
	t.Helper()
	res := post(t, h, `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25"}}`, "", "")
	if res.Code != http.StatusOK {
		t.Fatalf("initialize status=%d body=%s", res.Code, res.Body.String())
	}
	return res.Header().Get("MCP-Session-Id")
}

func post(t *testing.T, h http.Handler, body, session, version string) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, mcpPath, strings.NewReader(body))
	req.RemoteAddr = "203.0.113.10:12345"
	req.Header.Set("Authorization", "Bearer abcdefghijklmnopqrstuvwxyz012345")
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json, text/event-stream")
	if session != "" {
		req.Header.Set("MCP-Session-Id", session)
	}
	if version != "" {
		req.Header.Set("MCP-Protocol-Version", version)
	}
	res := httptest.NewRecorder()
	h.ServeHTTP(res, req)
	return res
}

func decodeJSON(t *testing.T, res *httptest.ResponseRecorder, target any) {
	t.Helper()
	if err := json.Unmarshal(res.Body.Bytes(), target); err != nil {
		t.Fatalf("decode %q: %v", res.Body.String(), err)
	}
}
