#!/usr/bin/env bash
# Build the host against the staged tree's actual Robolectric API, not Maven's.
set -euo pipefail
cd "$(dirname "$0")/.."
native="$(realpath "$1")"
work="$(mktemp -d "$native/aosp/.host.XXXXXX")"
trap 'rm -rf "$work"' EXIT
cp -R runtime/host/java "$work/src"
# apklayer patch: older tree ShadowProvider uses reset(), Maven uses reset(ClassTracker).
if ! javap -classpath "$native/aosp/Robolectric_all.jar" org.robolectric.internal.ShadowProvider | grep -q 'reset(org.robolectric.internal.ClassTracker)'; then
    sed -i '/import org.robolectric.internal.ClassTracker;/d;s/reset(ClassTracker classTracker)/reset()/' \
        "$work/src/apklayer/host/HostShadowProvider.java"
fi
mkdir "$work/classes"
# The tree simulator predates the FrameListener split. Reuse these two unchanged
# classes from the already pinned simulator artifact (also used by the CPU flavor).
simulator="${APKLAYER_DEPS:-$HOME/.cache/apklayer/deps}/libs/simulator-4.17.jar"
(cd "$work/classes" && jar xf "$simulator" org/robolectric/simulator/FrameListener.class org/robolectric/simulator/ScreenUpdateHandler.class)
mapfile -t sources < <(find "$work/src" -name '*.java' ! -name '._*' -print | sort)
echo "Compiling ${#sources[@]} tree host sources"
javac -nowarn -d "$work/classes" \
    -cp "$work/classes:$native/aosp/sim-classes:$native/aosp/Robolectric_all.jar:$native/aosp/android-all/*:b/runtime/dex2jvm/dex2jvm.jar" "${sources[@]}"
cp -R runtime/host/resources/. "$work/classes/"
# Call only while app JVMs are stopped; preserve the previous build on compiler failure.
[ ! -d "$native/aosp/host-classes" ] || mv "$native/aosp/host-classes" "$work/previous"
mv "$work/classes" "$native/aosp/host-classes"
echo "Published $native/aosp/host-classes"
# java.base members Android's native libraries look up (apklayer.host.JdkPatch): the JVM runs with --patch-module java.base=<native>/jdk-patch (bin/apklayer)
rm -rf "$native/jdk-patch"
java -cp "$native/aosp/host-classes:$native/aosp/Robolectric_all.jar" apklayer.host.JdkPatch "$native/jdk-patch"
