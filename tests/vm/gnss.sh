#!/bin/bash
# gnss.sh: the D-Bus GNSS backends against stand-in location services on private buses, and the hub's one GNSS HAL client (HubGnss) serving
# two apps at once. GeoClue 2 (libapklayer-gnss-geoclue.so, python-dbusmock template tests/vm/geoclue2-mock.py): hello.gps alone gets the
# mock's point as gps fixes; hello.gps and hello.gps2 together both get them; with the desktop's agent refusing (deny) the provider stays
# without fixes. Then lomiri-location-service (Ubuntu Touch, tests/vm/lomiri-location-mock.py) and GeoClue 1 on the session bus (Sailfish OS,
# tests/vm/geoclue1-mock.py): hello.gps gets each one's point.
# On the VM, in the group's instance (tests/run gnss). Needs python3-dbusmock (apt).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p "$APKLY_STATE"
python3 -c 'import dbusmock' 2>/dev/null || { echo "FAIL: python3-dbusmock is not installed (sudo apt install python3-dbusmock)"; exit 1; }
status=0
fail() { echo "FAIL: $*"; status=1; }
read -r address daemon < <(dbus-daemon --session --fork --print-address=1 --print-pid=1 | paste -sd' ')
export DBUS_SYSTEM_BUS_ADDRESS=$address
mock() { python3 -m dbusmock --system --template tests/vm/geoclue2-mock.py -p "$1" >/dev/null 2>&1 & mockpid=$!; gdbus wait --system --timeout 10 org.freedesktop.GeoClue2; }
trap 'bin/apklayer stop >/dev/null 2>&1; bin/apklayer providers unset gnss >/dev/null; kill $mockpid $daemon $session 2>/dev/null' EXIT
mock '{"latitude": 47.3769, "longitude": 8.5417, "accuracy": 6}' || { echo "FAIL: no mock GeoClue"; exit 1; }
bin/apklayer stop >/dev/null 2>&1
bin/apklayer providers set gnss geoclue >/dev/null
bin/apklayer providers | grep -a "gnss"
bin/apklayer providers | grep -aq "^backend	gnss	geoclue	quality [1-9]" || fail "the geoclue backend's probe() does not see GeoClue"
run() { APKLAYER_JOPTS="-Dapklayer.runms=$2" bin/apklayer run "$1" > "$APKLY_STATE/gnss-$1.out" 2>&1; grep -a "\[gps\]\|location:" "$APKLY_STATE/gnss-$1.out"; }

# 1. one app: GeoClue's point through the backend, the HAL and the hub's client, which ends with the app's request (removeUpdates after 5
# fixes), before the app does
run hello.gps 20000
# the hub's events from its last client start on (this run's: the stack was stopped before it)
events=$(grep -a -o "gnss: client started\|gnss: client ended\|died hello.gps" "$APKLY_STATE/hub.log" | awk '/started/ { e = "" } { e = e $0 "," } END { print e }')
echo "hub: $events"
[ "$events" = "gnss: client started,gnss: client ended,died hello.gps," ] || fail "the hub's GNSS client did not run only while hello.gps had its request"
grep -aq "^\[gps\] hello.gps fix 47.37690 8.54170 accuracy 6.0" "$APKLY_STATE/gnss-hello.gps.out" && grep -aq "^\[gps\] hello.gps PASS" "$APKLY_STATE/gnss-hello.gps.out" ||
    fail "hello.gps did not get GeoClue's fixes"
grep -a "gnss geoclue:" "$APKLY_STATE"/*.log | tail -3
grep -aq "fix 37.42200 -122.08406" "$APKLY_STATE/gnss-hello.gps.out" && fail "the HAL reported the AOSP example's mock location before GeoClue's fix"

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
bin/apklayer stop >/dev/null 2>&1
kill $mockpid; wait $mockpid 2>/dev/null

# 4. lomiri-location-service (its own protocol: a session whose updates the service calls on the client)
python3 tests/vm/lomiri-location-mock.py 41.9028 12.4964 4 > "$APKLY_STATE/gnss-lomiri-mock.out" 2>&1 & mockpid=$!
gdbus wait --system --timeout 10 com.lomiri.location.Service || fail "no stand-in lomiri-location-service"
bin/apklayer providers set gnss lomiri >/dev/null
bin/apklayer providers | grep -a "^backend	gnss	lomiri"
run hello.gps 15000
cat "$APKLY_STATE/gnss-lomiri-mock.out"
grep -aq "^\[gps\] hello.gps fix 41.90280 12.49640 accuracy 4.0" "$APKLY_STATE/gnss-hello.gps.out" || fail "hello.gps did not get lomiri-location-service's fix"
# the backend alone (as on the phone): altitude, velocity and heading too
b/bridge/hals/gnss/gnss-backend-fixes "$APKLAYER_NATIVE/libapklayer-gnss-lomiri.so" 10 2>&1 | tee "$APKLY_STATE/gnss-lomiri-backend.out"
grep -q "^fix 41.9028000 12.4964000 alt 35.0 accuracy 4.0 speed 1.50 bearing 90.0" "$APKLY_STATE/gnss-lomiri-backend.out" || fail "the lomiri backend's fix lacks altitude, speed or bearing"
bin/apklayer stop >/dev/null 2>&1
kill $mockpid; wait $mockpid 2>/dev/null

# 5. GeoClue 1 on the session bus (the stack starts on a private one for this step)
read -r session_address session < <(dbus-daemon --session --fork --print-address=1 --print-pid=1 | paste -sd' ')
DBUS_SESSION_BUS_ADDRESS=$session_address python3 -m dbusmock --template tests/vm/geoclue1-mock.py -p '{"latitude": 60.1699, "longitude": 24.9384, "accuracy": 9}' >/dev/null 2>&1 & mockpid=$!
DBUS_SESSION_BUS_ADDRESS=$session_address gdbus wait --session --timeout 10 org.freedesktop.Geoclue.Master || fail "no mock GeoClue 1"
bin/apklayer providers set gnss geoclue1 >/dev/null
DBUS_SESSION_BUS_ADDRESS=$session_address bin/apklayer providers | grep -a "^backend	gnss	geoclue1"
(export DBUS_SESSION_BUS_ADDRESS=$session_address; run hello.gps 15000)
grep -aq "^\[gps\] hello.gps fix 60.16990 24.93840 accuracy 9.0" "$APKLY_STATE/gnss-hello.gps.out" || fail "hello.gps did not get GeoClue 1's fix"
grep -a "gnss \(lomiri\|geoclue1\):" "$APKLY_STATE"/*.log | tail -4
[ $status = 0 ] && echo "gnss: PASS"
exit $status
