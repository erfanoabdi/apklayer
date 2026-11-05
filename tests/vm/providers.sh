#!/bin/bash
# providers.sh: the hardware providers (docs/PLAN.md "Plan: hardware providers") with their fake backends, deterministic values from providers.toml:
# each HAL front through its AIDL interface (the fronts' own probes on a stack), then through Android's APIs from hello.hardware, then a changed
# backend option (`apklayer providers set`) restarting only that domain's service. On the VM, in the group's instance (tests/run providers).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p "$APKLY_STATE"
status=0
fail() { echo "FAIL: $*"; status=1; }
bin/apklayer stop >/dev/null 2>&1
trap 'bin/apklayer stop >/dev/null 2>&1; for d in sensors vibrator gnss audio; do bin/apklayer providers unset $d >/dev/null; done' EXIT
bin/apklayer providers set sensors fake accel=0.5,9.5,1.25 >/dev/null
bin/apklayer providers set vibrator fake >/dev/null
bin/apklayer providers set gnss fake location=48.8584,2.2945 accuracy=3 >/dev/null
bin/apklayer providers | tee $APKLY_STATE/providers.out
# the listing the manager's Hardware page reads: the configured options a field each, the options every backend takes
grep -q "^gnss	apklayer	fake	location=48.8584,2.2945	accuracy=3$" $APKLY_STATE/providers.out || fail "providers: the configured options are not a field each"
grep -q "^option	gnss	fake	gpx		file$" $APKLY_STATE/providers.out && grep -q "^option	sensors	fake	accel	0,9.80665,0	text$" $APKLY_STATE/providers.out \
    || fail "providers: no option rows from the backends"
# the audio domain: the VM's PipeWire answers (pipewire ranks above pulse, which pipewire-pulse also answers); the choice is the host library
# every process loads
grep -q "^default	audio	pipewire$" $APKLY_STATE/providers.out || fail "providers: the automatic audio backend is not pipewire"
bin/apklayer env | grep -q "^APKLAYER_HOST_LIBS=.*/libapklayer-audio-pipewire.so" || fail "env: the automatic audio backend is not loaded"
if grep -q "^backend	audio	pulse	quality" $APKLY_STATE/providers.out; then
    bin/apklayer providers set audio pulse >/dev/null
    bin/apklayer env | grep -q "^APKLAYER_HOST_LIBS=.*/libapklayer-audio-pulse.so" || fail "env: providers set audio pulse did not reach the processes"
    bin/apklayer providers unset audio >/dev/null
fi
grep -q "^default	graphics	apklayer$" $APKLY_STATE/providers.out && grep -q "^backend	graphics	device	unavailable	(the device's HAL)	not a Halium device" $APKLY_STATE/providers.out \
    || fail "providers: graphics is not the stack's own, with the device's HAL unavailable"

# 1. each HAL as its framework client uses it (sensors: NDK ASensorManager through SensorService, since the HAL serves one client): the stack runs the probe as its tool (output in tool.log; the next stack removes it)
tool=$APKLY_STATE/tool.log
bin/apklayer stack libapklayer_sensors.so --probe >/dev/null 2>&1
grep -a "^sensor type\|accelerometer:\|PASS\|Check failed" $tool
grep -aq "PASS sensors through SensorService" $tool && grep -aq "last 0.500 9.500 1.250" $tool || fail "sensors (NDK over SensorService and the HAL): no accelerometer events with the configured value"
bin/apklayer stack libapklayer_vibrator.so --probe >/dev/null 2>&1
grep -a "PASS\|Check failed" $tool; grep -a "vibrator:" $APKLY_STATE/vibrator.log
grep -aq "PASS vibrator AIDL" $tool && grep -aq "vibrator: on 120 ms amplitude 0.50" $APKLY_STATE/vibrator.log || fail "vibrator AIDL: the backend did not get on 120 ms at 0.5"
bin/apklayer stack libapklayer_gnss.so --client 2 >/dev/null 2>&1
grep -a "^location\|PASS\|Check failed" $tool
grep -aq "PASS gnss AIDL" $tool && grep -aq "^location 48.8584000 2.2945000 3.0" $tool || fail "gnss AIDL: no fix at the configured location"

# 2. through Android's APIs: Vibrator (the hub's vibrator_manager over the HAL), LocationManager's gps provider, SensorManager over SensorService
out=$APKLY_STATE/providers-app.out
grep -aq -m1 '^\[hw\] \(PASS\|FAIL\)' < <(APKLAYER_JOPTS="-Dapklayer.runms=40000" bin/apklayer run hello.hardware 2>&1 | tee $out)
grep -a '\[hw\]\|\[runtime\] registering\|no registration function' $out
grep -a "location \[" $APKLY_STATE/hub.log
grep -a "vibrator:" $APKLY_STATE/vibrator.log
grep -aq '^\[hw\] PASS' $out || fail "hello.hardware"
# the waveform's two steps, then the click (the example HAL's 100 ms effect), in this order
[ "$(grep -a -o 'vibrator: on [0-9]* ms amplitude [0-9.]*' $APKLY_STATE/vibrator.log | tail -3 | tr '\n' ' ')" = \
  "vibrator: on 100 ms amplitude 1.00 vibrator: on 200 ms amplitude 0.50 vibrator: on 100 ms amplitude 0.50 " ] || fail "the app's waveform and click did not reach the vibrator backend"
grep -aq '^\[hw\] gps fix 48.85840 2.29450 accuracy 3.0' $out || fail "the app's gps fix is not the configured one"
# SensorManager over SensorService: 50 Hz for 2 s (about 100 events), the configured value
events=$(grep -a -o '^\[hw\] accelerometer [0-9]* events' $out | grep -o '[0-9][0-9]*')
[ "${events:-0}" -ge 60 ] && grep -aq '^\[hw\] accelerometer .* last 0.500 9.500 1.250' $out || fail "the app's accelerometer: ${events:-no} events, not the configured value"
bin/apklayer stop hello.hardware >/dev/null 2>&1
wait $! 2>/dev/null

# 3. a changed option restarts only that domain's service (the stack the app started still runs)
before=$(wc -l < $APKLY_STATE/stack.log)  # stack.log outlives stacks: only the lines from here on
bin/apklayer providers set gnss fake location=1.5,2.5 accuracy=7
bin/apklayer exec libapklayer_gnss.so --client 1 > $APKLY_STATE/providers-client.out 2>&1
grep -a "^location\|PASS" $APKLY_STATE/providers-client.out
grep -aq "^location 1.5000000 2.5000000 7.0" $APKLY_STATE/providers-client.out || fail "gnss: the new location did not reach the client"
reload=$(tail -n +$((before + 1)) $APKLY_STATE/stack.log | grep -a "providers:")
echo "$reload"
grep -q "providers: gnss restarted" <<<"$reload" && ! grep -q "providers: \(sensors\|vibrator\)" <<<"$reload" || fail "not only gnss restarted"
# `stop` ends the restarted service too (a service the daemon restarts must not inherit its blocked SIGTERM)
bin/apklayer stop >/dev/null 2>&1
for p in $(pgrep -f '[l]ibapklayer_(gnss|sensors|vibrator)\.so --main'); do
    tr '\0' '\n' < /proc/$p/environ 2>/dev/null | grep -qx "APKLAYER_INSTANCE=${APKLAYER_INSTANCE:-default}" && { fail "provider service $p outlived stop"; kill -9 $p; }
done
exit $status
