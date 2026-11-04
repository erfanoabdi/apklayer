#!/bin/bash
# Bluetooth through the hub's BlueZ client (HubBluetooth, bridge/portal/bluez.py) against python-dbusmock's bluez5 template on a private bus (the instance's
# "system bus"): hello.bluetooth reads the adapter, discovers, scans with a filter, connects GATT, reads and gets a notification, bonds, switches the
# adapter off and on. Then once more with BLUETOOTH_SCAN revoked: discovery must throw SecurityException, as on Android 12+.
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
APKLAYER_JOPTS="-Dapklayer.runms=25000" bin/apklayer run hello.bluetooth > $out 2>&1 || true
grep -a "\[bt\]\|bluetooth" $out
bin/apklayer stop >/dev/null 2>&1
for want in "adapter state=12 enabled=true name=apklayer-mock address=02:00:00:00:00:00" "bonded 11:22:33:44:55:66 mock-paired type=1 bond=12" \
        "discovery-started" "found 11:22:33:44:55:66 name=mock-paired" "discovery-finished discovering=false" \
        "scan C0:FF:EE:00:00:01 name=mock-ble rssi=-50 type=2 mfr=010203" "gatt state=2 status=0" "services 1 battery=true" \
        "read 00002a19-0000-1000-8000-00805f9b34fb value=42 status=0" "ccc written status=0" "notify 00002a19-0000-1000-8000-00805f9b34fb value=43" \
        "bond C0:FF:EE:00:00:01 10->11" "bond C0:FF:EE:00:00:01 11->12" "state 12->10 enabled=false" "state 10->12 enabled=true" "PASS"; do
    grep -aqF "[bt] $want" $out || { echo "FAIL: no '[bt] $want'"; exit 1; }
done
grep -aq "\[bt\] scan C0:FF:EE:00:00:02" $out && { echo "FAIL: the scan filter let the device without the Battery service through"; exit 1; }
echo "bluetooth: mock BlueZ flow passed"
bin/apklayer permissions revoke hello.bluetooth android.permission.BLUETOOTH_SCAN
APKLAYER_JOPTS="-Dapklayer.runms=8000" bin/apklayer run hello.bluetooth > $out.revoked 2>&1 || true
bin/apklayer permissions grant hello.bluetooth android.permission.BLUETOOTH_SCAN
grep -a "\[bt\]" $out.revoked
grep -aq "\[bt\] discovery SecurityException" $out.revoked || { echo "FAIL: discovery without BLUETOOTH_SCAN did not throw"; exit 1; }
echo "bluetooth: PASS"
