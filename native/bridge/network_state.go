package nativebridge

import (
	"encoding/json"
	"errors"
	"net"
	"net/netip"
	"slices"
	"strings"
	"sync"

	"tailscale.com/net/netmon"
)

var androidNetworkState struct {
	sync.RWMutex
	interfaces []netmon.Interface
	configured bool
}

var registerAndroidInterfaceGetter sync.Once

type androidAddressJSON struct {
	IP        string `json:"ip"`
	PrefixLen int    `json:"prefixLen"`
}

type androidInterfaceJSON struct {
	Name         string               `json:"name"`
	Index        int                  `json:"index"`
	MTU          int                  `json:"mtu"`
	Up           bool                 `json:"up"`
	Broadcast    bool                 `json:"broadcast"`
	Loopback     bool                 `json:"loopback"`
	PointToPoint bool                 `json:"pointToPoint"`
	Multicast    bool                 `json:"multicast"`
	Addresses    []androidAddressJSON `json:"addrs"`
}

// UpdateNetworkState supplies Android's Java-level network interface view to
// Tailscale. Android SDK 30+ blocks the netlink operation used by Go's
// net.Interfaces, so tsnet must use this alternate getter before it starts.
func (b *Bridge) UpdateNetworkState(interfacesJSON, defaultInterface, gateway string) error {
	interfaces, err := parseAndroidInterfacesJSON(interfacesJSON)
	if err != nil {
		return err
	}
	androidNetworkState.Lock()
	androidNetworkState.interfaces = cloneInterfaces(interfaces)
	androidNetworkState.configured = true
	androidNetworkState.Unlock()

	registerAndroidInterfaceGetter.Do(func() {
		netmon.RegisterInterfaceGetter(func() ([]netmon.Interface, error) {
			androidNetworkState.RLock()
			defer androidNetworkState.RUnlock()
			return cloneInterfaces(androidNetworkState.interfaces), nil
		})
	})
	updatePlatformNetworkState(defaultInterface, gateway)
	b.injectNetworkChange()
	return nil
}

func (b *Bridge) injectNetworkChange() {
	b.mu.RLock()
	hook := b.networkChangeHook
	server := b.server
	status := b.status
	b.mu.RUnlock()

	if status != statusRunning {
		return
	}
	if hook != nil {
		hook()
		return
	}
	if server == nil {
		return
	}
	monitor, ok := server.Sys().NetMon.GetOK()
	if ok && monitor != nil {
		monitor.InjectEvent()
	}
}

func androidNetworkConfigured() bool {
	androidNetworkState.RLock()
	defer androidNetworkState.RUnlock()
	return androidNetworkState.configured
}

func parseAndroidInterfacesJSON(raw string) ([]netmon.Interface, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, errors.New("Android network interface JSON is required")
	}
	var input []androidInterfaceJSON
	if err := json.Unmarshal([]byte(raw), &input); err != nil {
		return nil, err
	}
	output := make([]netmon.Interface, 0, len(input))
	for _, item := range input {
		if strings.TrimSpace(item.Name) == "" {
			continue
		}
		iface := netmon.Interface{
			Interface: &net.Interface{
				Index: item.Index,
				MTU:   item.MTU,
				Name:  item.Name,
			},
			AltAddrs: []net.Addr{},
		}
		if item.Up {
			iface.Flags |= net.FlagUp
		}
		if item.Broadcast {
			iface.Flags |= net.FlagBroadcast
		}
		if item.Loopback {
			iface.Flags |= net.FlagLoopback
		}
		if item.PointToPoint {
			iface.Flags |= net.FlagPointToPoint
		}
		if item.Multicast {
			iface.Flags |= net.FlagMulticast
		}
		for _, address := range item.Addresses {
			parsed, err := androidAddressToNetAddr(address)
			if err != nil {
				continue
			}
			iface.AltAddrs = append(iface.AltAddrs, parsed)
		}
		output = append(output, iface)
	}
	if len(output) == 0 {
		return nil, errors.New("Android reported no usable network interfaces")
	}
	return output, nil
}

func androidAddressToNetAddr(address androidAddressJSON) (net.Addr, error) {
	parsed, err := netip.ParseAddr(address.IP)
	if err != nil {
		return nil, err
	}
	zone := parsed.Zone()
	ip := net.IP(slices.Clone(parsed.AsSlice()))
	if zone != "" {
		return &net.IPAddr{IP: ip, Zone: zone}, nil
	}
	bits := 128
	if parsed.Is4() {
		bits = 32
	}
	if address.PrefixLen < 0 || address.PrefixLen > bits {
		return &net.IPAddr{IP: ip}, nil
	}
	return &net.IPNet{IP: ip, Mask: net.CIDRMask(address.PrefixLen, bits)}, nil
}

func cloneInterfaces(input []netmon.Interface) []netmon.Interface {
	output := make([]netmon.Interface, len(input))
	for index, item := range input {
		output[index] = item
		if item.Interface != nil {
			copyInterface := *item.Interface
			copyInterface.HardwareAddr = slices.Clone(item.Interface.HardwareAddr)
			output[index].Interface = &copyInterface
		}
		output[index].AltAddrs = slices.Clone(item.AltAddrs)
	}
	return output
}
