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
export LD_LIBRARY_PATH="$BT:$N" XDG_DATA_DIRS="$N/share:${XDG_DATA_DIRS:-/usr/local/share:/usr/share}" LD_PRELOAD="$N/libapklayer-preload.so:libgcc_s.so.1" BIONIC_LD_LIBRARY_PATH="$L"
export APKLAYER_CRASH_TRACE="${APKLAYER_CRASH_TRACE:-1}" APKLAYER_HOST_LIBS="${APKLAYER_HOST_LIBS:-$N/libapklayer-hal-graphics.so}"

# The arm64 JVM for the app (the Rosetta x86 JVM of the older flavors cannot load arm64 libraries).
native_java() {
    local j
    for j in "${APKLAYER_NATIVE_JAVA:-}" /usr/lib/jvm/java-21-openjdk-arm64/bin/java /usr/lib/jvm/*arm64*/bin/java; do
        [ -n "$j" ] && [ -x "$j" ] && { echo "$j"; return; }
    done
    command -v java
}

# system_server services remain in the JVM; native clients use their existing unavailable-service fallbacks.
export APKLAYER_NO_SYSTEM_SERVER="${APKLAYER_NO_SYSTEM_SERVER:-1}"

# apklayer patch: Android and host Vulkan loaders retain separate dispatch tables.
# The optional frontend is installed only after its native readback regression.
if [ -f "$L/libapklayer_vulkan.so" ] && [ -f "$L/vulkan.apklayer.so" ]; then
    export APKLAYER_VULKAN_HOST=1
fi

# apklayer patch: native OpenSL ES clients need the existing Android audio
# services and PipeWire backend, including when launched from the desktop menu.
if [ -f "$L/libOpenSLES.so" ] && [ -f "$L/libapklayer_audioserver.so" ] && [ -f "$N/libapklayer-hal-audio.so" ]; then
    export APKLAYER_AUDIO="${APKLAYER_AUDIO:-1}"
    export APKLAYER_AUDIO_SERVICES="${APKLAYER_AUDIO_SERVICES:-$APKLAYER_AUDIO}"
    if [ "$APKLAYER_AUDIO" = 1 ]; then
        case ":$APKLAYER_HOST_LIBS:" in
            *":$N/libapklayer-hal-audio.so:"*) ;;
            *) export APKLAYER_HOST_LIBS="$APKLAYER_HOST_LIBS:$N/libapklayer-hal-audio.so" ;;
        esac
    fi
fi

# apklayer patch: Android apps ask for the GLES level a device gives (3.2; Off The Road 2 needs 3.1). A host driver that stops lower (virgl: ES 3.0)
# makes eglCreateContext fail with EGL_BAD_MATCH. The driver's own version override then claims the Android level: what the driver truly lacks
# (compute shaders...) fails when used, as on a device without it. Probed once per session; APKLAYER_GLES_OVERRIDE=off disables it, or set
# MESA_GLES_VERSION_OVERRIDE yourself.
if [ -z "${MESA_GLES_VERSION_OVERRIDE:-}" ] && [ "${APKLAYER_GLES_OVERRIDE:-auto}" != off ] && command -v eglinfo >/dev/null 2>&1; then
    _es_cache="$XDG_RUNTIME_DIR/apklayer-host-gles"
    if [ -s "$_es_cache" ]; then
        _es="$(cat "$_es_cache")"
    else
        _es="$(eglinfo -p gbm 2>/dev/null | sed -n 's/^OpenGL ES profile version: OpenGL ES \([0-9.]*\).*/\1/p' | head -1)"
        [ -z "$_es" ] || echo "$_es" > "$_es_cache"
        case "$_es" in 2.*|3.0) echo "apklayer: the host GLES is $_es; apps get a 3.2 claim (APKLAYER_GLES_OVERRIDE=off to disable)" >&2 ;; esac
    fi
    case "$_es" in 2.*|3.0) export MESA_GLES_VERSION_OVERRIDE=3.2 ;; esac
    unset _es _es_cache
fi
