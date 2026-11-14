#!/bin/bash
# recreate.sh: hello.recreate calls Activity.recreate() in onResume; the new instance resumes with its window attached. In the group's instance (tests/run recreate).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p "$APKLY_STATE"
out=$APKLY_STATE/recreate.out
grep -aq -m1 '^\[recreate\] END' < <(APKLAYER_JOPTS="-Dapklayer.runms=60000" bin/apklayer run hello.recreate 2>&1 | tee "$out")
bin/apklayer stop >/dev/null 2>&1
wait $! 2>/dev/null
grep -a '^\[recreate\]' "$out"
grep -aq '^\[recreate\] PASS' "$out" && grep -aq 'onCreate state=saved' "$out" && ! grep -aq 'NullPointer' "$out"
