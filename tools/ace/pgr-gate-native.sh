#!/bin/bash
# Native arm64 control for the gate trace (patch 0071): ACE's call gate libanogs +0x1f5200 (entry) / +0x1f5238 (return) as two hardware EXECUTE breakpoints
# (perf mem:<addr>:x, inherited by the game's threads; a uprobe/ftrace breakpoint writes BRK into libanogs's text and the game dies ~16 s after start, a plain run survives),
# registers x0-x3, lr per hit in <W>/gate.perf, the game in pgr-native-ns.sh's namespace. Needs root (sudo -n) and perf.
#   XDG_DATA_HOME=~/pgr-gate-xdg INST=pgrgate W=~/pgr-gate-work RUNMS=100000 tools/ace/pgr-gate-native.sh   (under flock ~/pgr-run.lock); then `perf script -i $W/gate.perf`
#   BASE=0x4000000000 is libanogs's load address in the native game (all runs so far). NOPROBE=1: the same run without breakpoints.
set -eu
PKG=com.kurogame.gplay.punishing.grayraven.en; BASE=${BASE:-0x4000000000}
A=$XDG_DATA_HOME/apklayer/$INST
mkdir -p "$A/app-data/$PKG/app" "$A/app-data/$PKG/device" "$A/storage/emulated/0" "$W"
sudo -n mkdir -p /data /sdcard /storage
cat > "$W/inner.sh" <<IN
mount -t tmpfs tmpfs /data; mount -t tmpfs tmpfs /storage
mkdir -p /data/data/$PKG /data/user/0/$PKG /data/user_de/0/$PKG /storage/emulated/0
mount --bind $A/app-data/$PKG/app /data/data/$PKG; mount --bind $A/app-data/$PKG/app /data/user/0/$PKG; mount --bind $A/app-data/$PKG/device /data/user_de/0/$PKG
mount --bind $A/storage/emulated/0 /storage/emulated/0; mount --bind $A/storage/emulated/0 /sdcard
exec runuser -u $(id -un) -- env HOME=$HOME XDG_RUNTIME_DIR=/run/user/$(id -u) DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$(id -u)/bus XDG_DATA_HOME=$XDG_DATA_HOME APKLAYER_JOPTS=-Dapklayer.runms=${RUNMS:-120000} $(command -v apklayer) --instance $INST run $PKG
IN
if [ -n "${NOPROBE:-}" ]; then P=""; else
  P="perf record -q -o $W/gate.perf -m 128M -T -e mem:$(printf 0x%x $((BASE + 0x1f5200))):x -e mem:$(printf 0x%x $((BASE + 0x1f5238))):x -c 1 --user-regs=x0,x1,x2,x3,lr --"
fi
sudo -n $P unshare -m --propagation private bash "$W/inner.sh" > "$W/run.log" 2>&1 || true
XDG_DATA_HOME=$XDG_DATA_HOME apklayer --instance "$INST" stop >/dev/null 2>&1 || true
ls -la "$W"/gate.perf 2>/dev/null || true
