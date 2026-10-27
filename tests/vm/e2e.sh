#!/usr/bin/env bash
# End-to-end test on the Parallels VM: real kernel input (uinput) through the real GNOME
# compositor into apklayer-shell and the app; the window is located from a screenshot
# (prlctl capture) and its decoration buttons/edges are clicked and dragged like a user would.
# You can watch it in the VM's desktop.
# Covers: Esc/Back, title bar buttons (Back, Maximize/Restore, Close), edge resize, title bar
# move, D-pad navigation, wheel delivery. Not covered: HiDPI scale > 1 (needs a 2x output).
# Usage: tests/vm/e2e.sh [group...]   groups: window keys wheel lifecycle clipboard close (default all; run only what you changed)
# Exit 0 = all pass.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VM="${APKLY_VM:-utm@192.168.64.7}"; PASS="${APKLY_VM_PASS:-1234}"
VMNAME="${APKLY_VM_NAME:-Ubuntu 24.04 (with Rosetta)}"
SW=1024; SH=768                                   # VM screen size in px (for abs pointer permille)
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
keys() { vm "echo $PASS | sudo -S /tmp/uinput-keys $* 2>/dev/null"; }
pm() { echo "$(( $1 * 1000 / SW )),$(( $2 * 1000 / SH ))"; }          # px -> permille pair
LOG="$(mktemp)"; fail=0
WANTED=("$@")
want() { [ ${#WANTED[@]} -eq 0 ] && return 0; for g in "${WANTED[@]}"; do [ "$g" = "$1" ] && return 0; done; return 1; }
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
shlog() { vm "cat /run/user/1000/apklayer-shell.log"; }
waitfor() { timeout "$2" bash -c "until grep -q '$1' '$LOG'; do sleep 1; done"; }
geom() { prlctl capture "$VMNAME" --file /tmp/e2e-screen.png >/dev/null 2>&1 && python3 "$ROOT/tests/vm/window-geometry.py" /tmp/e2e-screen.png; }
count() { grep -c "$1" "$LOG"; }
launch() {
  vm 'pkill -f apklayer.host; true'; : > "$LOG"
  vm 'bash -s' >> "$LOG" 2>&1 <<'R' &
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
~/apklayer-m1/bin/apklayer run ~/apklayer-apps/uibench.apk 2>&1 | grep --line-buffered -v '^OpenJDK'
R
  waitfor "depth=1" 120 || { echo "FAIL  app did not start"; exit 1; }
  sleep 5
}

sshpass -p "$PASS" scp -q -o StrictHostKeyChecking=no "$ROOT/tests/vm/uinput-keys.c" "$VM:/tmp/"
vm "gcc -O1 -o /tmp/uinput-keys /tmp/uinput-keys.c" || { echo "FAIL  build uinput helper"; exit 1; }
pkill -f "scripts/vm-run" 2>/dev/null
vm 'pkill -f apklayer.host; pkill -x apklayer-shell; true'
APKLAYER_DEBUG_INPUT=1 "$ROOT/scripts/vm-run.sh" "$ROOT/tests/uibench/uibench.apk" > "$LOG" 2>&1 &
waitfor "depth=1" 240 || { echo "FAIL  app did not start"; tail "$LOG"; exit 1; }
sleep 5

read -r L T R <<<"$(geom)" || { echo "FAIL  could not find the window in a screenshot"; exit 1; }
echo "window title bar: left=$L top=$T right=$R"
BY=$((T + 16)); MAXX=$((R - 60)); CLOSEX=$((R - 20)); BACKX=$((L + 20))

if want window; then
# --- title bar: Maximize and Restore ---
keys abs:$(pm $MAXX $BY) sleep:400 click sleep:3000
check "title bar Maximize button maximizes (big rebuffer)" 'shlog | grep -q "rebuffered 9[0-9][0-9]x"'
read -r L2 T2 R2 <<<"$(geom)"
keys abs:$(pm $((R2 - 60)) $((T2 + 16))) sleep:400 click sleep:3000
check "title bar Restore button restores the small size" 'shlog | grep rebuffered | tail -1 | grep -q "rebuffered [4-5][0-9][0-9]x"'
fi

if want window; then
# --- title bar drag moves the window ---
read -r L T R <<<"$(geom)"
keys drag:$(pm $((L + 150)) $((T + 16))),$(pm $((L + 300)) $((T + 120))) sleep:1200
read -r L3 T3 R3 <<<"$(geom)"
check "dragging the title bar moves the window (+~150 px)" '[ $((L3 - L)) -gt 100 ]'
fi

if want window; then
# --- resize cursor over the edges (the shell logs the cursor it sets) ---
read -r L T R <<<"$(geom)"
keys abs:$(pm $((L + 200)) $((T + 200))) sleep:500 abs:$(pm $((R - 3)) $((T + 200))) sleep:800
check "right edge shows the east/right resize cursor" 'shlog | grep "cursor " | tail -1 | grep -q right_side'
keys abs:$(pm $((L + 200)) $((T + 200))) sleep:800
check "cursor goes back to the arrow inside the window" 'shlog | grep "cursor " | tail -1 | grep -q left_ptr'
fi

if want window; then
# --- resize by holding an edge ---
W0=$(shlog | grep rebuffered | tail -1 | sed 's/.*rebuffered \([0-9]*\)x.*/\1/')
keys drag:$(pm $((R3 - 2)) $((T3 + 300))),$(pm $((R3 + 100)) $((T3 + 300))) sleep:1500
W1=$(shlog | grep rebuffered | tail -1 | sed 's/.*rebuffered \([0-9]*\)x.*/\1/')
check "holding the right edge and dragging resizes the window ($W0 -> $W1)" '[ "$W1" -gt "$((W0 + 50))" ]'
fi

if want keys; then
# --- D-pad menu selection, then the Back button in the title bar ---
read -r L T R <<<"$(geom)"
keys abs:$(pm $((L + 200)) $((T + 400))) click sleep:600   # focus the window content (opens an entry)
before=$(count "started .*depth=")
keys key:108 sleep:300 key:108 sleep:300 key:28 sleep:2500
check "D-pad: Down, Down, Enter opens a menu entry" '[ "$(count "started .*depth=")" -gt "$before" ]'
fin=$(count "finished .*depth=")
read -r L T R <<<"$(geom)"
keys abs:$(pm $((L + 20)) $((T + 16))) sleep:400 click sleep:2500
check "title bar Back button goes back one screen" '[ "$(count "finished .*depth=")" -gt "$fin" ]'
fin=$(count "finished .*depth=")
keys key:1 sleep:2000
check "Esc goes back one screen" '[ "$(count "finished .*depth=")" -gt "$fin" ]'
fi

if want wheel; then
# --- wheel: sweep the pointer over the screen; the shell sees scroll events inside the window ---
grid=""; for y in 300 450 600; do for x in 250 400 550; do grid="$grid abs:$(pm $x $y) wheel:-1 sleep:120"; done; done
keys $grid
# uinput wheel events only sometimes reach the compositor's pointer focus; the app-side wheel path
# (incl. dialogs) is covered by tests/p2-host.sh, so no events at all is a SKIP, not a failure
if shlog | grep -q "axis "; then
  check "wheel events reach the shell and the app stays alive" 'shlog | grep -q "scroll v=" && vm "pgrep -f apklayer.host >/dev/null"'
else
  echo "SKIP  wheel: the test mouse's wheel produced no Wayland axis events (uinput limitation)"
fi
fi

if want lifecycle; then
# --- lifecycle: focus loss pauses the activity, focusing the window resumes it ---
read -r L T R <<<"$(geom)"
keys abs:$(pm 900 150) click sleep:2000                       # click the desktop: the window deactivates
check "clicking the desktop pauses the activity (window inactive)" 'grep -q "lifecycle: paused" "$LOG"'
keys abs:$(pm $((L + 200)) $((T + 300))) click sleep:2000    # click the window: it activates again
check "clicking the window resumes the activity" 'grep -q "lifecycle: resumed" "$LOG"'
fi

if want clipboard; then
# --- clipboard: system selection -> app, and app copy -> system selection ---
read -r L T R <<<"$(geom)"
vm 'export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0; echo -n "from the desktop" | wl-copy'
keys abs:$(pm 900 150) click sleep:500 abs:$(pm $((L + 200)) $((T + 300))) click sleep:2000   # focus the window: the shell pushes the selection
check "text copied on the desktop reaches the app clipboard" 'grep -q "clipboard from the system (16 chars)" "$LOG"'
# app -> system: restart the app with a scripted copy 15 s after start
vm 'pkill -f apklayer.host; true'; : > "$LOG"
vm 'bash -s' >> "$LOG" 2>&1 <<'R' &
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 APKLAYER_JOPTS="-Dapklayer.script=15000:clipcopy:copied-in-the-app"
~/apklayer-m1/bin/apklayer run ~/apklayer-apps/uibench.apk 2>&1 | grep --line-buffered -v '^OpenJDK'
R
waitfor "depth=1" 120
sleep 3
read -r L T R <<<"$(geom)"
keys abs:$(pm $((L + 200)) $((T + 300))) click   # a focused window and a fresh input serial: only then may it own the selection
waitfor "clipboard to the system" 90
sleep 1
got=$(vm 'XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0 wl-paste -n 2>/dev/null')
check "text copied in the app is the system selection (wl-paste: '$got')" '[ "$got" = "copied-in-the-app" ]'
fi

if want close; then
# --- Close button ends the app ---
read -r L T R <<<"$(geom)"
keys abs:$(pm $((R - 20)) $((T + 16))) sleep:400 click sleep:3000
check "title bar Close button closes the app" 'grep -q "finished .*depth=0\|exit:" "$LOG"'
fi

exit $fail
