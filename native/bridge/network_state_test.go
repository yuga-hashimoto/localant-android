package nativebridge

import (
	"net"
	"testing"
)

func TestParseAndroidInterfacesJSON(t *testing.T) {
	interfaces, err := parseAndroidInterfacesJSON(`[
	  {
	    "name":"wlan0",
	    "index":7,
	    "mtu":1500,
	    "up":true,
	    "broadcast":true,
	    "loopback":false,
	    "pointToPoint":false,
	    "multicast":true,
	    "addrs":[
	      {"ip":"192.0.2.20","prefixLen":24},
	      {"ip":"fe80::1234%wlan0","prefixLen":64}
	    ]
	  }
	]`)
	if err != nil {
		t.Fatal(err)
	}
	if len(interfaces) != 1 {
		t.Fatalf("interfaces=%d", len(interfaces))
	}
	iface := interfaces[0]
	if iface.Name != "wlan0" || iface.Index != 7 || iface.MTU != 1500 {
		t.Fatalf("interface=%+v", iface.Interface)
	}
	if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagMulticast == 0 {
		t.Fatalf("flags=%v", iface.Flags)
	}
	if len(iface.AltAddrs) != 2 {
		t.Fatalf("addresses=%v", iface.AltAddrs)
	}
}

func TestParseAndroidInterfacesRejectsEmptyOrUnusablePayload(t *testing.T) {
	for _, payload := range []string{"", "[]", `[{"name":"","addrs":[]}]`} {
		if _, err := parseAndroidInterfacesJSON(payload); err == nil {
			t.Fatalf("payload %q should fail", payload)
		}
	}
}

func TestUpdateNetworkStateRegistersAlternateInterfaceGetter(t *testing.T) {
	bridge := NewBridge(&fakeHost{})
	err := bridge.UpdateNetworkState(`[{"name":"eth0","index":2,"mtu":1500,"up":true,"addrs":[{"ip":"198.51.100.2","prefixLen":24}]}]`, "eth0", "198.51.100.1")
	if err != nil {
		t.Fatal(err)
	}
	if !androidNetworkConfigured() {
		t.Fatal("network state should be configured")
	}
}
