#!/bin/sh
# tools/arm32/build.sh [out]: the 32-bit app process's runtime (docs/PLAN.md "apps built for another CPU" 2(a)) in <out>/arm32
# (default build/arm32): the armel meson build of the native parts (bionic_translation, the JNI shim, btload, the path layer, the host HALs) under
# lib/apklayer, the armel JRE (jvm/) on its own glibc (sysroot/lib: the closure of what they link), bin/java (that JVM on that loader) and
# lib/apklayer/native/shim.jar (the shim where Robolectric's loader looks for it). Put the arm32 Android libraries (tools/stage arm32) in
# <out>/arm32/android-libs first: the library mapping is generated against them. On the Mac it builds the image (tools/arm32/Dockerfile)
# and runs itself inside (its build directory is the docker volume apklayer-arm32-b: a bind mount's clock skew stops meson); the result goes to the target as APKLAYER_ARM32 (default <native>/arm32).
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(dirname "$(dirname "$here")")"
if [ "${APKLAYER_IN_ARM32:-}" != 1 ]; then
    out="$(mkdir -p "${1:-$repo/build}" && cd "${1:-$repo/build}" && pwd)"
    docker build -q -t apklayer-arm32-build "$here" >/dev/null
    exec docker run --rm -e APKLAYER_IN_ARM32=1 -v "$repo:/src" -v "$out:/out" -v apklayer-arm32-b:/b apklayer-arm32-build sh /src/tools/arm32/build.sh
fi
a=/out/arm32
mkdir -p "$a"
libs=""; [ ! -d "$a/android-libs" ] || libs="-Dandroid_libs=$a/android-libs"
re=""; [ ! -f /b/build.ninja ] || re=--reconfigure
meson setup $re /b /src --cross-file /src/tools/arm32/armel.ini --prefix "$a" -Dandroid_runtime=false $libs
ninja -C /b
meson install -C /b --skip-subprojects --quiet
n="$a/lib/apklayer/native"
python3 - "$n/shim.jar" /b/bridge/native/libandroid_runtime.so <<'EOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'w') as z:
    for d in ('native/', 'native/linux/', 'native/linux/arm/'): z.writestr(d, '')
    z.write(sys.argv[2], 'native/linux/arm/libandroid_runtime.so')
EOF
rm -rf "$a/jvm" && cp -rL /usr/lib/jvm/java-21-openjdk-armel "$a/jvm"
ln -sf ../jvm/lib/libjsig.so "$a/lib/libjsig.so"  # where hostJvmArgs looks for it (<java>/../lib)
# the glibc side: the loader and every library the armel ELF files need, recursively, from the multiarch directories
s="$a/sysroot/lib"
rm -rf "$a/sysroot" && mkdir -p "$s"
cp -L /lib/ld-linux.so.3 /usr/lib/arm-linux-gnueabi/libgcc_s.so.1 "$s/"
queue="$(find "$a/lib" "$a/jvm" -type f \( -name '*.so*' -o -perm -u+x \) | sort)"
while [ -n "$queue" ]; do
    next=""
    for f in $queue; do
        for need in $(readelf -d "$f" 2>/dev/null | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p'); do
            [ -e "$s/$need" ] && continue
            for d in /usr/lib/arm-linux-gnueabi /lib/arm-linux-gnueabi; do
                [ -e "$d/$need" ] && { cp -L "$d/$need" "$s/"; next="$next $s/$need"; break; }
            done
        done
    done
    queue="$next"
done
mkdir -p "$a/bin"
cat > "$a/bin/java" <<'EOF'
#!/bin/sh
# the armel JVM on its own loader and glibc (tools/arm32/build.sh): the host has no /lib/ld-linux.so.3
# (shell expansions only: a 64-bit tool run here would see LD_LIBRARY_PATH's armel libraries)
r="${0%/bin/java}"
# the preloads (armel libraries) come as APKLAYER_ARM32_PRELOAD (cli/run.cpp) and go to the loader's --preload: in LD_PRELOAD the 64-bit
# programs on the way and those the JVM runs would refuse them, noisily. Processes start by vfork: the JDK's jspawnhelper is armel too
# and names a loader the host lacks
p="${APKLAYER_ARM32_PRELOAD:-${LD_PRELOAD:-}}"; unset LD_PRELOAD APKLAYER_ARM32_PRELOAD
exec "$r/sysroot/lib/ld-linux.so.3" --library-path "${LD_LIBRARY_PATH:+$LD_LIBRARY_PATH:}$r/sysroot/lib" ${p:+--preload "$p"} "$r/jvm/bin/java" -Djdk.lang.Process.launchMechanism=VFORK "$@"
EOF
chmod +x "$a/bin/java"
echo "arm32 runtime: $(du -sh "$a" | cut -f1) in $a ($(ls "$s" | wc -l) glibc-side libraries)"
