#!/usr/bin/env bash
# P2 host-level check (Mac or Linux, headless): drives the shell-event paths of AppHost with the
# same int[] events ShellClient produces: key (Back, typing), wheel, configure (resize, HiDPI).
# Exit 0 = all pass. Usage: tests/p2-host.sh [group...]   groups: back type resize hidpi wheel dialog dpad
# (no argument = all; run only the groups for the code you changed)
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/tests/uibench/uibench.apk"
fail=0
WANTED=("$@")
want() { [ ${#WANTED[@]} -eq 0 ] && return 0; for g in "${WANTED[@]}"; do [ "$g" = "$1" ] && return 0; done; return 1; }
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
size() { python3 -c "from PIL import Image; print('%dx%d' % Image.open('$1').size)"; }
differs() { python3 -c "
from PIL import Image, ImageChops
import sys
sys.exit(0 if ImageChops.difference(Image.open('$1').convert('RGB'), Image.open('$2').convert('RGB')).getbbox() else 1)"; }

if want back; then
# 1. Back key (Esc/Alt+Left arrive as Android keycode 4): General opens, key:4 returns.
log=$("$ROOT/scripts/check.sh" "$APK" "2000:tap:100,80+4000:key:4" "1500" 5500 2>&1)
check "key:4 closes the second screen (depth 2 -> 1)" \
  'grep -q "started .*depth=2" <<<"$log" && grep -q "finished .*depth=1" <<<"$log"'
fi

if want back; then
# 2. Backspace is Back when nothing is being edited (code 67)
log=$("$ROOT/scripts/check.sh" "$APK" "2000:tap:100,80+4000:key:67" "1500" 5500 2>&1)
check "Backspace outside a text field acts as Back" 'grep -q "finished .*depth=1" <<<"$log"'
fi

if want type; then
# 3. Typing incl. non-ASCII into an EditText (Text > EditText Typing)
"$ROOT/scripts/check.sh" "$APK" "2000:tap:100,374+3500:tap:100,80+5000:tap:200,150+6000:key:36,104+6500:key:0,228" \
  "5900,7500" 8000 >/dev/null 2>&1
check "typed keys change the screen (weak: benchmark also types by itself)" 'differs "$ROOT/build/check/uibench-5900ms.png" "$ROOT/build/check/uibench-7500ms.png"'
fi

if want resize; then
# 4. Resize: configure event relayouts the app and the frame has the new size
"$ROOT/scripts/check.sh" "$APK" "2000:configure:360,600" "1500,3500" 4000 >/dev/null 2>&1
check "configure 360x600 -> 360x600 frame" '[ "$(size "$ROOT/build/check/uibench-3500ms.png")" = 360x600 ]'
fi

if want hidpi; then
# 5. HiDPI: 2x scale -> frame is 2x pixels of the logical size
"$ROOT/scripts/check.sh" "$APK" "2000:configure:360,600,200" "1500,3500" 4000 >/dev/null 2>&1
check "configure 360x600 @200% -> 720x1200 frame" '[ "$(size "$ROOT/build/check/uibench-3500ms.png")" = 720x1200 ]'
fi

if want wheel; then
# 6. Wheel scrolls a list that overflows its window (Text screen at 150 px high)
"$ROOT/scripts/check.sh" "$APK" "2000:tap:100,374+3000:configure:480,150+4500:wheel:240,100,-8000" \
  "3800,6000" 6500 >/dev/null 2>&1
check "wheel scrolls the list" 'differs "$ROOT/build/check/uibench-3800ms.png" "$ROOT/build/check/uibench-6000ms.png"'
fi

if want dialog; then
# 7. Wheel in a dialog window (General > Dialog List): the dialog is a separate window above the
#    activity window and must receive the wheel (and keys), not the activity underneath.
"$ROOT/scripts/check.sh" "$APK" "2000:tap:100,80+3500:tap:100,129+5500:wheel:240,400,-5000" \
  "5000,7000" 7500 >/dev/null 2>&1
check "wheel scrolls the list inside a dialog" 'differs "$ROOT/build/check/uibench-5000ms.png" "$ROOT/build/check/uibench-7000ms.png"'
fi

if want dpad; then
# 8. D-pad: Down, Down, Enter opens a menu entry (keyboard-only navigation, real key timing)
log=$("$ROOT/scripts/check.sh" "$APK" "2000:key:20+2400:key:20+2800:keydown:66+2860:keyup:66" "1500" 4500 2>&1)
check "D-pad Down, Down, Enter opens the second menu entry" 'grep -q "started .*depth=2" <<<"$log"'
fi

exit $fail
