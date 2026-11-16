#!/bin/bash
# ut-unlock.sh [on|off] [DIR]: on the Ubuntu Touch phone (Volla X23, 720x1560), `on` wakes the locked screen and types the PIN (stdin's first line; the
# phone's sudo password is the PIN, tests/vm/local.conf) on Lomiri's greeter, `off` presses the power key when the screen is on (the phone locks).
# Apps are stopped (SIGSTOP) while the screen is off or locked, so a check that runs an app needs `on` first. DIR holds ut-uikeys.py and ut-uitap.py
# (uinput keys and taps, `phablet` is in plugdev; default: this script's directory). Prints "screen on" / "unlocked" / "screen off".
mode=${1:-on} D=${2:-$(dirname "$0")}
on() { [ "$(cat /sys/class/leds/lcd-backlight/brightness)" -gt 0 ]; }
if [ "$mode" = off ]; then
    on && python3 "$D/ut-uikeys.py" POWER
    on && { echo "screen still on"; exit 1; }
    echo "screen off"; exit 0
fi
if on; then echo "screen on"; exit 0; fi
read -r pin
python3 "$D/ut-uikeys.py" POWER
sleep 1.5
python3 "$D/ut-uitap.py" 700,800\>60,800  # the lock screen shows its clock first: a swipe to the side brings the PIN pad (on the pad it does nothing)
sleep 1
python3 "$D/ut-uitap.py" $(printf '%s' "$pin" | python3 -c 'import sys; k={"1":(96,1088),"2":(271,1088),"3":(447,1088),"4":(96,1219),"5":(271,1219),"6":(447,1219),"7":(96,1346),"8":(271,1346),"9":(447,1346),"0":(271,1472)}; print(*[v for d in sys.stdin.read().strip() for v in k[d]])')
sleep 1.5
on && { echo unlocked; exit 0; }
echo "the screen stayed off"; exit 1
