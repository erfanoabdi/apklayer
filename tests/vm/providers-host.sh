#!/bin/bash
# providers-host.sh: the host backends of the sensors and vibrator providers (docs/PLAN.md "Plan: hardware providers" 7c) against mocks, through
# Android's APIs from hello.hardware, with no backend named in providers.toml (each domain's default is the best probe()):
#  1. iio-sensor-proxy and feedbackd (haptic interface): python-dbusmock on a private system and session bus; a value changed while the app
#     runs (PropertiesChanged) reaches SensorManager;
#  2. sensorfw (D-Bus and its data socket) and an older feedbackd (events only);
#  3. a uinput force-feedback device (FF_RUMBLE) and no feedbackd: the ff backend.
# On the VM, in the group's instance (tests/run hardware-host). Needs python3-dbusmock and sudo for /dev/uinput.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p "$APKLY_STATE"
status=0
fail() { echo "FAIL: $*"; status=1; }
python3 -c 'import dbusmock' 2>/dev/null || { echo "FAIL: python3-dbusmock is not installed (sudo apt install python3-dbusmock)"; exit 1; }
mocks=tests/vm/mocks
read -r system system_pid < <(dbus-daemon --session --fork --print-address=1 --print-pid=1 | paste -sd' ')
read -r session session_pid < <(dbus-daemon --session --fork --print-address=1 --print-pid=1 | paste -sd' ')
export DBUS_SYSTEM_BUS_ADDRESS=$system DBUS_SESSION_BUS_ADDRESS=$session SENSORFW_SOCKET_PATH=$APKLY_STATE/sensorfw
pids=()
ff=
trap 'bin/apklayer stop >/dev/null 2>&1; kill ${pids[*]} $system_pid $session_pid 2>/dev/null; [ -n "$ff" ] && sudo -n kill $ff 2>/dev/null' EXIT
for d in sensors vibrator gnss; do bin/apklayer providers unset $d >/dev/null; done

mock() {  # mock <template> <log> [parameters]: the mock's pid in pids; ready when its name is on the bus
    python3 -m dbusmock --template $mocks/$1.py -l $APKLY_STATE/$2 ${3:+--parameters "$3"} >/dev/null 2>&1 & pids+=($!)
}
defaults() {  # the defaults `apklayer providers` reports for sensors and vibrator
    bin/apklayer providers | grep "^backend\|^default"
    echo "$(bin/apklayer providers | awk '$1 == "default" && $2 == "sensors" {print $3}') $(bin/apklayer providers | awk '$1 == "default" && $2 == "vibrator" {print $3}')"
}
run() {  # run <out> [<trigger line> <command>]: hello.hardware until its verdict; the command runs once when the trigger line appears
    bin/apklayer stop >/dev/null 2>&1
    : > $1
    APKLAYER_JOPTS="-Dapklayer.runms=40000" bin/apklayer run hello.hardware 2>&1 | while IFS= read -r line; do
        echo "$line" >> $1
        [ -n "$2" ] && [[ $line == *"$2"* ]] && eval "$3"
        [[ $line == "[hw] PASS"* || $line == "[hw] FAIL"* ]] && break
    done
    grep -a '^\[hw\]' $1
    bin/apklayer stop >/dev/null 2>&1
}
want() { grep -aqF -- "$2" $1 || fail "no '$2' in $1"; }

