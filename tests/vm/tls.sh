#!/bin/bash
# tls.sh: hello.tls (tests/apps/hello-tls) prints its [tls] lines; every one must PASS. In the group's instance (tests/run tls); the instance is stopped at the end.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p "$APKLY_STATE"
out=$APKLY_STATE/tls.out
grep -aq -m1 '^\[tls\] END' < <(APKLAYER_JOPTS="-Dapklayer.runms=60000" bin/apklayer run hello.tls 2>&1 | tee "$out")
bin/apklayer stop >/dev/null 2>&1
wait $! 2>/dev/null
grep -a '^\[tls\]' "$out"
if grep -aq '^\[tls\] FAIL' "$out" || ! grep -aq '^\[tls\] PASS summary' "$out"; then grep -a 'Exception\|Error\|warning' "$out" | head -5; exit 1; fi
