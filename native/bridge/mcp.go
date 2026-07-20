package nativebridge

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

const (
	mcpPath          = "/mcp"
	healthPath       = "/healthz"
	maxRequestBytes  = 1 << 20
	defaultRateLimit = 120
	defaultRateBurst = 30
	latestProtocol   = "2025-11-25"
	maxSessions      = 1024
	sessionTTL       = 24 * time.Hour
)

var supportedProtocols = map[string]struct{}{
	"2025-11-25": {},
	"2025-06-18": {},
	"2025-03-26": {},
}

// Host is implemented by the generated Kotlin adapter. All values crossing
// this boundary are JSON so gomobile never needs to bind Kotlin/Go model types.
type Host interface {
	ListToolsJSON() string
	ExecuteTool(tool, inputJSON, sessionID string) string
}

type serverOptions struct {
	now       func() time.Time
	rand      io.Reader
	rateLimit int
	rateBurst int
}

type mcpServer struct {
	host     Host
	token    string
	now      func() time.Time
	rand     io.Reader
	sessions *sessionStore
	limiter  *fixedWindowLimiter
}

func newMCPServer(host Host, token string, opts serverOptions) http.Handler {
	if opts.now == nil {
		opts.now = time.Now
	}
	if opts.rand == nil {
		opts.rand = rand.Reader
	}
	if opts.rateLimit <= 0 {
		opts.rateLimit = defaultRateLimit
	}
	if opts.rateBurst <= 0 {
		opts.rateBurst = defaultRateBurst
	}
	return &mcpServer{
		host:     host,
		token:    token,
		now:      opts.now,
		rand:     opts.rand,
		sessions: newSessionStore(opts.now),
		limiter:  newFixedWindowLimiter(opts.rateLimit+opts.rateBurst, time.Minute, opts.now),
	}
}

