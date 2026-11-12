#!/bin/bash
# Network test for PGR's ACE shutdown: does it still come at ~90 s when the game loses its network after DELAY seconds?
# Waits for PGR's systemd scope (apklayer-<instance>-app-<package>-<pid>.scope), lets it run DELAY s with network, then drops all
# outgoing non-loopback IP traffic of that cgroup only (nftables "socket cgroupv2"); Unix sockets (binder, audio, display) are not IP.
# The rule is removed when the scope ends or on Ctrl+C. Run: sudo bash tools/ace/pgr-netcut.sh [DELAY]   then start the app,
# or: sudo env LAUNCH=1 bash tools/ace/pgr-netcut.sh [DELAY]   (starts it itself). Another app: PKG=<package>. Findings: docs/PLAN.md.
set -u
DELAY=${1:-45}
PKG=${PKG:-com.kurogame.gplay.punishing.grayraven.en}
USER_ID=$(id -u "${SUDO_USER:-$USER}")
SLICE=user.slice/user-$USER_ID.slice/user@$USER_ID.service/app.slice
ROOT=/sys/fs/cgroup/$SLICE
TABLE=pgr_netcut
cleanup() { nft delete table inet $TABLE 2>/dev/null && echo "$(date +%T) rule removed"; }
trap 'cleanup; exit' INT TERM
cleanup >/dev/null

# LAUNCH=1: start the app here as the invoking user, in the desktop session's environment (its systemd user manager's: display, D-Bus);
# its output goes to /tmp/pgr-netcut-run.out
if [ "${LAUNCH:-0}" = 1 ] && [ -n "${SUDO_USER:-}" ]; then
  echo "$(date +%T) starting $PKG as $SUDO_USER"
  env_file=$(mktemp)
  sudo -u "$SUDO_USER" XDG_RUNTIME_DIR=/run/user/$USER_ID systemctl --user show-environment | sed 's/^/export /' > "$env_file"
  chmod 644 "$env_file"
  sudo -u "$SUDO_USER" bash -c "set -a; source '$env_file'; set +a; rm -f '$env_file'; exec apklayer run $PKG" > /tmp/pgr-netcut-run.out 2>&1 &
fi
echo "$(date +%T) waiting for PGR's scope (start PGR now)"
scope=""
while [ -z "$scope" ]; do
  scope=$(ls -d $ROOT/apklayer-*-app-$PKG-*.scope 2>/dev/null | head -1)
  sleep 0.5
done
name=$(basename "$scope")
start=$(date +%s)
echo "$(date +%T) PGR started: $name; cutting its network in $DELAY s"
sleep "$DELAY"
if [ ! -d "$scope" ]; then echo "$(date +%T) PGR ended before the cut ($(( $(date +%s) - start )) s)"; exit 0; fi

nft add table inet $TABLE
nft add chain inet $TABLE out '{ type filter hook output priority 0; policy accept; }'
nft add rule inet $TABLE out oifname lo accept
# nft needs the cgroup path as a quoted string of its own (it contains '@'): the inner quotes reach nft
if ! nft add rule inet $TABLE out socket cgroupv2 level 5 "\"$SLICE/$name\"" counter drop; then
  echo "$(date +%T) could not add the rule: network NOT cut"; cleanup; exit 1
fi
echo "$(date +%T) network cut ($(( $(date +%s) - start )) s after start)"

while [ -d "$scope" ]; do sleep 1; done
echo "$(date +%T) PGR ended $(( $(date +%s) - start )) s after start; packets dropped:"
nft list chain inet $TABLE out | grep counter
cleanup
