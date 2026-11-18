#!/bin/sh
# setup-runtime.sh <bundle> <package native dir>: the installed CLI (cli/layout.cpp) runs it on first use and after a package update, under a lock.
# The deb has no Android half (meson -Dandroid_runtime=false, docs/RELEASES.md): the unpacked Android runtime bundle becomes the native dir with
# the package's half linked in, the package's JNI shim and its libcore overlay classes (dalvik/system and com/android/org/conscrypt, as runtime/meson.build's framework-patched.jar) put into the framework jars, and the framework instrumented once.
set -eu
# half as named (not resolved): the click's is under its <app>/current link, so the links below outlive an update (cli/layout.cpp selfPath)
native="$(realpath "$1")" half="$(cd "$2" && pwd)" share="$(cd "$(dirname "$0")/.." && pwd)"
# a wrong half (a CLI built for another layout resolved it to /) would link every entry of it into the runtime
[ -f "$half/native-runtime.jar" ] || { echo "apklayer: $half is not apklayer's native dir (no native-runtime.jar); runtime not set up" >&2; exit 1; }
# a runtime another build linked into (its native-runtime.jar link still resolves) is not ours to relink: a dev tree's CLI would leave it dangling once the tree is deleted
owner="$(readlink "$native/native-runtime.jar" 2>/dev/null || true)"
if [ -n "$owner" ] && [ -e "$owner" ] && [ "$(dirname "$owner")" != "$half" ]; then
    echo "apklayer: the runtime $native is set up by $(dirname "$owner"), not by $half; not relinking it (APKLAYER_NATIVE=<own copy of the runtime> for this build)" >&2; exit 1
fi
exec 9>"$native/.setup.lock" && flock 9
host_classes_changed=0
[ ! -f "$half/aosp/host-classes.jar" ] || [ ! "$half/aosp/host-classes.jar" -nt "$native/aosp/host-classes.jar" ] || host_classes_changed=1
[ "$(stat -c %Y "$native/.setup" 2>/dev/null)" != "$(stat -c %Y "$half/native-runtime.jar")" ] || [ "$host_classes_changed" = 1 ] || exit 0  # another CLI did it meanwhile
echo "apklayer: setting up the Android runtime in $native (after each update, a few minutes)" >&2
chmod 755 "$native"  # unpacked as 0700 before 0.4.6: sandboxed apps (other uids) could not read the runtime
for f in "$half"/* "$half"/aosp/*; do  # links to the package's files, so an update reaches them; the bundle's own files stay
    t="$native/${f#"$half"/}"
    if [ "$f" = "$half/aosp/host-classes.jar" ] && [ -f "$t" ] && [ ! -L "$t" ] && [ "$f" -nt "$t" ]; then
        # Keep package host classes (NativeLoader, etc.) in sync with the CLI; replace atomically because live JVMs may have the old jar mapped.
        tmpfile="$(mktemp "$native/aosp/.host-classes.XXXXXX")"
        if cp -- "$f" "$tmpfile" && chmod --reference="$f" "$tmpfile" && mv -f -- "$tmpfile" "$t"; then :; else rm -f -- "$tmpfile"; exit 1; fi
    else
        [ "$f" = "$half/aosp" ] || { [ -e "$t" ] && [ ! -L "$t" ]; } || ln -sfn "$f" "$t"
    fi
done
tar -C "$share/bridge/native/android-root" -cf - . | tar -C "$native/android-root" -xf -  # apklayer's own files of the Android root, replacing (busybox cp has no --remove-destination)
for d in "$half"/android-root/apex/*; do  # the package build's apex dirs the bundle lacks: com.android.art (libart.so: the JNI tables), com.android.runtime (bionic, linker); linked, so their own links resolve
    t="$native/android-root/apex/${d##*/}"
    [ ! -d "$d" ] || { [ -e "$t" ] && [ ! -L "$t" ]; } || ln -sfn "$d" "$t"
done
# the package build's libjavacore link points into its build root (/cache/prebuilts/...): the art apex dir becomes a real one, its entries linked, libjavacore from the bundle
art="$native/android-root/apex/com.android.art"
if [ -L "$art" ] && [ ! -e "$art/lib64/libjavacore.so" ] && [ -e "$native/android-libs/libjavacore.so" ]; then
    src="$(readlink "$art")" && rm "$art" && mkdir -p "$art/lib64" && for f in "$src"/lib64/*; do ln -sfn "$f" "$art/lib64/"; done
    ln -sfn ../../../../android-libs/libjavacore.so "$art/lib64/libjavacore.so"  # relative: the runtime dir may move
fi
tmp="$(mktemp -d)" && trap 'rm -rf "$tmp"' EXIT
echo "apklayer: 1/3 patching the framework jars" >&2
patch_jar() {
    j="$1"
    if command -v zip >/dev/null; then zip -q -r -O "$j.new" "$j" dalvik com native/linux/x86_64/libandroid_runtime.so native/linux/aarch64/libandroid_runtime.so
    else cp "$j" "$j.new" && "$(dirname "$(realpath "$(command -v "${APKLAYER_JAVA:-java}")")")/jar" uf "$j.new" native/linux dalvik com; fi  # no zip (Ubuntu Touch): the click's JDK has jar
    mv -f "$j.new" "$j"
    # the unpatched NativeCryptoJni (371 bytes) fails to load Conscrypt: every WebView https load fails (the 0.4.18 bundle)
    c=com/android/org/conscrypt/NativeCryptoJni.class
    [ "$(unzip -p "$j" $c | wc -c)" = "$(wc -c < $c)" ] || { echo "apklayer: $j lacks the patched $c" >&2; exit 1; }
}
# one job per jar (independent files); the first failure fails the script
(cd "$tmp" && unzip -q "$half/native-runtime.jar" 'native/linux/*' 'dalvik/system/*' 'com/android/org/conscrypt/*' && pids="" &&
    for j in "$native"/aosp/android-all/*.jar; do patch_jar "$j" & pids="$pids $!"; done && for p in $pids; do wait "$p" || exit 1; done)
# the framework jars instrumented once (apklayer.tools.PreInstrument), which app JVMs take while they are newer than their inputs (cli/env.cpp)
echo "apklayer: 2/3 instrumenting the framework and building the JDK image (in parallel)" >&2
{ sh "$share/tools/jdk-image.sh" "${APKLAYER_JAVA:-java}" "$native" || echo "apklayer: no JDK image (class data sharing) for the runtime" >&2; } &  # independent of the instrumenting
jdk_pid=$!
out="$native/aosp/android-all-instrumented" done_jar=""
rm -rf "$out" && mkdir -p "$out"
entries() { unzip -lv "$1" | awk 'NR > 3 { print $1, $7, $8 }'; }  # size, CRC and name of every entry
for j in "$native"/aosp/android-all/*.jar; do  # the SDK provider names the jar twice (16 and current): the same entries, instrumented once
    if [ -n "$done_jar" ] && [ "$(entries "$j")" = "$(entries "$done_jar")" ]; then ln "$out/$(basename "$done_jar")" "$out/$(basename "$j")"; continue; fi
    "${APKLAYER_JAVA:-java}" -cp "$native/native-runtime.jar:$native/aosp/host-classes.jar:$native/aosp/sim-classes:$native/aosp/Robolectric_all.jar" apklayer.tools.PreInstrument "$j" "$out/$(basename "$j")"
    done_jar="$j"
done
wait "$jdk_pid"
echo "apklayer: 3/3 done" >&2
touch -r "$half/native-runtime.jar" "$native/.setup"
