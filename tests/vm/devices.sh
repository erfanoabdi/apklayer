#!/bin/bash
# The host's devices as an app sees them (battery, network, USB, location) compared with what the host reports.
# On the VM, in the group's instance (tests/run devices): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/devices.out
bin/apklayer stop >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=20000" bin/apklayer run hello.devices > $out 2>&1 || true
grep -a "\[dev\]" $out
bin/apklayer stop >/dev/null 2>&1
echo "host: default route $(awk '$2=="00000000" {print $1}' /proc/net/route | head -1), usb $(ls /sys/bus/usb/devices 2>/dev/null | wc -l) sysfs entries, power supplies: $(ls /sys/class/power_supply 2>/dev/null | tr "\n" " ")"
grep -aq "\[dev\] PASS" $out