func (s *mcpServer) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")

	if r.URL.Path == healthPath {
		if r.Method != http.MethodGet {
			w.Header().Set("Allow", http.MethodGet)
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"ok":true}`)
		return
	}
	if r.URL.Path != mcpPath {
		http.NotFound(w, r)
		return
	}
	if !validOrigin(r.Header.Get("Origin")) {
		writeJSONRPC(w, http.StatusForbidden, rpcError(nil, -32000, "invalid Origin header", nil))
		return
	}
	if !s.authorized(r) {
		w.Header().Set("WWW-Authenticate", `Bearer realm="LocalAnt MCP"`)
		writeJSONRPC(w, http.StatusUnauthorized, rpcError(nil, -32001, "unauthorized", nil))
		return
	}
	if !s.limiter.Allow(clientKey(r)) {
		w.Header().Set("Retry-After", "60")
		writeJSONRPC(w, http.StatusTooManyRequests, rpcError(nil, -32002, "rate limit exceeded", nil))
		return
	}

	switch r.Method {
	case http.MethodPost:
		s.handlePost(w, r)
	case http.MethodDelete:
		s.handleDelete(w, r)
	case http.MethodGet:
		w.Header().Set("Allow", "POST, DELETE")
		http.Error(w, "SSE streaming is not supported", http.StatusMethodNotAllowed)
	default:
		w.Header().Set("Allow", "POST, DELETE")
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func (s *mcpServer) authorized(r *http.Request) bool {
	if s.token == "" {
		return false
	}
	candidate := ""
	if auth := r.Header.Get("Authorization"); strings.HasPrefix(strings.ToLower(auth), "bearer ") {
		candidate = strings.TrimSpace(auth[len("Bearer "):])
	}
	if candidate == "" {
		candidate = r.URL.Query().Get("key")
	}
	return constantTimeEqual(candidate, s.token)
}

func constantTimeEqual(a, b string) bool {
	if len(a) != len(b) {
		// Compare against the real token even for mismatched lengths to avoid a
		// fast path that reveals the expected token length.
		padded := make([]byte, len(b))
		copy(padded, a)
		_ = subtle.ConstantTimeCompare(padded, []byte(b))
		return false
	}
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

func validOrigin(raw string) bool {
	if raw == "" || raw == "null" {
		return true
	}
	u, err := url.Parse(raw)
	if err != nil || u.Hostname() == "" {
		return false
	}
	host := strings.ToLower(u.Hostname())
	if u.Scheme == "https" {
		return host == "chatgpt.com" ||
			strings.HasSuffix(host, ".chatgpt.com") ||
			host == "openai.com" ||
			strings.HasSuffix(host, ".openai.com")
	}
	if u.Scheme != "http" {
		return false
	}
	return host == "localhost" || host == "127.0.0.1" || host == "::1"
}

func (s *mcpServer) handleDelete(w http.ResponseWriter, r *http.Request) {
	id := r.Header.Get("MCP-Session-Id")
	if id == "" || !s.sessions.Delete(id) {
		http.Error(w, "unknown MCP session", http.StatusNotFound)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *mcpServer) handlePost(w http.ResponseWriter, r *http.Request) {
	defer r.Body.Close()
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxRequestBytes))
	if err != nil {
		writeJSONRPC(w, http.StatusRequestEntityTooLarge, rpcError(nil, -32700, "request body too large", nil))
		return
	}
	var req rpcRequest
	if err := json.Unmarshal(body, &req); err != nil || req.JSONRPC != "2.0" || req.Method == "" {
		writeJSONRPC(w, http.StatusBadRequest, rpcError(nil, -32700, "invalid JSON-RPC request", nil))
		return
	}

	if req.Method == "initialize" {
		s.handleInitialize(w, req)
		return
	}

	sessionID := r.Header.Get("MCP-Session-Id")
	if sessionID == "" || !s.sessions.Exists(sessionID) {
		http.Error(w, "missing or unknown MCP session", http.StatusBadRequest)
		return
	}
	if version := r.Header.Get("MCP-Protocol-Version"); version != "" {
		if _, ok := supportedProtocols[version]; !ok {
			http.Error(w, "unsupported MCP protocol version", http.StatusBadRequest)
			return
		}
	}

	switch req.Method {
	case "notifications/initialized":
		w.WriteHeader(http.StatusAccepted)
	case "ping":
		writeJSONRPC(w, http.StatusOK, rpcResult(req.ID, map[string]any{}))
	case "tools/list":
		s.handleToolsList(w, req)
	case "tools/call":
		s.handleToolsCall(w, req, sessionID)
	default:
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32601, "method not found", nil))
	}
}

func (s *mcpServer) handleInitialize(w http.ResponseWriter, req rpcRequest) {
	var params struct {
		ProtocolVersion string `json:"protocolVersion"`
	}
	_ = json.Unmarshal(req.Params, &params)
	version := params.ProtocolVersion
	if _, ok := supportedProtocols[version]; !ok {
		version = latestProtocol
	}
	sessionID, err := randomSessionID(s.rand)
	if err != nil {
		writeJSONRPC(w, http.StatusInternalServerError, rpcError(req.ID, -32603, "failed to create MCP session", nil))
		return
	}
	s.sessions.Add(sessionID, version)
	w.Header().Set("MCP-Session-Id", sessionID)
	writeJSONRPC(w, http.StatusOK, rpcResult(req.ID, map[string]any{
		"protocolVersion": version,
		"capabilities": map[string]any{
			"tools": map[string]any{"listChanged": false},
		},
		"serverInfo": map[string]any{
			"name":    "LocalAnt Android",
			"version": "0.1.0",
		},
		"instructions": "Android operations are executed locally. Sensitive tools may return APPROVAL_REQUIRED; approve the request on the phone and retry with the supplied _approvalId.",
	}))
}

func (s *mcpServer) handleToolsList(w http.ResponseWriter, req rpcRequest) {
	if s.host == nil {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32603, "host unavailable", nil))
		return
	}
	var rawTools []struct {
		Name        string          `json:"name"`
		Description string          `json:"description"`
		InputSchema json.RawMessage `json:"inputSchema"`
	}
	hostTools, hostError := callHostListTools(s.host)
	if hostError != nil {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32603, "host failed while listing tools", nil))
		return
	}
	if err := json.Unmarshal([]byte(hostTools), &rawTools); err != nil {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32603, "host returned invalid tool metadata", nil))
		return
	}
	tools := make([]map[string]any, 0, len(rawTools))
	for _, tool := range rawTools {
		var schema any
		if err := json.Unmarshal(tool.InputSchema, &schema); err != nil {
			writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32603, "host returned an invalid input schema", nil))
			return
		}
		tools = append(tools, map[string]any{
			"name":        tool.Name,
			"description": tool.Description,
			"inputSchema": schema,
		})
	}
	writeJSONRPC(w, http.StatusOK, rpcResult(req.ID, map[string]any{"tools": tools}))
}

func (s *mcpServer) handleToolsCall(w http.ResponseWriter, req rpcRequest, sessionID string) {
	if s.host == nil {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32603, "host unavailable", nil))
		return
	}
	var params struct {
		Name      string          `json:"name"`
		Arguments json.RawMessage `json:"arguments"`
	}
	if err := json.Unmarshal(req.Params, &params); err != nil || params.Name == "" {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32602, "tools/call requires a tool name", nil))
		return
	}
	if len(params.Arguments) == 0 || string(params.Arguments) == "null" {
		params.Arguments = json.RawMessage(`{}`)
	}
	var object map[string]any
	if err := json.Unmarshal(params.Arguments, &object); err != nil {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32602, "tool arguments must be a JSON object", nil))
		return
	}

	hostOutput, hostError := callHostExecute(s.host, params.Name, string(params.Arguments), sessionID)
	if hostError != nil {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32603, "host failed while executing the tool", nil))
		return
	}
	var result hostResult
	if err := json.Unmarshal([]byte(hostOutput), &result); err != nil {
		writeJSONRPC(w, http.StatusOK, rpcError(req.ID, -32603, "host returned an invalid tool result", nil))
		return
	}
	if result.Success {
		text := rawJSONText(result.Content)
		response := map[string]any{
			"content": []map[string]any{{"type": "text", "text": text}},
			"isError": false,
		}
		var structured map[string]any
		if len(result.Content) != 0 && json.Unmarshal(result.Content, &structured) == nil {
			response["structuredContent"] = structured
		}
		writeJSONRPC(w, http.StatusOK, rpcResult(req.ID, response))
		return
	}

	failure := map[string]any{
		"code":    result.Code,
		"message": result.Message,
	}
	if len(result.Details) != 0 && string(result.Details) != "null" {
		var details any
		if json.Unmarshal(result.Details, &details) == nil {
			failure["details"] = details
		}
	}
	failureJSON, _ := json.Marshal(failure)
	writeJSONRPC(w, http.StatusOK, rpcResult(req.ID, map[string]any{
		"content":           []map[string]any{{"type": "text", "text": string(failureJSON)}},
		"structuredContent": failure,
		"isError":           true,
	}))
}

type rpcRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id,omitempty"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params,omitempty"`
}

