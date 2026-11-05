#!/bin/bash
# plugins.sh: an out-of-tree hardware plugin (docs/PLUGINS.md). examples/plugin-vibrator-hello is built against the installed API only (the
# Meson install's apklayer-plugin.pc through PKG_CONFIG_PATH, its own build and prefix in a temp dir), installs into apklayer's plugin dir
# (the .pc's plugindir), is listed by `apklayer providers`, chosen with `providers set`, and gets the vibrations of the HAL's AIDL probe and
# of hello.hardware. A plugin of another ABI version (plain cc, in $APKLAYER_PLUGIN_PATH) is refused: listed as such, never the default,
# `providers set` fails, and a providers.toml naming it starts no vibrator service. On the VM, in the group's instance (tests/run plugins).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
id=${APKLAYER_INSTANCE:-default}
toml=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$([ "$id" = default ] && echo 0 || echo "$id")/providers.toml
T=$APKLY_STATE/plugin-test
rm -rf "$T" && mkdir -p "$T/bad"
status=0
fail() { echo "FAIL: $*"; status=1; }
export PKG_CONFIG_PATH=$(dirname "$(ls "$PWD"/inst/lib/*/pkgconfig/apklayer-plugin.pc "$PWD"/inst/lib/pkgconfig/apklayer-plugin.pc 2>/dev/null | head -1)")
plugindir=$(pkg-config --variable=plugindir apklayer-plugin)
hello=$plugindir/libapklayer-vibrator-hello.so
bin/apklayer stop >/dev/null 2>&1
trap 'bin/apklayer stop >/dev/null 2>&1; for d in sensors vibrator gnss; do bin/apklayer providers unset $d >/dev/null; done; rm -f "$hello"; rm -rf "$T"' EXIT

# 1. the example, out of tree: its own meson.build with dependency('apklayer-plugin') and nothing else of apklayer's
meson setup "$T/build" examples/plugin-vibrator-hello --prefix "$T/prefix" >/dev/null && ninja -C "$T/build" >/dev/null && meson install -C "$T/build" --quiet \
    || fail "the example did not build or install against apklayer-plugin.pc"
[ -f "$hello" ] && echo "installed $hello" || fail "no $hello"
# built with hidden visibility: only the API leaves the library (the headers export it)
exported=$(nm -D --defined-only "$hello" | awk '{print $3}' | sort | tr '\n' ' ')
echo "exported: $exported"
[ "$exported" = "apklayer_plugin_abi apklayer_vibrator_amplitude apklayer_vibrator_init apklayer_vibrator_off apklayer_vibrator_on apklayer_vibrator_options apklayer_vibrator_probe " ] \
    || fail "the example's exports are not exactly the API"

# 2. a plugin of the next ABI version, plain cc with the .pc's flags; its probe would make it the default if it were not refused
cc -shared -fPIC $(pkg-config --cflags apklayer-plugin) -x c - -o "$T/bad/libapklayer-vibrator-oldabi.so" <<'EOF' || fail "plain cc build"
#include <apklayer/vibrator.h>
#include <stdio.h>
const int apklayer_plugin_abi = APKLAYER_PLUGIN_ABI + 1;
int apklayer_vibrator_probe(void) { return 200; }
int apklayer_vibrator_init(int count, const char *const *options) { (void)count; (void)options; fprintf(stderr, "vibrator oldabi: loaded\n"); return 0; }
int apklayer_vibrator_on(int32_t duration_ms) { (void)duration_ms; return 0; }
int apklayer_vibrator_off(void) { return 0; }
int apklayer_vibrator_amplitude(float amplitude) { (void)amplitude; return 0; }
EOF
export APKLAYER_PLUGIN_PATH=$T/bad

# 3. the listing (the manager's Hardware page reads it)
bin/apklayer providers unset vibrator >/dev/null
bin/apklayer providers > "$T/providers.out"
grep "vibrator" "$T/providers.out"
grep -q "^backend	vibrator	hello	quality 0	$hello$" "$T/providers.out" || fail "providers: the installed plugin is not listed"
grep -q "^option	vibrator	hello	greeting	hello	text$" "$T/providers.out" || fail "providers: no option row of the plugin"
grep -q "^backend	vibrator	oldabi	refused: plugin ABI 2, this apklayer takes 1	$T/bad/libapklayer-vibrator-oldabi.so$" "$T/providers.out" \
    || fail "providers: the other ABI is not refused"
grep -q "^default	vibrator	oldabi" "$T/providers.out" && fail "the refused plugin became the default"
out=$(bin/apklayer providers set vibrator oldabi 2>&1) && fail "providers set took the refused plugin"
echo "$out"
grep -q "refused: plugin ABI 2, this apklayer takes 1" <<<"$out" || fail "providers set: no clear refusal"

# 4. a providers.toml naming it anyway (an upgrade of apklayer under an old plugin): the stack starts no vibrator service, nothing crashes
printf '[vibrator]\nprovider = "apklayer"\nbackend = "oldabi"\n' > "$toml"
rm -f "$APKLY_STATE/vibrator.log"
bin/apklayer stack libapklayer_servicetool.so list > "$T/stack.out" 2>&1
grep -a "providers:" "$T/stack.out"
grep -aq "providers: vibrator: .*oldabi.so refused: plugin ABI 2" "$T/stack.out" || fail "the stack did not report the refused plugin"
grep -aq "IVibrator" "$APKLY_STATE/tool.log" && fail "a vibrator HAL ran with the refused plugin"
[ -e "$APKLY_STATE/vibrator.log" ] && fail "a vibrator front started: $(cat "$APKLY_STATE/vibrator.log")"
bin/apklayer stop >/dev/null 2>&1

# 5. chosen: the HAL's AIDL probe, then hello.hardware's waveform and click reach the plugin (sensors and GNSS fakes for the app's other checks)
bin/apklayer providers set vibrator hello greeting=hi >/dev/null || fail "providers set vibrator hello"
bin/apklayer providers set sensors fake >/dev/null
bin/apklayer providers set gnss fake >/dev/null
bin/apklayer stack libapklayer_vibrator.so --probe >/dev/null 2>&1
grep -a "PASS\|Check failed" "$APKLY_STATE/tool.log"; grep -a "vibrator" "$APKLY_STATE/vibrator.log"
grep -aq "PASS vibrator AIDL" "$APKLY_STATE/tool.log" && grep -aq "vibrator hi: on 120 ms amplitude 0.50" "$APKLY_STATE/vibrator.log" \
    || fail "vibrator AIDL: the plugin did not get on 120 ms at 0.5"
app=$T/app.out
grep -aq -m1 '^\[hw\] \(PASS\|FAIL\)' < <(APKLAYER_JOPTS="-Dapklayer.runms=40000" bin/apklayer run hello.hardware 2>&1 | tee "$app")
grep -a '\[hw\]' "$app"
grep -a "vibrator hi:" "$APKLY_STATE/vibrator.log"
# the waveform's two steps, then the click (the example HAL's 100 ms effect), in this order
[ "$(grep -a -o 'vibrator hi: on [0-9]* ms amplitude [0-9.]*' "$APKLY_STATE/vibrator.log" | tail -3 | tr '\n' ' ')" = \
  "vibrator hi: on 100 ms amplitude 1.00 vibrator hi: on 200 ms amplitude 0.50 vibrator hi: on 100 ms amplitude 0.50 " ] \
    || fail "the app's waveform and click did not reach the plugin"
bin/apklayer stop hello.hardware >/dev/null 2>&1
wait $! 2>/dev/null
exit $status
