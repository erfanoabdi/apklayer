// The host-wide VPN (docs/VPN.md): the native guard the hub starts (vpn_guard.cpp), `apklayer vpn status|disconnect`, and the cleanup of what a hub left.
#pragma once

#include "layout.h"

#include <string>
#include <vector>

namespace apklayer {

// `apklayer vpn-guard ...`: the hub's helper that opens the VPN's TUN, serves protect() and removes the VPN when its end is seen (vpn_guard.cpp)
int vpnGuard(const std::vector<std::string>& args);
// `apklayer vpn status|disconnect`
int vpnCommand(const Layout& layout, const Instance& instance, const std::vector<std::string>& args);
// Removes the instance's VPN left by a hub or guard that died (`nmcli connection delete` of its profiles; the polkit helper is its own watchdog):
// `apklayer stop` and the hub's start call it. The connection names start with "apklayer-vpn-<instance>-".
void vpnSweep(const Instance& instance);

}  // namespace apklayer
