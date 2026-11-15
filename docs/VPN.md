# VPN for apps (VpnService), host-wide

Apps that are VPN clients (WireGuard's GoBackend, OpenVPN for Android) call `VpnService.Builder.establish()` and read and write IP packets on the
descriptor it returns. apklayer gives them a **real TUN device on the host**: the host's traffic for the app's routes (all of it for a default route)
goes through the app. Like Android's key icon, the manager shows the VPN and can disconnect it. One VPN at a time per instance.

## Architecture

```
app (VpnService) --establish()--> hub (HubVpn, vpn_management service, framework side: VpnManager, ConnectivityManager)
                                    | 1. consent dialog once per app (HubVpnUi, HostVpnIntents: VpnService.prepare())
                                    | 2. HubVpnNet: the persistent TUN tunapk<hash><a|b> owned by the user, addresses, routes, DNS
                                    |      NetworkManager (nmcli = its D-Bus API), else pkexec apklayer-vpn-helper
                                    | 3. HubVpnGuard: `apklayer vpn-guard` (cli/vpn_guard.cpp) opens the TUN (TUNSETIFF needs no capability on a
                                    |      device owned by the user), hands the descriptor to the hub, which gives it to the app
                                    | 4. notification "VPN is active" with Disconnect, state file <logs>/vpn/active
app: protect(socket) --hub VPN op "protect" (HubProtocol 68)--> guard: setsockopt(SO_BINDTODEVICE, the host's own interface)
```

- **Framework side**: `vpn_management` (`IVpnManager`, `HubVpn`); `VpnService.prepare()`'s Intent (the `com.android.vpndialogs` ConfirmDialog, absent here)
  is answered by the hub's own dialog (`HostVpnIntents`); the consent is remembered per app (`<logs>/vpn/consent`). `onRevoke` is delivered by binding
  the owner's `VpnService` and sending the `LAST_CALL_TRANSACTION`. `ConnectivityManager`: the host's default route is read from `/proc/net/route`; when it
  is a VPN interface (`tun*`, `wg*`, ...) the default network is `TRANSPORT_VPN` without `NOT_VPN`; a VPN without a default route is listed as another network.
- **Default route**: `0.0.0.0/0` (and `::/0`) is installed as its two halves (`0.0.0.0/1`, `128.0.0.0/1`), the usual trick: the host's own default route stays
  and the more specific halves win, so removing the TUN restores everything, and the machine's LAN routes (more specific than /1) stay reachable. (Android
  would send LAN traffic through the VPN too; here it does not. Deliberate: safer.)
- **protect()**: the guard binds the socket to the interface the host's own traffic left by when the VPN was established (the lowest-metric default route that
  is not a VPN): `SO_BINDTODEVICE` is allowed without root since Linux 5.7. Only the VPN's owner may protect. `SO_MARK` needs `CAP_NET_ADMIN` and is not used.
  A bound socket's route lookup is restricted to that interface (so loopback and LAN addresses of other interfaces are not reachable from a protected socket;
  WireGuard/OpenVPN protect only their tunnel sockets to the server).
- **Lifetime**: the VPN ends with the owner's descriptor. The guard watches the TUN's carrier over netlink (`RTMGRP_LINK`): when the last descriptor closes
  (the app closed it, was killed or crashed) it runs the teardown (`nmcli connection delete`) and tells the hub, which ends the VPN. If the hub dies, the
  guard's stdin closes and it tears down too; `apklayer stop` and the next hub's start run `apklayer vpn sweep` (deletes the instance's `apklayer-vpn-<id>-*`
  connections); a polkit helper removes its device when its pipe closes. DNS goes with the connection. No kill switch (see below).
- **CLI and manager**: `apklayer vpn status` (`VPN<TAB>owner<TAB>session<TAB>since<TAB>interface<TAB>all traffic|some routes`, or `no VPN`),
  `apklayer vpn disconnect` (revokes the owner: onRevoke, then the TUN goes), `apklayer vpn sweep`. The manager's Instances page shows the active VPN of
  the selected instance with a Disconnect button.

## Privilege paths

Changing host routes needs privilege; two ways, tried in order, neither needs the user to be root:

1. **NetworkManager** (`nmcli connection add save no type tun ... owner <uid>`, then `connection up`). NM's polkit policy lets an *active local* user add
   and activate connections without a password (`org.freedesktop.NetworkManager.settings.modify.system`, `network-control`: `yes` for the active
   session). The connection is in memory only (`save no`), `autoconnect no`; the device is persistent and owned by the user. Addresses, routes (metric 50),
   DNS (and exclusive DNS, `dns-priority -1`, when the VPN takes everything) come from the Builder. The MTU of the TUN stays 1500: NM has no tun MTU
   property (the route-level `ipv4.mtu` is not used). Needs the hub (and its `nmcli` children) to run where polkit sees the user's local active
   session: under the user manager (`systemd-run --user`, a user unit) or inside the desktop session. A plain ssh login is a remote session: polkit says
   `auth` there and the VPN falls through to 2. (On the test VM the hub started by `apklayer run` from an ssh login got `yes`: apklayer starts the stack through
   its daemon/user scopes; which one decides is not pinned down here.)
