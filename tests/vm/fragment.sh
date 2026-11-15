#!/bin/bash
# fragment.sh: hello.fragment commits a fragment in onCreate and reads its view at onViewCreated/onStart/onResume, also after recreate(). In the group's instance (tests/run fragment).
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
mkdir -p "$APKLY_STATE"
out=$APKLY_STATE/fragment.out
grep -aq -m1 '^\[fragment\] END' < <(APKLAYER_JOPTS="-Dapklayer.runms=60000" bin/apklayer run hello.fragment 2>&1 | tee "$out")
bin/apklayer stop >/dev/null 2>&1
wait $! 2>/dev/null
grep -a '^\[fragment\]' "$out"
grep -aq '^\[fragment\] PASS' "$out" && ! grep -aq '^\[fragment\] FAIL' "$out" && ! grep -aq 'NullPointer' "$out"
