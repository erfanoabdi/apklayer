#!/bin/bash
# One app: install (if given an apk path), run it for a few seconds, print the lines that say why it failed, close everything.
# Usage (on the VM): tests/vm/appcheck.sh <package> [apk]      APPCHECK_SECS=18
cd "$(dirname "$0")/../.." || exit 1
pkg=$1; apk=$2
[ -n "$apk" ] && { bin/apklayer install "$apk" 2>&1 | grep -v "wrapped\|log 6" | tail -2; }
out=/tmp/appcheck-$pkg.out
APKLAYER_JOPTS="-Dapklayer.logcat=true" setsid bin/apklayer run "$pkg" > "$out" 2>&1 &
sleep "${APPCHECK_SECS:-18}"
grep -v "wrapped\|PackageParser\|Fence\|Large\|trace file" "$out" | grep -E "FATAL|F0000|Exception:|Error:|SIGSEGV|SIGABRT|Fatal signal|UnsatisfiedLink|NoSuchMethod|NoClassDef|ClassNotFound" | sort | uniq -c | sort -rn | head -${APPCHECK_LINES:-8} | cut -c1-230
echo "frames: $(grep -c 'dmabuf frame' ~/.local/state/apklayer/default/shell.log 2>/dev/null) alive: $(pgrep -f "[a]pklayer.package=$pkg" | head -1)"
bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail
