# native-env: the environment of every process that runs Android's own libraries (the device-library route, PLAN P7). Sourced by
# scripts/arm64-stack.sh (the services) and by bin/apklayer (the app JVM), so both see the same instance, properties and library paths.
# Overridable: APKLAYER_NATIVE (the native dir, default ~/arm64-native), APKLAYER_ANDROID_LIBS (the tree's arm64 libraries, default
# ~/android-arm64/lib64), APKLAYER_BT (bionic_translation's build dir), APKLAYER_INSTANCE (default "default").
N="${APKLAYER_NATIVE:-$HOME/arm64-native}"
L="${APKLAYER_ANDROID_LIBS:-$HOME/android-arm64/lib64}"
BT="${APKLAYER_BT:-$HOME/bt-arm/bionic_translation/b}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"
export APKLAYER_INSTANCE="${APKLAYER_INSTANCE:-default}" APKLAYER_NATIVE="$N"
# bionic_translation's libraries first (the patched ones), then ours; the path layer and bionic's TLS slots come first in every lookup
export LD_LIBRARY_PATH="$BT:$N" XDG_DATA_DIRS="$N/share" LD_PRELOAD="$N/libapklayer-preload.so:libgcc_s.so.1" BIONIC_LD_LIBRARY_PATH="$L"
export APKLAYER_CRASH_TRACE="${APKLAYER_CRASH_TRACE:-1}" APKLAYER_HOST_LIBS="${APKLAYER_HOST_LIBS:-$N/libapklayer-hal-graphics.so}"

# The arm64 JVM for the app (the Rosetta x86 JVM of the older flavors cannot load arm64 libraries).
native_java() {
    local j
    for j in "${APKLAYER_NATIVE_JAVA:-}" /usr/lib/jvm/java-21-openjdk-arm64/bin/java /usr/lib/jvm/*arm64*/bin/java; do
        [ -n "$j" ] && [ -x "$j" ] && { echo "$j"; return; }
    done
    command -v java
}
