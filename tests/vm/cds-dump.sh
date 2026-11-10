#!/bin/bash
# The instance's CDS archive (apklayer.host.CdsDump), run on the VM: `tests/vm/cds-dump.sh <package> [line]` (line: what the app prints in
# runs 2 and 3, e.g. hello.cdsloader's own ClassLoader subclass at work). (1) The app ends while its first launch
# dumps: no archive is kept unless that dump finished. (2) A full first launch writes one with no unlinked custom-loader class (HotSpot keeps
# such a class's constant pool as it is: a nest host's resolved member entry crashed Termux, docs/history/PLAN-2026-11-10.md). (3) A cold launch runs on it.
# Honours APKLAYER_INSTANCE, APKLAYER_NATIVE, XDG_* like the CLI.
cd "$(dirname "$0")/../.."
pkg="$1" marker="${2:-}"
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}"
out="$state/cds-run"
fail() { echo "FAIL cds-dump: $1"; bin/apklayer stop; exit 1; }
bin/apklayer stop
rm -rf "$state/cds" "$state"/cds-dump-*.log
# (1) runms counts from the first activity; the dump starts 3 s after it and takes 2-3 s (Parallels: the native exit ends HotSpot's write)
APKLAYER_JOPTS="-Dapklayer.runms=4500 $APKLAYER_EXTRA_JOPTS" bin/apklayer run "$pkg" > "$out.1" 2>&1
bin/apklayer stop
if ls "$state"/cds/*.jsa > /dev/null 2>&1; then
    grep -aq "cds: archive .* written" "$out.1" || fail "an archive kept from a dump its JVM's end interrupted"
    echo "(the dump finished before the app ended: kept)"; rm -f "$state"/cds/*.jsa
fi
# (2)
APKLAYER_JOPTS="-Dapklayer.runms=12000 -Xlog:cds+class=debug:file=$state/cds-dump-%p.log $APKLAYER_EXTRA_JOPTS" bin/apklayer run "$pkg" > "$out.2" 2>&1
bin/apklayer stop
grep -a "cds: " "$out.2"
[ -z "$marker" ] || grep -aqF -- "$marker" "$out.2" || fail "no '$marker' in the first full launch"
ls "$state"/cds/*.jsa > /dev/null 2>&1 || fail "no archive after a full first launch"
ls "$state"/cds/*.tmp* > /dev/null 2>&1 && fail "an interrupted dump's file left beside the archive"
unlinked=$(cat "$state"/cds-dump-*.log | grep -ac "unreg .*unlinked")
rm -f "$state"/cds-dump-*.log
[ "$unlinked" = 0 ] || fail "$unlinked unlinked custom-loader classes in the archive"
# (3)
APKLAYER_JOPTS="-Dapklayer.runms=5000 $APKLAYER_EXTRA_JOPTS" bin/apklayer run "$pkg" > "$out.3" 2>&1
bin/apklayer stop
grep -aq "SIGSEGV\|A fatal error" "$out.3" && fail "the JVM crashed on the archive"
grep -aq "\[apklayer\] started " "$out.3" || fail "no activity started on the archive"
[ -z "$marker" ] || grep -aqF -- "$marker" "$out.3" || fail "no '$marker' in the launch on the archive"
echo "PASS cds-dump: no interrupted archive kept, no unlinked custom-loader class, a launch on it"
