#!/bin/sh
# Runs one of the bpftrace scripts here against the installed translator: its @BRIDGE@ becomes the libberberis_arm64.so the runtime
# uses (runtime/current/android-libs, links followed) of the user who ran sudo. Run as root:
#   sudo tools/ace/trace.sh <script.bt> [output file]     (then start the app; Ctrl+C when done)
# Strings up to 128 bytes (BPF's 512-byte stack); --unsafe for scripts that call system().
set -eu
[ $# -ge 1 ] || { echo "usage: sudo $0 <script.bt> [output file]" >&2; exit 2; }
script=$1
[ -f "$script" ] || script=$(dirname "$0")/$1
home=$(getent passwd "${SUDO_USER:-$(id -un)}" | cut -d: -f6)
bridge=$(readlink -f "$home/.local/share/apklayer/runtime/current/android-libs/libberberis_arm64.so")
[ -f "$bridge" ] || { echo "no libberberis_arm64.so under $home/.local/share/apklayer/runtime/current/android-libs" >&2; exit 1; }
tmp=$(mktemp --suffix=.bt)
trap 'rm -f "$tmp"' EXIT
sed "s|@BRIDGE@|$bridge|g" "$script" > "$tmp"
unsafe=; grep -q 'system(' "$tmp" && unsafe=--unsafe
echo "translator: $bridge" >&2
if [ $# -ge 2 ]; then
  BPFTRACE_MAX_STRLEN=128 bpftrace $unsafe -o "$2" "$tmp"
else
  BPFTRACE_MAX_STRLEN=128 bpftrace $unsafe "$tmp"
fi