type rpcResponse struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id,omitempty"`
	Result  any             `json:"result,omitempty"`
	Error   *rpcErrorObject `json:"error,omitempty"`
}

type rpcErrorObject struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

type hostResult struct {
	Success bool            `json:"success"`
	Content json.RawMessage `json:"content,omitempty"`
	Code    string          `json:"code,omitempty"`
	Message string          `json:"message,omitempty"`
	Details json.RawMessage `json:"details,omitempty"`
}

func rpcResult(id json.RawMessage, result any) rpcResponse {
	return rpcResponse{JSONRPC: "2.0", ID: id, Result: result}
}

func rpcError(id json.RawMessage, code int, message string, data any) rpcResponse {
	return rpcResponse{JSONRPC: "2.0", ID: id, Error: &rpcErrorObject{Code: code, Message: message, Data: data}}
}

func writeJSONRPC(w http.ResponseWriter, status int, response rpcResponse) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(response)
}

func randomSessionID(reader io.Reader) (string, error) {
	buf := make([]byte, 24)
	if _, err := io.ReadFull(reader, buf); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

func rawJSONText(raw json.RawMessage) string {
	if len(raw) == 0 {
		return "null"
	}
	var text string
	if json.Unmarshal(raw, &text) == nil {
		return text
	}
	return string(raw)
}

func clientKey(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil && host != "" {
		return host
	}
	if r.RemoteAddr == "" {
		return "unknown"
	}
	return r.RemoteAddr
}

func callHostListTools(host Host) (output string, err error) {
	defer func() {
		if recovered := recover(); recovered != nil {
			err = fmt.Errorf("host panic: %v", recovered)
		}
	}()
	return host.ListToolsJSON(), nil
}

func callHostExecute(host Host, tool, inputJSON, sessionID string) (output string, err error) {
	defer func() {
		if recovered := recover(); recovered != nil {
			err = fmt.Errorf("host panic: %v", recovered)
		}
	}()
	return host.ExecuteTool(tool, inputJSON, sessionID), nil
}

type sessionStore struct {
	mu       sync.Mutex
	sessions map[string]sessionEntry
	now      func() time.Time
}

type sessionEntry struct {
	version   string
	createdAt time.Time
}

func newSessionStore(now func() time.Time) *sessionStore {
	return &sessionStore{sessions: make(map[string]sessionEntry), now: now}
}

func (s *sessionStore) Add(id, version string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now()
	s.removeExpiredLocked(now)
	if len(s.sessions) >= maxSessions {
		var oldestID string
		var oldest time.Time
		for candidateID, entry := range s.sessions {
			if oldestID == "" || entry.createdAt.Before(oldest) {
				oldestID = candidateID
				oldest = entry.createdAt
			}
		}
		delete(s.sessions, oldestID)
	}
	s.sessions[id] = sessionEntry{version: version, createdAt: now}
}

func (s *sessionStore) Exists(id string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now()
	entry, ok := s.sessions[id]
	if !ok {
		return false
	}
	if now.Sub(entry.createdAt) >= sessionTTL {
		delete(s.sessions, id)
		return false
	}
	return true
}

func (s *sessionStore) Delete(id string) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.sessions[id]; !ok {
		return false
	}
	delete(s.sessions, id)
	return true
}

