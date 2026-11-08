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
# until a line matching $1 is in the app's output, at most 20 s
seen() { for _ in $(seq 40); do grep -aq "$1" "$out" && return 0; sleep 0.5; done; return 1; }
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
check "the shell redrew the title bar light, then dark" "$(grep -a 'hello.settings: title bar light' "$APKLY_STATE/shell.log" && grep -a 'hello.settings: title bar dark' "$APKLY_STATE/shell.log")"
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
bin/apklayer settings set accent '#e01b24' >/dev/null
seen 'contrast=1.0 accent1_600'; sleep 1; n=$(creates)  # contrast and accent recreate (a new palette), the others need none
aprop=${APKLAYER_NATIVE:-$HOME/arm64-native}/bin/aprop
check "persist.sys.timezone $($aprop get persist.sys.timezone), persist.sys.locale $($aprop get persist.sys.locale) in the instance's store" \
  "$([ "$($aprop get persist.sys.timezone)" = Europe/Berlin ] && [ "$($aprop get persist.sys.locale)" = de-DE ] && echo 1)"
check "a malformed locale is refused" "$(bin/apklayer settings set locale 'de DE' >/dev/null 2>&1 || echo 1)"
grep -a '\[set\] broadcast' "$out" | sort | uniq -c
check "ACTION_TIMEZONE_CHANGED with Europe/Berlin" "$(grep -a '\[set\] broadcast android.intent.action.TIMEZONE_CHANGED tz=Europe/Berlin' "$out")"
check "ACTION_TIME_SET with the 12-hour format" "$(grep -a '\[set\] broadcast android.intent.action.TIME_SET tz=null 24h=0' "$out")"
check "ACTION_LOCALE_CHANGED" "$(grep -a '\[set\] broadcast android.intent.action.LOCALE_CHANGED' "$out")"
# a last recreation shows the values that need no recreation themselves (time zone, 24h, animator scale) and the palette's
bin/apklayer settings set font-scale 1.0 >/dev/null; await $((n + 1)); n=$(creates)
last
check "time zone, 12-hour clock, animator scale 0, palette seed, UiModeManager contrast 1.0" "$(last | grep 'tz=Europe/Berlin 24h=false animator=0.0 palette=.*FFE01B24.* contrast=1.0')"
# dynamic colour: system_accent1_600 from the red seed (the framework's default is blue)
a=$(last | grep -o 'accent1_600=[0-9a-f]*' | cut -d= -f2)
check "dynamic colour: system_accent1_600 #${a:-none} is red-dominant" "$(python3 -c 'import sys; v=int(sys.argv[1],16); r,g,b=v>>16&255,v>>8&255,v&255; sys.exit(0 if r>g and r>b else 1)' "${a:-0}" && echo 1)"
# a change while the activity is stopped (Top over it): Top is recreated at once, Main relaunched when Top finishes
tap() { python3 -c 'import sys; sys.path.insert(0, "tests/lib"); import instance; instance.inject(sys.argv[1], "tap:" + sys.argv[2])' hello.settings 200,300 >/dev/null 2>&1; }
tap; seen '\[set\] top'
n=$(creates)
bin/apklayer settings set font-scale 1.15 >/dev/null; seen 'top fontScale=1.15'; sleep 1
check "a change with Main stopped: Top recreated with it, Main not ($(creates) creations, $n before)" "$(grep -aq 'top fontScale=1.15' "$out" && [ "$(creates)" = "$n" ] && echo 1)"
tap; seen 'relaunched hello.settings.Main at its restart'
check "Main relaunched at its restart with the change: $(grep -a -B1 'relaunched hello.settings.Main' "$out" | grep -o 'fontScale=[0-9.]*')" \
  "$(grep -a -B1 'relaunched hello.settings.Main at its restart' "$out" | grep -q '\[set\] create .*fontScale=1.15' && echo 1)"
check "the hub took contrast high and the accent" "$(grep -a 'host settings .*accent=-2090204.*contrast=1.0' "$APKLY_STATE/hub.log")"
grep -aq "configuration change failed" "$out" && check "no failed configuration change" ""
grep -aq "host settings not written" "$APKLY_STATE/hub.log" && check "the hub wrote every system setting ($(grep -a 'host settings not written' "$APKLY_STATE/hub.log" | head -1))" ""

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
# a fresh start with nothing pinned: the first activity is already in the desktop's scheme (no recreation after it)
out=$work/start.out
APKLAYER_JOPTS="-Dapklayer.runms=30000" bin/apklayer run hello.settings > "$out" 2>&1 &
app=$!
await 1; sleep 2
check "a fresh start follows the desktop ($scheme): $(grep -a -m1 '\[set\] create' "$out" | grep -o 'night=[a-z]*'), $(creates) creation(s)" \
  "$([ "$(creates)" = 1 ] && grep -a -m1 '\[set\] create' "$out" | grep -q "$host" && echo 1)"
bin/apklayer stop >/dev/null 2>&1
wait $app 2>/dev/null
echo "  screenshots and log: $work"
exit $fail
