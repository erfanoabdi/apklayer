#!/bin/bash
# phone-checks.sh <ut|sfos> <group>...: runs ON a phone (tools/halium/phone-test.sh copies it with its helpers and calls it). One instance of its own
# (ptest) takes the checks; prints "PASS group: ..." or "FAIL group: ..." per group, exits 1 when one failed. Env: A (the CLI), R (this directory with
# the helper scripts and apks/), OS. Groups: hello (hello.input started, SurfaceFlinger's picture of it), providers (the device's hardware is the
# default), hidl (hidl-list.sh), hidl-host (hello.hidl: the instance's own manager, marker, own service), camera (hello.camera lists cameras), sensors (hello.hardware:
# sensors, location), fullscreen (UiBench hides and shows the bars: the navigation bar inset), navbar (ut: Back on the bar reaches the app, the vibrator's click), race
# (spare-race.py), rotate (sfos: Lipstick turned by its orientation lock: the shell turns the display; the lock is put back), notify (hello.notify: every kind of
# notification reaches the shell's notification service, with the hints it understands; a tap on the popup sends the content intent; the log has PHONE-SHOT lines for phone-test.sh).
os=$1; shift
A=${A:-apklayer} R=${R:-$(dirname "$0")}
export APKLAYER_INSTANCE=${APKLAYER_INSTANCE:-ptest}
fail=0
report() { if [ "$1" = 0 ]; then echo "PASS $2: $3"; else echo "FAIL $2: $3"; fail=1; fi; }
run_app() { APKLAYER_JOPTS="-Dapklayer.runms=${3:-30000} $4" $A run "$1" > "$R/$2.out" 2>&1; }  # pkg, log, ms, extra options
state=~/.local/state/apklayer/$APKLAYER_INSTANCE