func (s *sessionStore) removeExpiredLocked(now time.Time) {
	for id, entry := range s.sessions {
		if now.Sub(entry.createdAt) >= sessionTTL {
			delete(s.sessions, id)
		}
	}
}

type fixedWindowLimiter struct {
	mu     sync.Mutex
	limit  int
	window time.Duration
	now    func() time.Time
	items  map[string]windowCounter
}

type windowCounter struct {
	started time.Time
	count   int
}

func newFixedWindowLimiter(limit int, window time.Duration, now func() time.Time) *fixedWindowLimiter {
	return &fixedWindowLimiter{limit: limit, window: window, now: now, items: make(map[string]windowCounter)}
}

func (l *fixedWindowLimiter) Allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := l.now()
	for existingKey, existing := range l.items {
		if now.Sub(existing.started) >= l.window {
			delete(l.items, existingKey)
		}
	}
	item := l.items[key]
	if item.started.IsZero() {
		l.items[key] = windowCounter{started: now, count: 1}
		return true
	}
	if item.count >= l.limit {
		return false
	}
	item.count++
	l.items[key] = item
	return true
}

func validateStartInputs(host Host, stateDir, hostname, token string) error {
	if host == nil {
		return errors.New("host is required")
	}
	if strings.TrimSpace(stateDir) == "" {
		return errors.New("state directory is required")
	}
	if strings.TrimSpace(hostname) == "" {
		return errors.New("hostname is required")
	}
	if len(token) < 32 {
		return fmt.Errorf("access token must contain at least 32 characters")
	}
	return nil
}
