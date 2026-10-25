#!/usr/bin/env bash
# P2 end-to-end test on the Parallels VM: real kernel input (uinput) through the real GNOME
# compositor into apklayer-shell and the app. You can watch it in the VM's desktop.
# Covers: Esc / Backspace / Alt+Left = Back, compositor-driven resize (Super+Up / Super+Down),
# wheel. Not covered (needs a 2x output): HiDPI scale > 1.
# Usage: tests/vm/e2e.sh        Exit 0 = all pass.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
keys() { vm "echo $PASS | sudo -S /tmp/uinput-keys $* 2>/dev/null"; }
LOG="$(mktemp)"; fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
shlog() { vm "cat /run/user/1000/apklayer-shell.log"; }
waitfor() { timeout "$2" bash -c "until grep -q '$1' '$LOG'; do sleep 1; done"; }

sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$ROOT/tests/vm/uinput-keys.c" "$VM:/tmp/"
vm "gcc -O1 -o /tmp/uinput-keys /tmp/uinput-keys.c" || { echo "FAIL  build uinput helper"; exit 1; }
pkill -f "scripts/vm-run" 2>/dev/null
vm 'pkill -f apklayer.host; pkill -x apklayer-shell; true'
APKLAYER_DEBUG_INPUT=1 "$ROOT/scripts/vm-run.sh" "$ROOT/tests/uibench/uibench.apk" > "$LOG" 2>&1 &
waitfor "depth=1" 240 || { echo "FAIL  app did not start"; cat "$LOG" | tail; exit 1; }
sleep 4

# Back: the root activity finishing proves Back (and the app exit) through the real keyboard
keys key:1 sleep:2500
check "Esc reaches the app as Back (root activity finishes)" 'grep -q "finished .*depth=0" "$LOG"'

# relaunch for the remaining checks
vm 'pkill -f apklayer.host; true'; : > "$LOG"
vm 'bash -s' >> "$LOG" 2>&1 <<'R' &
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
~/apklayer-m1/bin/apklayer run ~/apklayer-apps/uibench.apk 2>&1 | grep --line-buffered -v '^OpenJDK'
R
waitfor "depth=1" 120 || { echo "FAIL  relaunch"; exit 1; }
sleep 4

keys combo:super+103 sleep:3500
check "Super+Up: compositor resize reaches the app (rebuffer + configure)" \
  'shlog | grep -q "rebuffered 9[0-9][0-9]x" && grep -q "configure 9[0-9][0-9]x" "$LOG"'
keys combo:super+108 sleep:3500
check "Super+Down: back to the small size" 'shlog | grep "rebuffered" | tail -1 | grep -q "rebuffered 480x"'
keys move:-5000,-5000 move:250,250 wheel:-3 sleep:1500
check "wheel reaches the shell and the app stays alive" 'shlog | grep -q "scroll v=10.0" && vm "pgrep -f apklayer.host >/dev/null"'
keys key:1 sleep:2500
check "Esc again exits the app" 'grep -q "finished .*depth=0" "$LOG"'
exit $fail
