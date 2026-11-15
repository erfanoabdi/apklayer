#!/bin/bash
# phone-checks.sh <ut|sfos> <group>...: runs ON a phone (tools/halium/phone-test.sh copies it with its helpers and calls it). One instance of its own
# (ptest) takes the checks; prints "PASS group: ..." or "FAIL group: ..." per group, exits 1 when one failed. Env: A (the CLI), R (this directory with
# the helper scripts and apks/), OS. Groups: hello (hello.input started, SurfaceFlinger's picture of it), providers (the device's hardware is the
# default), hidl (hidl-list.sh), camera (hello.camera lists cameras), sensors (hello.hardware: sensors, location), race (spare-race.py), rotate (sfos:
# Lipstick turned by its orientation lock: the shell turns the display; the lock is put back).
os=$1; shift
A=${A:-apklayer} R=${R:-$(dirname "$0")}
export APKLAYER_INSTANCE=ptest
fail=0
report() { if [ "$1" = 0 ]; then echo "PASS $2: $3"; else echo "FAIL $2: $3"; fail=1; fi; }
run_app() { APKLAYER_JOPTS="-Dapklayer.runms=${3:-30000} $4" $A run "$1" > "$R/$2.out" 2>&1; }  # pkg, log, ms, extra options
state=~/.local/state/apklayer/$APKLAYER_INSTANCE

$A stop >/dev/null 2>&1
# MediaTek's EGL layer (libGLES_meow, the Quintus) crashes in SurfaceFlinger's dlopen (docs/HALIUM.md): the instance uses Mali's own
[ "$os" = sfos ] && $A prop set ro.hardware.egl mali >/dev/null 2>&1
for apk in "$R"/apks/*.apk; do [ -e "$apk" ] && $A install "$apk" >/dev/null 2>&1; done

for g in "$@"; do
    case $g in
    hello)
        INSTANCE=$APKLAYER_INSTANCE APKLAYER=$A RUNMS=20000 bash "$R/ut-shot.sh" hello.input "$R/hello.png" > "$R/hello.out" 2>&1
        [ -s "$R/hello.png" ] && [ "$(wc -c < "$R/hello.png")" -gt 3000 ]; report $? hello "picture $(wc -c < "$R/hello.png" 2>/dev/null) bytes" ;;
    newpipe)  # org.schabi.newpipe installed by hand (apklayer install): runs, draws, no fatal exception
        INSTANCE=$APKLAYER_INSTANCE APKLAYER=$A RUNMS=40000 bash "$R/ut-shot.sh" org.schabi.newpipe "$R/newpipe.png" > "$R/newpipe.out" 2>&1
        ! grep -a -q "FATAL EXCEPTION" "$R/newpipe.png.log" && [ "$(wc -c < "$R/newpipe.png" 2>/dev/null || echo 0)" -gt 3000 ]
        report $? newpipe "picture $(wc -c < "$R/newpipe.png" 2>/dev/null) bytes" ;;
    providers)
        p=$($A providers); ok=0
        for h in graphics camera media vibrator; do echo "$p" | awk -F'\t' -v h=$h '$1 == "default" && $2 == h && $3 == "device" { f = 1 } END { exit !f }' || { ok=1; echo "  $h is not device"; }; done
        report $ok providers "graphics, camera, media, vibrator = device" ;;
    hidl)
        bash "$R/hidl-list.sh" "$APKLAYER_INSTANCE" > "$R/hidl.out" 2>&1; r=$?; cat "$R/hidl.out" | sed 's/^/  /'; report $r hidl "device camera provider and Codec2 listed" ;;
    camera)
        $A permissions grant hello.camera android.permission.CAMERA >/dev/null 2>&1
        run_app hello.camera camera 45000 -Dhello.real=1
        l=$(grep -a '^\[cam\] cameras ' "$R/camera.out" | head -1)
        case "$l" in *"[]"*|"") report 1 camera "no cameras: ${l:-no line}" ;; *) report 0 camera "$l" ;; esac ;;
    sensors)
        run_app hello.hardware sensors 25000
        n=$(sed -n 's/^\[hw\] sensors \([0-9]*\).*/\1/p' "$R/sensors.out" | head -1)
        loc=$(grep -a '^\[hw\] location' "$R/sensors.out" | head -1)
        [ "${n:-0}" -gt 0 ]; report $? sensors "$n sensors, $loc" ;;
    race)
        APKLAYER=$A python3 "$R/spare-race.py" hello.input 6 > "$R/race.out" 2>&1; r=$?; report $r race "$(tail -1 "$R/race.out" | cut -c1-200)" ;;
    rotate)
        [ "$os" = sfos ] || { echo "SKIP rotate: Lipstick only"; continue; }
        old=$(dconf read /lipstick/orientationLock); dconf write /lipstick/orientationLock '"landscape"'
        run_app hello.input rotate 30000
        if [ -n "$old" ]; then dconf write /lipstick/orientationLock "$old"; else dconf reset /lipstick/orientationLock; fi
        grep -a -q 'turned' "$state/shell.log"; report $? rotate "$(grep -a 'configure content' "$state/shell.log" | tail -1 | grep -o 'content [0-9x]*.*turned [0-9]*\|content [0-9x]*' | head -1)" ;;
    *) report 1 "$g" "unknown group" ;;
    esac
    $A stop >/dev/null 2>&1
done
exit $fail