2. **pkexec helper** `apklayer-vpn-helper` (cli/vpn_helper.cpp, polkit action `org.apklayer.vpn`, `data/org.apklayer.vpn.policy.in`): the user's password
   at the click of the consent dialog's flow (polkit's agent). A small root program that only makes a TUN owned by `PKEXEC_UID`, sets MTU, addresses,
   routes (metric 50) and the DNS (`resolvectl`), says `ready`, and removes the device when its stdin closes, on SIGTERM/SIGHUP or, with `--watchdog N`,
   after N seconds without a `confirm` line. Arguments are validated strictly (`tunapk*` names, CIDR and IP syntax, counts), run through `ip` by absolute path
   with an empty environment. The deb installs both files (`meson install`: `<prefix>/lib/apklayer/cli/apklayer-vpn-helper` and
   `/usr/share/polkit-1/actions/org.apklayer.vpn.policy`; `Recommends: pkexec` is there already).

The consent dialog (`HubVpnUi.ask`) says plainly that the traffic of the whole computer goes through the app and that, if NetworkManager does not allow
the connection, the computer's password is asked next (a static sentence: the dialog cannot know which path will win). The test checks the dialog's title only.

**Sailfish OS** (connman, no polkit prompt): not implemented. It would need a connman VPN plugin or a root helper started by a systemd unit (the `ip`
commands of the helper above, for example through a socket-activated service that checks the caller's uid) because connman has no D-Bus call that makes
an unattended TUN with routes for another process. **Ubuntu Touch** has NetworkManager: the first path applies unchanged where polkit permits it.

## Safety

- The host's own default route is never replaced or deleted; the VPN adds halves with a lower metric and removing them restores the machine.
- Cleanup happens on: `apklayer vpn disconnect`, the notification's button, app death or crash, hub crash and stack stop (see Lifetime).
- Tests never put the default route through the TUN without the ssh peer's `/32` route and a 60 s systemd-timer watchdog (`tests/vm/vpn.sh`).
- **No kill switch**: if the app dies, the VPN goes and traffic uses the host's own route again (Android's "block connections without VPN" is not
  implemented; `setAlwaysOnVpnPackage` stores the setting only). IPv6 is not leaked-protected: a v4-only VPN leaves IPv6 on the host's route.
- Not implemented, because a host-wide VPN cannot do them: `addAllowedApplication`/`addDisallowedApplication` (logged and ignored), `allowBypass`, per-app
  VPNs. Platform VPN profiles (`VpnManager.provisionVpnProfile`, IKEv2) and legacy VPNs throw or return false.

## Tests

`tests/run vpn` (group `vpn`, alone: it changes the host's network) runs `tests/vm/vpn.sh` with `tests/apps/hello-vpn` (an echo tunnel: ICMP echo and UDP):
unit (real device owned by the user, address, route, ping and UDP from the host answered by the app, resolver, status, disconnect/onRevoke, device and
connection gone), crash (the app killed), default route (guarded: ssh peer `/32`, 60 s watchdog; the active network is the VPN, an unprotected datagram goes into
the TUN and a `protect()`ed one does not, `ip route` identical afterwards). The helper is tested directly (see CHANGELOG).

## Limits

- One VPN per instance; two instances with VPNs at once would both add `0.0.0.0/1` and `128.0.0.0/1` at metric 50 and fight over them.
- `Builder.setMtu` is ignored on the NetworkManager path (the TUN stays at 1500); the helper path sets it.
- The notification's Disconnect action (a desktop notification button) and the per-app sandbox (`APKLAYER_SANDBOX`: the guard's socket and the TUN go through
  the hub, nothing in the app's view is needed, but it was not run) are untested. The manager phase of the test is skipped unless `manager/b` is built
  (`meson setup manager/b manager -Dslint=...`; `tests/run` does not).

## Untested / needs a person

- The hub's pkexec path (`HubVpnNet.viaHelper`: only the helper binary was run, as root through `sudo`, never from the hub through pkexec).
- Establishing through the real OpenVPN for Android and WireGuard (local corpus APKs): both install and start (WireGuard's libwg-go loads), the UI was not
  driven to a connection (WireGuard needs a tunnel created in its UI; OpenVPN a profile and a reachable server).
- The pkexec dialog on a real desktop (the helper's logic is tested as root through `sudo -n` with `PKEXEC_UID`).
- A real VPN provider end to end; Sailfish; KDE/Lomiri polkit agents; the default-route case against a working VPN resolver (the test checks that
  resolved lists the app's DNS as the default route, not that names resolve through it).
- NetworkManager versions before 1.40 (the `tun` connection type with `owner` is older; `save no` and `dns-priority` were checked on 1.54 only).