# 1. iio-sensor-proxy + feedbackd with the haptic interface
mock iio_sensor_proxy iio.calls; mock feedbackd feedbackd.calls
gdbus wait --system --timeout 10 net.hadess.SensorProxy && gdbus wait --session --timeout 10 org.sigxcpu.Feedback || fail "mocks did not start"
[ "$(defaults | tail -1)" = "iio feedbackd" ] || fail "defaults are not iio and feedbackd"
out=$APKLY_STATE/host-iio.out
run $out "[hw] location enabled" "gdbus call --system -d net.hadess.SensorProxy -o /net/hadess/SensorProxy -m org.freedesktop.DBus.Mock.UpdateProperties net.hadess.SensorProxy \"{'AccelerometerOrientation': <'left-up'>, 'LightLevel': <456.0>}\" >/dev/null"
grep -a "sensors iio\|^sensors:" $APKLY_STATE/sensors.log
want $out "accelerometer=null"  # an orientation is not an accelerometer
want $out "type=27 name=iio-sensor-proxy orientation max=3.00 mindelay=0 value=3.000"  # left-up: rotated 90 degrees clockwise
want $out "type=5 name=iio-sensor-proxy light max=100000.00 mindelay=0 value=456.000"
want $out "type=8 name=iio-sensor-proxy proximity max=5.00 mindelay=0 value=0.000"  # near
want $out "type=3 name=iio-sensor-proxy compass (heading only) max=360.00 mindelay=0 value=42.000 0.000 0.000"
grep -a "Vibrate" $APKLY_STATE/feedbackd.calls
# the waveform's two steps, then the click (the example HAL's 100 ms effect), in this order; off is a 1 ms step at 0
[ "$(grep -a -o 'Vibrate "apklayer" \[([0-9.]*, [0-9]*)\]' $APKLY_STATE/feedbackd.calls | grep -v '(0.0, 1)' | tail -3 | tr '\n' ' ')" = \
  'Vibrate "apklayer" [(1.0, 100)] Vibrate "apklayer" [(0.5019607843137255, 200)] Vibrate "apklayer" [(0.5019607843137255, 100)] ' ] ||
    fail "the app's waveform and click did not reach feedbackd's haptic interface"
kill ${pids[*]} 2>/dev/null; pids=()

# 2. sensorfw + feedbackd with events only
mock sensorfw sensorfw.calls; mock feedbackd feedbackd-events.calls '{"haptic": false}'
gdbus wait --system --timeout 10 com.nokia.SensorService && gdbus wait --session --timeout 10 org.sigxcpu.Feedback || fail "mocks did not start"
[ "$(defaults | tail -1)" = "sensorfw feedbackd" ] || fail "defaults are not sensorfw and feedbackd"
out=$APKLY_STATE/host-sensorfw.out
run $out
grep -a "sensors sensorfw\|^sensors:" $APKLY_STATE/sensors.log
want $out "type=1 name=sensorfw accelerometersensor max=78.45 mindelay=10000 value=4.903 9.807 -2.452"
want $out "type=5 name=sensorfw alssensor max=65535.00 mindelay=0 value=321.000"
want $out "type=8 name=sensorfw proximitysensor max=5.00 mindelay=0 value=0.000"
events=$(grep -a -o '^\[hw\] accelerometer [0-9]* events' $out | grep -o '[0-9][0-9]*')
[ "${events:-0}" -ge 60 ] || fail "the accelerometer at 50 Hz: ${events:-no} events in 2 s"
grep -a "TriggerFeedback\|EndFeedback" $APKLY_STATE/feedbackd-events.calls | tail -4
grep -aq 'TriggerFeedback "apklayer" "phone-incoming-call" {"profile": "quiet"}' $APKLY_STATE/feedbackd-events.calls &&
    grep -aq "EndFeedback" $APKLY_STATE/feedbackd-events.calls || fail "no event feedback triggered and ended"
kill ${pids[*]} 2>/dev/null; pids=()

# 3. a force-feedback device and no feedbackd
gcc -O1 -o $APKLY_STATE/uinput-ff tests/vm/uinput-ff.c || fail "uinput-ff did not build"
coproc FF { sudo -n $APKLY_STATE/uinput-ff 90; }
ff=$FF_PID
read -r _ node <&"${FF[0]}"
echo "uinput device $node"
cat <&"${FF[0]}" > $APKLY_STATE/uinput-ff.log &
[ "$(defaults | tail -1 | cut -d' ' -f2)" = "ff" ] || fail "the vibrator default is not ff"
out=$APKLY_STATE/host-ff.out
run $out
grep -a "vibrator ff" $APKLY_STATE/vibrator.log
sudo -n pkill -P $ff 2>/dev/null; sudo -n kill $ff 2>/dev/null; ff=; wait
cat $APKLY_STATE/uinput-ff.log
[ "$(grep -o 'strong=[0-9]* length=[0-9]*' $APKLY_STATE/uinput-ff.log | tail -3 | tr '\n' ' ')" = "strong=65535 length=100 strong=32896 length=200 strong=32896 length=100 " ] ||
    fail "the app's waveform and click did not reach the force-feedback device"
grep -q "play id=[0-9]* value=1" $APKLY_STATE/uinput-ff.log || fail "no effect played"
for d in sensors vibrator; do bin/apklayer providers unset $d >/dev/null; done
[ $status = 0 ] && echo "providers-host: PASS"
exit $status
