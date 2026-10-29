#!/bin/sh
# collect-android-libs: copy libraries and everything they need (DT_NEEDED, recursively) from the AOSP device build
# (default ~/los24/out/target/product/generic_arm64/system/lib64) to a directory, skipping what is already there. The
# arm64 native runtime's library set (PLAN P7) grows with each Android process we run (framework, servicemanager, SF).
# Usage: collect-android-libs.sh DEST lib.so... [-- output to stdout is the list of copied files]
set -e
# ANDROID_LIBS_SRC: one directory, or several separated by ":" (the first that has a library wins: system/lib64, then the APEX and module directories)
src="${ANDROID_LIBS_SRC:-$HOME/los24/out/target/product/generic_arm64/system/lib64}"
find_lib() { # find_lib name -> the path of the library in the first source directory that has it
    oldifs="$IFS"; IFS=:
    for d in $src; do [ -e "$d/$1" ] && { IFS="$oldifs"; echo "$d/$1"; return 0; }; done
    IFS="$oldifs"; return 1
}
dest="$1"; shift
mkdir -p "$dest"
queue="$*"
while [ -n "$queue" ]; do
    set -- $queue; lib="$1"; shift; queue="$*"
    case "$lib" in libc.so|libm.so|libdl.so|libdl_android.so) continue;; esac  # bionic_translation provides these
    [ -e "$dest/$lib" ] && continue
    found="$(find_lib "$lib")" || { echo "missing in $src: $lib" >&2; continue; }
    cp "$found" "$dest/$lib"; echo "$lib"
    queue="$queue $(readelf -d "$found" | sed -n 's/.*NEEDED.*\[\(.*\)\]/\1/p' | tr '\n' ' ')"
done
