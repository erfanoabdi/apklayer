#!/bin/sh
# ndk-stubs: a weak do-nothing C function for every function the real device library exports (bridge/ndk/<name>.c defines the ones that matter).
# Usage: ndk-stubs.sh <real library.so> <out.c>   (run by bridge/native/meson.build)
set -e
nm -D --defined-only "$1" | awk '$2 ~ /[TW]/ {sub(/@.*/, "", $3); print "__attribute__((weak)) long " $3 "(void) { return 0; }"}' | sort -u > "$2"
