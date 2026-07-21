package nativebridge

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"sync"
	"time"

	"tailscale.com/envknob"
	"tailscale.com/tsnet"
)

const (
	statusStopped      = "STOPPED"
	statusStarting     = "STARTING"
	statusAuthRequired = "AUTH_REQUIRED"
	statusRunning      = "RUNNING"
	statusError        = "ERROR"
)

var authURLPattern = regexp.MustCompile(`https://login\.tailscale\.com/[A-Za-z0-9/_?=&.%-]+`)

// Bridge owns one embedded tsnet node and its Streamable HTTP MCP server.
// Start is asynchronous because first-time Tailscale login requires the user
// to open an authorization URL while tsnet waits for enrollment.
type Bridge struct {
	mu sync.RWMutex

	host Host

	status    string
	publicURL string
	authURL   string
	lastError string

	server     *tsnet.Server
	listener   net.Listener
	httpServer *http.Server
	cancel     context.CancelFunc

	// networkChangeHook is used by tests. In production, network changes are
	// injected into tsnet's live netmon monitor through server.Sys().NetMon.
	networkChangeHook func()
}

// NewBridge creates a stopped bridge bound to a Kotlin Host callback.
func NewBridge(host Host) *Bridge {
	return &Bridge{host: host, status: statusStopped}
}

// Start begins Tailscale enrollment and Funnel hosting in a background
// goroutine. Poll Status, AuthURL, PublicURL, and LastError for progress.
func (b *Bridge) Start(stateDir, hostname, accessToken string) error {
	if err := validateStartInputs(b.host, stateDir, hostname, accessToken); err != nil {
		return err
	}
	if !androidNetworkConfigured() {
		return fmt.Errorf("Android network state must be supplied before starting tsnet")
	}

	b.mu.Lock()
	if b.status == statusStarting || b.status == statusAuthRequired || b.status == statusRunning {
		b.mu.Unlock()
		return nil
	}
	ctx, cancel := context.WithCancel(context.Background())
	b.cancel = cancel
	b.status = statusStarting
	b.publicURL = ""
	b.authURL = ""
	b.lastError = ""
	b.mu.Unlock()

	go b.run(ctx, stateDir, hostname, accessToken)
	return nil
}

func (b *Bridge) run(ctx context.Context, stateDir, hostname, accessToken string) {
	if err := os.MkdirAll(stateDir, 0o700); err != nil {
		b.setError("Could not create the private Tailscale state directory: " + err.Error())
		return
	}
	if err := os.Setenv("TS_LOGS_DIR", stateDir); err != nil {
		b.setError("Could not configure the private Tailscale log directory: " + err.Error())
		return
	}
	// LocalAnt keeps a redacted local audit log and does not upload Tailscale
	// diagnostic logs from the user's phone.
	envknob.SetNoLogsNoSupport()

	srv := &tsnet.Server{
		Dir:      stateDir,
		Hostname: hostname,
		UserLogf: func(format string, args ...any) {
			message := fmt.Sprintf(format, args...)
			if authURL := authURLPattern.FindString(message); authURL != "" {
				b.mu.Lock()
				if b.status == statusStarting {
					b.status = statusAuthRequired
				}
				b.authURL = authURL
				b.mu.Unlock()
			}
		},
		// Backend logs are intentionally discarded. They can contain network
		// metadata and are not needed in the user-facing Android audit log.
		Logf: func(string, ...any) {},
	}

	b.mu.Lock()
	if ctx.Err() != nil {
		b.mu.Unlock()
		return
	}
	b.server = srv
	b.mu.Unlock()

	ln, err := srv.ListenFunnel("tcp", ":443", tsnet.FunnelOnly())
	if err != nil {
		_ = srv.Close()
		if ctx.Err() != nil {
			b.setStopped()
			return
		}
		b.setError(friendlyFunnelError(err))
		return
	}

	domains := srv.CertDomains()
	if len(domains) == 0 {
		_ = ln.Close()
		_ = srv.Close()
		b.setError("Tailscale did not provide an HTTPS certificate domain. Enable MagicDNS and HTTPS certificates for the tailnet, then restart LocalAnt.")
		return
	}
	endpoint := "https://" + domains[0] + mcpPath + "?key=" + url.QueryEscape(accessToken)
	httpServer := &http.Server{
		Handler:           newMCPServer(b.host, accessToken, serverOptions{}),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       70 * time.Second,
		WriteTimeout:      70 * time.Second,
		IdleTimeout:       90 * time.Second,
		MaxHeaderBytes:    32 << 10,
	}

	b.mu.Lock()
	if ctx.Err() != nil {
		b.mu.Unlock()
		_ = ln.Close()
		_ = srv.Close()
		return
	}
	b.listener = ln
	b.httpServer = httpServer
	b.publicURL = endpoint
	b.authURL = ""
	b.lastError = ""
	b.status = statusRunning
	b.mu.Unlock()

	err = httpServer.Serve(ln)
	if err != nil && err != http.ErrServerClosed && ctx.Err() == nil {
		b.setError("MCP HTTPS server stopped unexpectedly: " + err.Error())
	}
}

// Stop immediately closes Funnel, MCP HTTP, and the embedded tsnet node.
func (b *Bridge) Stop() {
	b.mu.Lock()
	cancel := b.cancel
	httpServer := b.httpServer
	listener := b.listener
	server := b.server
	b.cancel = nil
	b.httpServer = nil
	b.listener = nil
	b.server = nil
	b.status = statusStopped
	b.publicURL = ""
	b.authURL = ""
	b.lastError = ""
	b.mu.Unlock()

	if cancel != nil {
		cancel()
	}
	if httpServer != nil {
		_ = httpServer.Close()
	}
	if listener != nil {
		_ = listener.Close()
	}
	if server != nil {
		_ = server.Close()
	}
}

func (b *Bridge) Status() string {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.status
}

func (b *Bridge) PublicURL() string {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.publicURL
}

func (b *Bridge) AuthURL() string {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.authURL
}

func (b *Bridge) LastError() string {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.lastError
}

func (b *Bridge) setStopped() {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.status = statusStopped
	b.publicURL = ""
	b.authURL = ""
}

func (b *Bridge) setError(message string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.status = statusError
	b.lastError = message
	b.publicURL = ""
	b.authURL = ""
}

func friendlyFunnelError(err error) string {
	message := err.Error()
	lower := strings.ToLower(message)
	switch {
	case strings.Contains(lower, "https must be enabled") || strings.Contains(lower, "certificate"):
		return "Tailscale Funnel requires MagicDNS and HTTPS certificates. Enable both in the Tailscale DNS settings, then restart LocalAnt. Details: " + message
	case strings.Contains(lower, "funnel") && (strings.Contains(lower, "access") || strings.Contains(lower, "capability") || strings.Contains(lower, "not allowed")):
		return "This Tailscale account is not allowed to use Funnel. Add the funnel node attribute to the tailnet policy, then restart LocalAnt. Details: " + message
	case strings.Contains(lower, "login") || strings.Contains(lower, "auth"):
		return "Tailscale authentication did not complete. Open the authorization URL in LocalAnt and try again. Details: " + message
	default:
		return "Could not start Tailscale Funnel on port 443. Confirm network access, MagicDNS, HTTPS certificates, and the funnel policy. Details: " + message
	}
}
