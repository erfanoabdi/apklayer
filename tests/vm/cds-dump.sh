#!/bin/bash
# The instance's class archive (apklayer.host.CdsDump, cli/env.cpp cdsArgs), run on the VM: `tests/vm/cds-dump.sh <package> [line]` (line: what the app prints in
# the launches on the archive, e.g. hello.cdsloader's own ClassLoader subclass at work). Two flows, by the runtime's JDK image:
# JDK 21: a .jsa (dynamic dump in the first launch's JVM, which goes on). (1) The app ends while its first launch dumps: no archive is kept unless that
# dump finished. (2) A full first launch writes one with no unlinked custom-loader class (HotSpot keeps such a class's constant pool as it is: a nest host's
# resolved member entry crashed Termux, docs/history/PLAN-2026-11-10.md). (3) A cold launch runs on it.
# JDK 25 (not with APKLAYER_AOT=off): a .aot (JEP 483). The first launch trains: it ends 3 s after its first activity and a detached JVM creates the cache.
# (1) a launch cut short leaves no cache, no .tmp file. (2) the training launch ends cleanly and the cache appears, its configuration files gone. (3) a launch on it takes classes from it.
# Both: (4) an archive that is not one (garbage in its place) never stops a launch. Honours APKLAYER_INSTANCE, APKLAYER_NATIVE, XDG_* like the CLI.
cd "$(dirname "$0")/../.."
pkg="$1" marker="${2:-}"
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}"
out="$state/cds-run"
native="${APKLAYER_NATIVE:-inst/lib/apklayer/native}"
ext=jsa
grep -q 'JAVA_VERSION="\(2[5-9]\|[3-9][0-9]\)' "$native/jdk/release" 2> /dev/null && [ "${APKLAYER_AOT:-}" != off ] && ext=aot
fail() { echo "FAIL cds-dump: $1"; bin/apklayer stop; exit 1; }
# the detached create JVM of an AOT training (the shell and the JVM): wait for it to end (blocks on the process, no polling); `apklayer stop` would kill it
await_create() { for p in $(pgrep -f '[A]OTMode=create'); do tail --pid="$p" -f /dev/null; done; }
bin/apklayer stop
await_create
rm -rf "$state/cds" "$state"/cds-dump-*.log "$state"/cds-load-*.log
# (1) runms counts from the first activity; the dump starts 3 s after it and takes 2-3 s (Parallels: the native exit ends HotSpot's write)
APKLAYER_JOPTS="-Dapklayer.runms=$([ $ext = jsa ] && echo 4500 || echo 2000) $APKLAYER_EXTRA_JOPTS" bin/apklayer run "$pkg" > "$out.1" 2>&1
await_create  # before the stop: it ends the create JVM too
bin/apklayer stop
if [ $ext = jsa ]; then
    if ls "$state"/cds/*.jsa > /dev/null 2>&1; then
        grep -aq "cds: archive .* written" "$out.1" || fail "an archive kept from a dump its JVM's end interrupted"
        echo "(the dump finished before the app ended: kept)"; rm -f "$state"/cds/*.jsa
    fi
else  # the app ended before its training: no cache, and no half-made file
    ls "$state"/cds/*.aot "$state"/cds/*.tmp* > /dev/null 2>&1 && fail "a cache or a half-made file kept from a launch that ended before its training"
fi
# (2)
APKLAYER_JOPTS="-Dapklayer.runms=12000 -Xlog:cds+class=debug:file=$state/cds-dump-%p.log $APKLAYER_EXTRA_JOPTS" bin/apklayer run "$pkg" > "$out.2" 2>&1
await_create  # before the stop: it ends the create JVM too
bin/apklayer stop
grep -a "cds: " "$out.2"
if [ $ext = jsa ]; then
    [ -z "$marker" ] || grep -aqF -- "$marker" "$out.2" || fail "no '$marker' in the first full launch"
else
    grep -aq "cds: training done" "$out.2" || fail "the training launch did not reach its end"
    grep -aq "SIGSEGV\|A fatal error" "$out.2" && fail "the JVM crashed in the training launch"
    ls "$state"/cds/*.aot > /dev/null 2>&1 || { tail -5 "$state"/cds/*.aot.log 2> /dev/null; fail "no AOT cache after the training launch"; }
fi
ls "$state"/cds/*."$ext" > /dev/null 2>&1 || fail "no archive after a full first launch"
ls "$state"/cds/*.tmp* "$state"/cds/*.aotconf* > /dev/null 2>&1 && fail "an interrupted dump's file left beside the archive"
unlinked=$(cat "$state"/cds-dump-*.log 2> /dev/null | grep -ac "unreg .*unlinked")
rm -f "$state"/cds-dump-*.log
[ "$unlinked" = 0 ] || fail "$unlinked unlinked custom-loader classes in the archive"
# (3) classes a JVM took from an archive: the default one has ~500 of the JDK's, this one thousands
APKLAYER_JOPTS="-Dapklayer.runms=5000 -Xlog:class+load:file=$state/cds-load-%p.log $APKLAYER_EXTRA_JOPTS" bin/apklayer run "$pkg" > "$out.3" 2>&1
bin/apklayer stop
grep -aq "SIGSEGV\|A fatal error" "$out.3" && fail "the JVM crashed on the archive"
grep -aq "\[apklayer\] started " "$out.3" || fail "no activity started on the archive"
[ -z "$marker" ] || grep -aqF -- "$marker" "$out.3" || fail "no '$marker' in the launch on the archive"
n=$(cat "$state"/cds-load-*.log | grep -ac "source: shared objects file")
rm -f "$state"/cds-load-*.log
[ "$n" -gt 3000 ] || fail "a launch on the archive took only $n classes from it"
# (4) garbage where the archive is: the JVM runs without it (-Xshare:auto, AOTMode auto)
for f in "$state"/cds/*."$ext"; do rm -f "$f"; head -c 4096 /dev/urandom > "$f"; done
APKLAYER_JOPTS="-Dapklayer.runms=5000 $APKLAYER_EXTRA_JOPTS" bin/apklayer run "$pkg" > "$out.4" 2>&1
bin/apklayer stop
grep -aq "SIGSEGV\|A fatal error" "$out.4" && fail "the JVM crashed on a garbage archive"
grep -aq "\[apklayer\] started " "$out.4" || fail "no activity started with a garbage archive"
echo "PASS cds-dump ($ext): no interrupted archive kept, no unlinked custom-loader class, $n classes from the archive in a launch, a garbage archive is no error"
