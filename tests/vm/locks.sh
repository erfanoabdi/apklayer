#!/bin/sh
# locks: lock owner identity, socket reads, GC stack/register visibility and store-load fences of translated arm64 code under a GC-like signal storm
# (tests/apps/hello-arm64/locks.c; PGR's download stall, CHANGELOG 2026-11-21): one "locks PASS" line, every count 0 as on real arm64.
set -eu
out=$(mktemp)
trap 'rm -f "$out"; bin/apklayer stop >/dev/null 2>&1 || true' EXIT
APKLAYER_JOPTS="-Dapklayer.runms=60000 -Dhello.locks=6000" bin/apklayer run hello.arm64 > "$out" 2>&1 || true
line=$(sed -n 's/^.*\[hello-arm64\] locks //p' "$out" | head -1)
echo "locks $line"
case "$line" in PASS*) ;; *) exit 1 ;; esac
