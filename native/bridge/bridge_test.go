package nativebridge

import (
	"errors"
	"strings"
	"testing"
)

func TestBridgeStartValidatesInputsBeforeStartingGoroutine(t *testing.T) {
	if err := NewBridge(nil).Start("state", "phone", "abcdefghijklmnopqrstuvwxyz012345"); err == nil {
		t.Fatal("expected missing host error")
	}
	bridge := NewBridge(&fakeHost{})
	if err := bridge.Start("", "phone", "abcdefghijklmnopqrstuvwxyz012345"); err == nil {
		t.Fatal("expected missing state directory error")
	}
	if err := bridge.Start("state", "", "abcdefghijklmnopqrstuvwxyz012345"); err == nil {
		t.Fatal("expected missing hostname error")
	}
	if err := bridge.Start("state", "phone", "short"); err == nil {
		t.Fatal("expected short token error")
	}
	if bridge.Status() != statusStopped {
		t.Fatalf("status=%q", bridge.Status())
	}
}

func TestSetErrorClearsStaleAuthorizationState(t *testing.T) {
	bridge := NewBridge(&fakeHost{})
	bridge.status = statusAuthRequired
	bridge.authURL = "https://login.tailscale.com/example"

	bridge.setError("failed")

	if bridge.Status() != statusError {
		t.Fatalf("status=%q", bridge.Status())
	}
	if bridge.AuthURL() != "" {
		t.Fatalf("stale auth URL=%q", bridge.AuthURL())
	}
}

func TestFriendlyFunnelErrorsAreActionable(t *testing.T) {
	cases := []struct {
		message string
		want    string
	}{
		{"Funnel not available; HTTPS must be enabled", "MagicDNS"},
		{"funnel access denied by node capability", "tailnet policy"},
		{"authentication login failed", "authorization URL"},
		{"network unreachable", "port 443"},
	}
	for _, tc := range cases {
		got := friendlyFunnelError(errors.New(tc.message))
		if !strings.Contains(got, tc.want) {
			t.Fatalf("friendlyFunnelError(%q)=%q, want substring %q", tc.message, got, tc.want)
		}
	}
}
