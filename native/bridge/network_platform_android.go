//go:build android

package nativebridge

import "tailscale.com/net/netmon"

func updatePlatformNetworkState(defaultInterface, gateway string) {
	netmon.UpdateLastKnownDefaultRouteInterface(defaultInterface)
	netmon.UpdateLastKnownDefaultGateway(gateway)
}
