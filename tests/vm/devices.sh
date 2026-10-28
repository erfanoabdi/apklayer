#!/bin/bash
# The host's devices as an app sees them (battery, network, USB, location) compared with what the host reports.
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
tests/apps/build.sh hello-devices > /dev/null
sshpass -p "$PASS" scp -q build/hello-devices.apk "$VM:hello-devices.apk"
sshpass -p "$PASS" ssh "$VM" 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
b/cli/apklayer stop >/dev/null 2>&1
b/cli/apklayer install ~/hello-devices.apk | grep -a installed
APKLAYER_JOPTS="-Dapklayer.runms=20000" b/cli/apklayer run hello.devices > /tmp/devices.out 2>&1 || true
grep -a "\[dev\]" /tmp/devices.out
b/cli/apklayer stop >/dev/null 2>&1
echo "host: default route $(awk "\$2==\"00000000\" {print \$1}" /proc/net/route | head -1), usb $(ls /sys/bus/usb/devices 2>/dev/null | wc -l) sysfs entries, power supplies: $(ls /sys/class/power_supply 2>/dev/null | tr "\n" " ")"
grep -aq "\[dev\] PASS" /tmp/devices.out'
