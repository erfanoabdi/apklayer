#!/bin/sh
# threads: the thread lifetime stress of hello-arm64 (tests/apps/hello-arm64/threads.c, 2000 iterations of create/join/detach/exit/signal/fork) prints the
# lines real arm64 prints (threads-expected.txt: from the same code built as a static program on a real arm64 host); a hang ends at the run time.
set -eu
out=$(mktemp)
trap 'rm -f "$out"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
APKLAYER_JOPTS="-Dapklayer.runms=60000 -Dhello.threads=2000" bin/apklayer run hello.arm64 > "$out" 2>&1 || true
sed -n 's/^.*\[hello-arm64\] threads //p' "$out" | grep -v '^DONE' > "$out.lines"
diff tests/apps/hello-arm64/threads-expected.txt "$out.lines"; rm -f "$out.lines"
