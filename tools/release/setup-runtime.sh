#!/bin/sh
# setup-runtime.sh <bundle> <package native dir>: the installed CLI (cli/layout.cpp) runs it on first use and after a package update, under a lock.
# The deb has no Android half (meson -Dandroid_runtime=false, docs/RELEASES.md): the unpacked Android runtime bundle becomes the native dir with
# the package's half linked in, the package's JNI shim put into the framework jars, and the framework instrumented once.
set -eu
native="$(realpath "$1")" half="$(realpath "$2")" share="$(cd "$(dirname "$0")/.." && pwd)"
exec 9>"$native/.setup.lock" && flock 9
[ "$(stat -c %Y "$native/.setup" 2>/dev/null)" != "$(stat -c %Y "$half/native-runtime.jar")" ] || exit 0  # another CLI did it meanwhile
echo "apklayer: setting up the Android runtime in $native (after each update, a few minutes)" >&2
for f in "$half"/* "$half"/aosp/*; do  # links to the package's files, so an update reaches them; the bundle's own files stay
    t="$native/${f#"$half"/}"
    [ "$f" = "$half/aosp" ] || { [ -e "$t" ] && [ ! -L "$t" ]; } || ln -sfn "$f" "$t"
done
cp -r --remove-destination "$share/bridge/native/android-root/." "$native/android-root/"  # apklayer's own files of the Android root
tmp="$(mktemp -d)" && trap 'rm -rf "$tmp"' EXIT
(cd "$tmp" && unzip -q "$half/native-runtime.jar" 'native/linux/*' && for j in "$native"/aosp/android-all/*.jar; do
    zip -q -O "$j.new" "$j" native/linux/x86_64/libandroid_runtime.so native/linux/aarch64/libandroid_runtime.so && mv -f "$j.new" "$j"
done)
# the framework jars instrumented once (apklayer.tools.PreInstrument), which app JVMs take while they are newer than their inputs (cli/env.cpp)
out="$native/aosp/android-all-instrumented" done_jar=""
rm -rf "$out" && mkdir -p "$out"
entries() { unzip -lv "$1" | awk 'NR > 3 { print $1, $7, $8 }'; }  # size, CRC and name of every entry
for j in "$native"/aosp/android-all/*.jar; do  # the SDK provider names the jar twice (16 and current): the same entries, instrumented once
    if [ -n "$done_jar" ] && [ "$(entries "$j")" = "$(entries "$done_jar")" ]; then ln "$out/$(basename "$done_jar")" "$out/$(basename "$j")"; continue; fi
    java -cp "$native/native-runtime.jar:$native/aosp/host-classes.jar:$native/aosp/sim-classes:$native/aosp/Robolectric_all.jar" apklayer.tools.PreInstrument "$j" "$out/$(basename "$j")"
    done_jar="$j"
done
touch -r "$half/native-runtime.jar" "$native/.setup"
