#!/bin/bash
# Host integration settings (HubHostSettings, HostConfiguration): a running hello.settings (no configChanges: recreated on each change) follows
# the instance's overrides (`apklayer settings set`, live) and the desktop's colour scheme (gsettings through the Settings portal), and redraws:
# its screenshot is light, then dark. Checks every setting the app reports ("[set]" lines) and the broadcasts it gets.
# On the VM, in the group's instance (tests/run settings). The desktop flip touches the shared GNOME session for a few seconds, restored on exit.
cd "$(dirname "$0")/../.." || exit 1
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
work=${APKLY_OUT:-$APKLY_STATE/settings-test}; mkdir -p "$work"; out=$work/run.out
scheme=$(gsettings get org.gnome.desktop.interface color-scheme 2>/dev/null)
restore() { [ -n "$scheme" ] && gsettings set org.gnome.desktop.interface color-scheme "$scheme"; bin/apklayer stop >/dev/null 2>&1; }
trap restore EXIT
fail=0
check() { if [ -n "$2" ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }
creates() { grep -ac '\[set\] create' "$out"; }
# until the app was created n times (a recreation per change), at most 20 s
await() { for _ in $(seq 40); do [ "$(creates)" -ge "$1" ] && return 0; sleep 0.5; done; return 1; }
last() { grep -a '\[set\] create' "$out" | tail -1; }
luma() { python3 -c 'import sys; from PIL import Image, ImageStat; print(int(ImageStat.Stat(Image.open(sys.argv[1]).convert("L")).mean[0]))' "$1"; }

bin/apklayer stop >/dev/null 2>&1
for k in night accent contrast animations font-scale locale timezone time-format; do bin/apklayer settings unset $k >/dev/null; done
bin/apklayer settings set night no >/dev/null  # pinned light before the start
APKLAYER_JOPTS="-Dapklayer.runms=90000" bin/apklayer run hello.settings > "$out" 2>&1 &
app=$!
await 1; sleep 2  # the first frame
bin/apklayer screenshot hello.settings "$work/light.png" > /dev/null 2>&1
check "starts light with night pinned off: $(last | grep -o 'night=[a-z]*' | head -1)" "$(last | grep 'night=false nightMode=1')"

bin/apklayer settings set night yes | grep -q "applied now" || echo "settings set: not live"
await 2; sleep 2
bin/apklayer screenshot hello.settings "$work/dark.png" > /dev/null 2>&1
check "night pinned on: recreated with night=true, UiModeManager MODE_NIGHT_YES" "$(last | grep 'night=true nightMode=2')"
l=$(luma "$work/light.png" 2>/dev/null); d=$(luma "$work/dark.png" 2>/dev/null)
check "the window redrew: light screenshot luma ${l:-none} > 150, dark ${d:-none} < 100" "$([ "${l:-0}" -gt 150 ] && [ "${d:-255}" -lt 100 ] && echo 1)"

n=$(creates)
bin/apklayer settings set font-scale 1.3 >/dev/null; await $((n + 1)); n=$(creates)
check "font scale 1.3" "$(last | grep 'fontScale=1.3')"
bin/apklayer settings set locale de-DE >/dev/null; await $((n + 1)); n=$(creates)
check "locale de-DE (configuration, default locale, a framework string)" "$(last | grep 'locale=de-DE default=de-DE cancel=Abbrechen')"
bin/apklayer settings set timezone Europe/Berlin >/dev/null
bin/apklayer settings set time-format 12 >/dev/null
bin/apklayer settings set animations off >/dev/null
bin/apklayer settings set contrast high >/dev/null
bin/apklayer settings set accent '#3584e4' >/dev/null
sleep 2
grep -a '\[set\] broadcast' "$out" | sort | uniq -c
check "ACTION_TIMEZONE_CHANGED with Europe/Berlin" "$(grep -a '\[set\] broadcast android.intent.action.TIMEZONE_CHANGED tz=Europe/Berlin' "$out")"
check "ACTION_TIME_SET with the 12-hour format" "$(grep -a '\[set\] broadcast android.intent.action.TIME_SET tz=null 24h=0' "$out")"
check "ACTION_LOCALE_CHANGED" "$(grep -a '\[set\] broadcast android.intent.action.LOCALE_CHANGED' "$out")"
# a last recreation shows the values that need no recreation themselves (time zone, 24h, animator scale, contrast, palette seed)
bin/apklayer settings set font-scale 1.0 >/dev/null; await $((n + 1)); n=$(creates)
last
check "time zone, 12-hour clock, animator scale 0, contrast 1, palette seed" "$(last | grep 'tz=Europe/Berlin 24h=false animator=0.0 contrast=1.0 palette=.*FF3584E4')"
grep -aq "configuration change failed" "$out" && check "no failed configuration change" ""

# the desktop's colour scheme, live (night unpinned): flip it to the other one, then back
bin/apklayer settings unset night >/dev/null; sleep 2; n=$(creates)  # recreated only when the desktop is light
host=$(last | grep -o 'night=[a-z]*' | head -1)
check "night follows the desktop when unpinned ($scheme: $host)" "$( { [ "$scheme" = "'prefer-dark'" ] && [ "$host" = night=true ]; } || { [ "$scheme" != "'prefer-dark'" ] && [ "$host" = night=false ]; } && echo 1)"
if [ "$scheme" = "'prefer-dark'" ]; then flip="'default'" want=night=false; else flip="'prefer-dark'" want=night=true; fi
gsettings set org.gnome.desktop.interface color-scheme "$flip"
await $((n + 1)); n=$(creates)
bin/apklayer screenshot hello.settings "$work/desktop.png" > /dev/null 2>&1
gsettings set org.gnome.desktop.interface color-scheme "$scheme"
check "the desktop's color-scheme $flip reached the running app ($(last | grep -o 'night=[a-z]*' | head -1))" "$(last | grep "$want")"
await $((n + 1))
check "and back ($scheme: $(last | grep -o 'night=[a-z]*' | head -1))" "$(last | grep "$host")"
for k in night accent contrast animations font-scale locale timezone time-format; do bin/apklayer settings unset $k >/dev/null; done
bin/apklayer stop >/dev/null 2>&1
wait $app 2>/dev/null
grep -a "\[apklayer\] host settings\|recreated" "$out" | tail -4
echo "  screenshots and log: $work"
exit $fail
