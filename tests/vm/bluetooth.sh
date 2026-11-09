#!/bin/bash
# Bluetooth through the hub's BlueZ client (HubBluetooth, bridge/portal/bluez.py) against python-dbusmock's bluez5 template on a private bus (the instance's
# "system bus"): hello.bluetooth reads the adapter, discovers, scans with a filter, connects GATT, reads and gets a notification, bonds, switches the
# adapter off and on, opens an RFCOMM socket (the mock's echo service), one to a channel (a real AF_BLUETOOTH socket: it must fail with the kernel's
# reason, there is no such device), a server socket its own client connects to, then pairs three devices: one the app answers itself (ACTION_PAIRING_REQUEST
# and setPin, no dialog), a legacy one whose PIN is typed into the hub's dialog, one whose passkey is compared there. Then once more with BLUETOOTH_SCAN revoked: discovery must throw SecurityException, as on Android 12+.
# On the VM, in the group's instance (tests/run bluetooth). Needs python3-dbusmock (apt).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/bluetooth.out
python3 -c 'import dbusmock' 2>/dev/null || { echo "FAIL: python3-dbusmock is not installed (sudo apt install python3-dbusmock)"; exit 1; }
read -r address daemon < <(dbus-daemon --session --fork --print-address=1 --print-pid=1 | paste -sd' ')
export DBUS_SYSTEM_BUS_ADDRESS=$address
python3 -m dbusmock --template bluez5 --system >/dev/null 2>&1 & mock=$!
trap 'bin/apklayer stop >/dev/null 2>&1; kill $mock $daemon 2>/dev/null' EXIT
gdbus wait --system --timeout 10 org.bluez && python3 tests/vm/bluez-mock.py || { echo "FAIL: no mock BlueZ"; exit 1; }
bin/apklayer stop >/dev/null 2>&1
{ python3 -u tests/vm/answer-dialog.py text:1234 40 && python3 -u tests/vm/answer-dialog.py yes 20; } > $out.pin 2>&1 & answer=$!  # mock-pin's PIN, mock-compare's passkey
APKLAYER_JOPTS="-Dapklayer.runms=30000" bin/apklayer run hello.bluetooth > $out 2>&1 || true
kill $answer 2>/dev/null; cat $out.pin
grep -a "\[bt\]\|bluetooth" $out
bin/apklayer stop >/dev/null 2>&1
for want in "adapter state=12 enabled=true name=apklayer-mock address=02:00:00:00:00:00" "bonded 11:22:33:44:55:66 mock-paired type=1 bond=12" \
        "discovery-started" "found 11:22:33:44:55:66 name=mock-paired" "discovery-finished discovering=false" \
        "scan C0:FF:EE:00:00:01 name=mock-ble rssi=-50 type=2 mfr=010203" "gatt state=2 status=0" "services 1 battery=true" \
        "read 00002a19-0000-1000-8000-00805f9b34fb value=42 status=0" "ccc written status=0" "notify 00002a19-0000-1000-8000-00805f9b34fb value=43" \
        "bond C0:FF:EE:00:00:01 10->11" "bond C0:FF:EE:00:00:01 11->12" "state 12->10 enabled=false" "state 10->12 enabled=true" \
        "rfcomm connected=true" "rfcomm read echo:ping" "rfcomm channel 3 failed read failed, socket might closed or timeout (ERR " \
        "server accept timeout Try again" "server accepted 11:22:33:44:55:66 connected=true" "server client read got:hello" "server close woke accept socket closed" \
        "rfcomm pipes left=0" "pairing-request AA:BB:CC:00:00:02 variant=0 key=-1" "setpin answered=true" "bond AA:BB:CC:00:00:02 11->12" \
        "bond AA:BB:CC:00:00:01 10->11" "bond AA:BB:CC:00:00:01 11->12" "pairing-request AA:BB:CC:00:00:03 variant=2 key=123456" "bond AA:BB:CC:00:00:03 11->12" "PASS"; do
    grep -aqF "[bt] $want" $out || { echo "FAIL: no '[bt] $want'"; exit 1; }
done
grep -aq "\[bt\] scan C0:FF:EE:00:00:02" $out && { echo "FAIL: the scan filter let the device without the Battery service through"; exit 1; }
grep -q 'ANSWER text:1234 "Pair with mock-pin?"' $out.pin && grep -q 'ANSWER yes "Pair with mock-compare?"' $out.pin \
    || { echo "FAIL: the hub's dialogs were not mock-pin's PIN, then mock-compare's passkey (mock-setpin's answer is the app's)"; exit 1; }
echo "bluetooth: mock BlueZ flow passed"
bin/apklayer permissions revoke hello.bluetooth android.permission.BLUETOOTH_SCAN
APKLAYER_JOPTS="-Dapklayer.runms=8000" bin/apklayer run hello.bluetooth > $out.revoked 2>&1 || true
bin/apklayer permissions grant hello.bluetooth android.permission.BLUETOOTH_SCAN
grep -a "\[bt\]" $out.revoked
grep -aq "\[bt\] discovery SecurityException" $out.revoked || { echo "FAIL: discovery without BLUETOOTH_SCAN did not throw"; exit 1; }
# a host whose BlueZ is activatable but never comes up (bluetooth.service without an adapter): the helper answers "absent" at once instead of after
# D-Bus's activation timeout (an app's first BluetoothAdapter call waits for that line on its main thread: Briar stayed on its splash)
dir=$(mktemp -d); printf '[D-BUS Service]\nName=org.bluez\nExec=/bin/sleep 30\n' > $dir/org.bluez.service
printf '<busconfig><include>/usr/share/dbus-1/session.conf</include><servicedir>%s</servicedir></busconfig>' $dir > $dir/bus.conf
read -r address2 daemon2 < <(dbus-daemon --config-file=$dir/bus.conf --fork --print-address=1 --print-pid=1 | paste -sd' ')
start=$(date +%s%N)
first=$(DBUS_SYSTEM_BUS_ADDRESS=$address2 python3 bridge/portal/bluez.py < /dev/null 2>/dev/null | head -1)
ms=$(( ($(date +%s%N) - start) / 1000000 )); kill $daemon2; rm -rf $dir
echo "bluetooth: unstartable BlueZ: '$first' after $ms ms"
[ "$first" = '{"ev": "adapter", "present": false}' ] && [ $ms -lt 5000 ] || { echo "FAIL: the helper's first line waited on BlueZ's activation"; exit 1; }
echo "bluetooth: PASS"