$A stop >/dev/null 2>&1
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
    hidl-host)  # docs/HIDL.md "Needs a phone" (tests/vm/hidl.sh on a host-HAL instance): own manager + marker, an own service, the forwarded names (hidl)
        ok=0; m=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$APKLAYER_INSTANCE/data/apklayer/hwservicemanager.local
        run_app hello.hidl hidl-hello 30000
        grep -aq "\[hidl\] hello.hidl PASS" "$R/hidl-hello.out" || { ok=1; echo "  hello.hidl: no PASS"; }
        grep -aq "own service listed=true own PASS" "$R/hidl-hello.out" || { ok=1; echo "  hello.hidl: own service"; }
        [ -e "$m" ] || { ok=1; echo "  no marker $m"; }
        $A exec libapklayer_hidltool.so manager 2>&1 | grep -aq "manager: transport hwbinder, listed 1" || { ok=1; echo "  hidltool manager"; }
        $A exec libapklayer_hidltool.so local 2>&1 | grep -aq "local: register 1, found 1, listed 1" || { ok=1; echo "  hidltool local"; }
        report $ok hidl-host "manager marker, hello.hidl, own service" ;;
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
    fullscreen)  # UiBench hides the system bars (script step) and shows them again: the navigation bar inset 84 -> 0 -> 84, the window whole screen and back
        run_app com.android.test.uibench fullscreen 24000 "-Dapklayer.debugkeys=true -Dapklayer.script=7000:navinset+8000:fullscreen:1+12000:navinset+13000:fullscreen:0+18000:navinset+19000:navinset"
        ins=$(sed -n 's/^\[apklayer\] navinset \(-\?[0-9]*\)$/\1/p' "$R/fullscreen.out" | tr '\n' ' ')
        grep -a -q 'fullscreen on' "$state/shell.log" && grep -a -q 'fullscreen off' "$state/shell.log" && echo "$ins" | awk '{ exit !($1 > 0 && $2 == 0 && $3 == $1 && $4 == $1) }'  # 48 dp of the display: 84 px on the X23, 135 on the Quintus
        report $? fullscreen "navigation bar insets: $ins" ;;
    navbar)  # ut: a touch on the shell's navigation bar (Back, 180,1536 on the X23) reaches the app and gives the haptic click at the vibrator
        [ "$os" = ut ] || { echo "SKIP navbar: Lomiri touch only"; continue; }
        h0=$(cat "$state/hub.log" 2>/dev/null | grep -a -c 'haptic feedback')
        # timers, the app's start takes 20-30 s; the first Back hides the keyboard of the focused field (Android: the IME takes it), the second reaches the app
        ( sleep 34; python3 "$R/ut-uitap.py" 180 1536; sleep 4; python3 "$R/ut-uitap.py" 180 1536 ) >/dev/null 2>&1 &
        run_app hello.input navbar 50000 -Dhello.backlog=1
        wait
        b=$(grep -a -c 'back pressed' "$R/navbar.out"); h=$(cat "$state/hub.log" 2>/dev/null | grep -a -c 'haptic feedback')
        bar=$(grep -a -o 'content [0-9x]* .*navigation bar [0-9]* logical' "$state/shell.log" | tail -1 | sed 's/ @.*navigation/ ... navigation/')
        [ "$b" -ge 1 ] && [ "$h" -gt "$h0" ]; report $? navbar "backs $b, haptic lines $h0 -> $h, $bar" ;;
    race)
        APKLAYER=$A python3 "$R/spare-race.py" hello.input 6 > "$R/race.out" 2>&1; r=$?; report $r race "$(tail -1 "$R/race.out" | cut -c1-200)" ;;
    notify)  # hello.notify posts every kind of notification; what the shell's org.freedesktop.Notifications service got (dbus-monitor) and a tap on the first popup
        $A permissions grant hello.notify android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
        mon=$R/notify.mon; : > "$R/notify.out"; tapped=; shot=
        dbus-monitor --session "interface='org.freedesktop.Notifications'" > "$mon" 2>&1 & mpid=$!
        case $os in sfos) tap="python3 $R/touch.py tap:540,1200 wait:800 tap:1020,500 wait:800 tap:60,1900 wait:800 tap:540,150 wait:800 tap:540,150" ;; *) tap="python3 $R/ut-uitap.py ${NOTIFY_TAP:-360 150}" ;; esac
        while IFS= read -r l; do
            echo "$l" >> "$R/notify.out"
            case $l in *"[nf] POSTED"*) [ -n "$tapped" ] || { echo "PHONE-SHOT notify-pre"; $tap; }; tapped=1 ;; "[nf] progress updated"*) [ -n "$shot" ] || echo "PHONE-SHOT notify"; shot=1 ;; esac  # PHONE-SHOT: phone-test.sh takes the Lipstick picture
        done < <(APKLAYER_JOPTS="-Dapklayer.runms=18000" $A run hello.notify 2>&1)
        kill $mpid 2>/dev/null
        # the Notify calls of the app: "replaces id|summary|hint keys", from dbus-monitor's text
        awk '/member=Notify/ { n = 1; next } n && /string "hello.notify"/ { app = 1; next } app && /uint32/ { rep = $2; next } app && /^ *string "nf / { s = $0; sub(/^ *string "/, "", s); sub(/"$/, "", s); summary = s; hints = ""; inh = 1; next }
             inh && /^ *string "(urgency|suppress-sound|value|progress|image-path|resident|x-nemo-preview-summary)"/ { h = $0; sub(/^ *string "/, "", h); sub(/"$/, "", h); hints = hints " " h }
             inh && /int32 -1/ { print rep "|" summary "|" hints; n = 0; app = 0; inh = 0 }' "$mon" > "$R/notify.calls"
        want="basic bigtext progress silent vibrate child.A child.B cancel.me"; [ "$os" = sfos ] && want="$want low summary ongoing foreground"
        miss=""; for w in $want; do grep -q "|nf ${w//./ }|" "$R/notify.calls" || miss="$miss $w"; done
        [ "$os" = sfos ] || { grep -q '|nf low|' "$R/notify.calls" && miss="$miss low(must be quiet)"; }
        report $([ -z "$miss" ]; echo $?) notify-shown "$(cut -d'|' -f2 "$R/notify.calls" | sort -u | wc -l) distinct notifications, missing:${miss:- none}"
        ok=0; grep -q '^[1-9][0-9]*|nf basic|' "$R/notify.calls" && grep -q '^[1-9][0-9]*|nf progress|' "$R/notify.calls" || ok=1
        report $ok notify-replace "basic and progress sent again with the shell's id (replaces_id)"
        ok=0; grep -q '|nf progress|.*progress' "$R/notify.calls" || ok=1; grep -q '|nf silent|.*suppress-sound' "$R/notify.calls" || ok=1; grep -q '|nf basic|.*image-path' "$R/notify.calls" || ok=1
        [ $os = sfos ] && { grep -q '|nf basic|.*x-nemo-preview-summary' "$R/notify.calls" || ok=1; }
        report $ok notify-hints "progress bar, suppress-sound on the silent channel, the large icon, urgency$([ $os = sfos ] && echo ', Lipstick banner text')"
        grep -q 'member=CloseNotification' "$mon"; report $? notify-cancel "cancel() closed the shell's popup"
        if [ "$os" = ut ]; then  # Lomiri (2026-11-16, X23): its notification server closes every Notify within 50 ms (reason 1), also one sent by gdbus alone: no popup to tap
            echo "SKIP notify-click: Lomiri closes the popups at once ($(grep -a -c 'member=NotificationClosed' "$mon") closes by the shell)"
        else
            grep -a -q '^\[nf\] received nf.OPEN' "$R/notify.out"; report $? notify-click "a tap on the popup sent the content intent (received: $(grep -a '^\[nf\] received' "$R/notify.out" | cut -d' ' -f3 | tr '\n' ' '); hub: $(grep -a -c 'notification click' "$state/hub.log" 2>/dev/null) clicks, $(grep -a 'closed by the desktop' "$state/hub.log" 2>/dev/null | head -3 | cut -d' ' -f2-7 | tr '\n' ';'))" 
        fi ;;
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
