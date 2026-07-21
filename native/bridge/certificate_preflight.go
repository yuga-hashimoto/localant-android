package nativebridge

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"
)

const certificatePreflightTimeout = 45 * time.Second

func (b *Bridge) preflightCertificate(parent context.Context, domain string) error {
	ctx, cancel := context.WithTimeout(parent, certificatePreflightTimeout)
	defer cancel()

	b.mu.RLock()
	override := b.certificatePreflight
	server := b.server
	b.mu.RUnlock()

	var err error
	if override != nil {
		err = override(ctx, domain)
	} else {
		if server == nil {
			return errors.New("Tailscale HTTPS certificate preflight started before tsnet was initialized")
		}
		client, clientErr := server.LocalClient()
		if clientErr != nil {
			err = clientErr
		} else {
			certPEM, keyPEM, certErr := client.CertPair(ctx, domain)
			err = certErr
			if err == nil && (len(certPEM) == 0 || len(keyPEM) == 0) {
				err = errors.New("Tailscale returned an empty HTTPS certificate pair")
			}
		}
	}
	if err != nil {
		return errors.New(friendlyCertificateError(err))
	}
	return nil
}

func friendlyCertificateError(err error) string {
	if err == nil {
		return ""
	}
	message := err.Error()
	lower := strings.ToLower(message)
	switch {
	case errors.Is(err, context.DeadlineExceeded) || strings.Contains(lower, "deadline exceeded") || strings.Contains(lower, "timeout"):
		return "Tailscale HTTPS certificate request timed out. Keep the phone online, then stop and start LocalAnt again. Details: " + message
	case strings.Contains(lower, "too many certificates") || strings.Contains(lower, "rate limit") || strings.Contains(lower, "ratelimit"):
		return "Tailscale HTTPS certificate rate limit was reached. Keep LocalAnt stopped and retry later after the certificate limit resets. Details: " + message
	case strings.Contains(lower, "https must be enabled") || strings.Contains(lower, "https is disabled") || strings.Contains(lower, "enable https"):
		return "Tailscale HTTPS certificates are not enabled for this tailnet. Enable MagicDNS and HTTPS certificates in the Tailscale DNS settings, then restart LocalAnt. Details: " + message
	case strings.Contains(lower, "not allowed") || strings.Contains(lower, "permission") || strings.Contains(lower, "forbidden") || strings.Contains(lower, "unauthorized"):
		return "Tailscale did not grant permission to obtain the HTTPS certificate. Confirm the Funnel node attribute and tailnet permissions, then restart LocalAnt. Details: " + message
	default:
		return fmt.Sprintf("Could not obtain the Tailscale HTTPS certificate for the MCP endpoint. Details: %s", message)
	}
}
