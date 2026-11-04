#!/bin/bash
# gnss.sh: the GeoClue GNSS backend (libapklayer-gnss-geoclue.so) against a python-dbusmock GeoClue (tests/vm/geoclue2-mock.py) on a private
# "system bus", and the hub's one GNSS HAL client (HubGnss) serving two apps at once: hello.gps alone gets the mock's point as gps fixes; then
# hello.gps and hello.gps2 together both get them; then with the desktop's agent refusing (deny) the provider stays without fixes.
# On the VM, in the group's instance (tests/run gnss). Needs python3-dbusmock (apt).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p "$APKLY_STATE"
python3 -c 'import dbusmock' 2>/dev/null || { echo "FAIL: python3-dbusmock is not installed (sudo apt install python3-dbusmock)"; exit 1; }
status=0
fail() { echo "FAIL: $*"; status=1; }
read -r address daemon < <(dbus-daemon --session --fork --print-address=1 --print-pid=1 | paste -sd' ')
export DBUS_SYSTEM_BUS_ADDRESS=$address
mock() { python3 -m dbusmock --system --template tests/vm/geoclue2-mock.py -p "$1" >/dev/null 2>&1 & mockpid=$!; gdbus wait --system --timeout 10 org.freedesktop.GeoClue2; }
trap 'bin/apklayer stop >/dev/null 2>&1; bin/apklayer providers unset gnss >/dev/null; kill $mockpid $daemon 2>/dev/null' EXIT
mock '{"latitude": 47.3769, "longitude": 8.5417, "accuracy": 6}' || { echo "FAIL: no mock GeoClue"; exit 1; }
bin/apklayer stop >/dev/null 2>&1
bin/apklayer providers set gnss geoclue >/dev/null
bin/apklayer providers | grep -a "gnss"
bin/apklayer providers | grep -aq "^backend	gnss	geoclue	quality [1-9]" || fail "the geoclue backend's probe() does not see GeoClue"
run() { APKLAYER_JOPTS="-Dapklayer.runms=$2" bin/apklayer run "$1" > "$APKLY_STATE/gnss-$1.out" 2>&1; grep -a "\[gps\]\|location:" "$APKLY_STATE/gnss-$1.out"; }

# 1. one app: GeoClue's point through the backend, the HAL and the hub's client
run hello.gps 20000
grep -aq "^\[gps\] hello.gps fix 47.37690 8.54170 accuracy 6.0" "$APKLY_STATE/gnss-hello.gps.out" && grep -aq "^\[gps\] hello.gps PASS" "$APKLY_STATE/gnss-hello.gps.out" ||
    fail "hello.gps did not get GeoClue's fixes"
grep -a "gnss geoclue:" "$APKLY_STATE"/*.log | tail -3

# 2. two apps locating at once: both get fixes from the one client
run hello.gps 25000 & first=$!
run hello.gps2 25000
wait $first
for app in hello.gps hello.gps2; do
    grep -aq "^\[gps\] $app PASS" "$APKLY_STATE/gnss-$app.out" || fail "$app (two apps at once) did not get 5 fixes"
done
grep -a "\[apklayer-hub\] gnss" "$APKLY_STATE/hub.log" | tail -4
bin/apklayer stop >/dev/null 2>&1

# 3. the desktop refuses: the provider stays, no fix (never the example's mock location)
kill $mockpid; wait $mockpid 2>/dev/null
mock '{"deny": true}' || { echo "FAIL: no mock GeoClue"; exit 1; }
run hello.gps 12000
grep -aq "^\[gps\] hello.gps gps=true" "$APKLY_STATE/gnss-hello.gps.out" && ! grep -aq "^\[gps\] hello.gps fix" "$APKLY_STATE/gnss-hello.gps.out" ||
    fail "with GeoClue refusing, hello.gps should see the gps provider without fixes"
grep -a "gnss geoclue:.*disallowed" "$APKLY_STATE"/*.log | tail -1 | grep -q . || fail "the backend did not report GeoClue's refusal"
[ $status = 0 ] && echo "gnss: PASS"
exit $status
