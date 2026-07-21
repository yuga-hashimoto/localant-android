package nativebridge

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"
)

func TestPreflightCertificateUsesConfiguredFetcher(t *testing.T) {
	bridge := NewBridge(&fakeHost{})
	called := false
	bridge.certificatePreflight = func(ctx context.Context, domain string) error {
		called = true
		if domain != "phone.example.ts.net" {
			t.Fatalf("domain=%q", domain)
		}
		deadline, ok := ctx.Deadline()
		if !ok {
			t.Fatal("preflight context must have a deadline")
		}
		if remaining := time.Until(deadline); remaining <= 0 || remaining > certificatePreflightTimeout {
			t.Fatalf("remaining=%s", remaining)
		}
		return nil
	}

	if err := bridge.preflightCertificate(context.Background(), "phone.example.ts.net"); err != nil {
		t.Fatal(err)
	}
	if !called {
		t.Fatal("certificate preflight was not called")
	}
}

func TestPreflightCertificateReturnsActionableError(t *testing.T) {
	bridge := NewBridge(&fakeHost{})
	bridge.certificatePreflight = func(context.Context, string) error {
		return errors.New("acme: too many certificates already issued for exact set of domains")
	}

	err := bridge.preflightCertificate(context.Background(), "phone.example.ts.net")
	if err == nil {
		t.Fatal("expected preflight failure")
	}
	message := strings.ToLower(err.Error())
	if !strings.Contains(message, "certificate") || !strings.Contains(message, "rate limit") {
		t.Fatalf("unexpected message: %s", err)
	}
}

func TestPreflightCertificateHonorsParentCancellation(t *testing.T) {
	bridge := NewBridge(&fakeHost{})
	bridge.certificatePreflight = func(ctx context.Context, _ string) error {
		<-ctx.Done()
		return ctx.Err()
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	if err := bridge.preflightCertificate(ctx, "phone.example.ts.net"); !errors.Is(err, context.Canceled) && !strings.Contains(strings.ToLower(err.Error()), "canceled") {
		t.Fatalf("unexpected cancellation error: %v", err)
	}
}

func TestFriendlyCertificateErrorCoversConfigurationFailures(t *testing.T) {
	cases := []struct {
		name string
		err  error
		want string
	}{
		{name: "https disabled", err: errors.New("HTTPS must be enabled"), want: "https certificates"},
		{name: "permission", err: errors.New("not allowed to get cert"), want: "permission"},
		{name: "timeout", err: context.DeadlineExceeded, want: "timed out"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			message := strings.ToLower(friendlyCertificateError(tc.err))
			if !strings.Contains(message, tc.want) {
				t.Fatalf("message=%q, want substring %q", message, tc.want)
			}
		})
	}
}
