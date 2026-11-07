#!/bin/sh
# sysroot-bundle.sh <build dir> <out dir>: apklayer for a system whose glibc is older than the build host's (Sailfish OS 5: glibc 2.30; docs/HALIUM.md "apklayer on Sailfish OS"),
# as <out>/apklayer-sysroot-<cpu>-<version>.tar.gz to unpack at / (busybox tar). It holds the build's `meson install` at its prefix (meson setup
# --prefix=/opt/apklayer -Dandroid_runtime=false) and, beside it, the build host's own userland that install needs: <prefix>/jvm (this host's Java 21,
# links resolved) and <prefix>/sysroot/lib (glibc with its loader and every library the install and the JVM link). Every ELF of it is patched to start
# with that loader and to find those libraries first (patchelf: interpreter, DT_RPATH): of the target only the kernel, /vendor, /system and the
# compositor's and PulseAudio's sockets are used. Run it on the build host (Ubuntu 24.04 arm64), patchelf installed.
set -eu
[ $# -eq 2 ] || { sed -n '2,7p' "$0" >&2; exit 2; }
B="$(cd "$1" && pwd)" out="$(mkdir -p "$2" && cd "$2" && pwd)"
prefix="$(meson introspect "$B" --buildoptions | python3 -c 'import json,sys; print(next(o["value"] for o in json.load(sys.stdin) if o["name"] == "prefix"))')"
version="$(sed -n 's/^runtime=//p' "$(dirname "$0")/versions")"
jdk="${APKLAYER_BUNDLE_JDK:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
stage="$(mktemp -d "$out/.stage.XXXXXX")"
trap 'rm -rf "$stage"' EXIT
root="$stage$prefix" lib="$stage$prefix/sysroot/lib"
DESTDIR="$stage" meson install -C "$B" --skip-subprojects --no-rebuild --quiet
cp -a "$jdk" "$root/jvm"
find "$root/jvm" -type l | while read -r l; do  # Ubuntu's JDK links its conf and cacerts into /etc (and src.zip to nothing)
    t="$(readlink -m "$l")"; rm "$l"; [ ! -e "$t" ] || cp -rL "$t" "$l"
done
mkdir -p "$lib"

# glibc ELF files of the bundle: not bionic ones (they need libc.so, bionic_translation loads them)
elfs() { find "$root" -type f ! -name '*.debug' | while read -r f; do
    [ "$(head -c 4 "$f" | od -An -c | tr -d ' ')" = '177ELF' ] || continue
    readelf -d "$f" 2>/dev/null | grep -q 'NEEDED.*\[libc\.so\]' || echo "$f"
done; }
elfs | while read -r f; do ldd "$f" 2>/dev/null || true; done | awk '$2 == "=>" && $3 ~ /^\// { print $3 } $1 ~ /^\/.*ld-linux/ { print $1 }' | sort -u |
    while read -r so; do case "$so" in "$stage"*) ;; *) cp -L "$so" "$lib/" ;; esac; done
# what glibc itself opens at run time (name service, pthread_cancel's unwinder)
for so in libnss_files.so.2 libnss_dns.so.2 libresolv.so.2 libgcc_s.so.1; do cp -L "$(ldconfig -p | awk -v n="$so" '$1 == n { print $NF; exit }')" "$lib/"; done
loader="$(basename "$(ls "$lib"/ld-linux-*.so.* | head -1)")"

elfs | while read -r f; do
    case "$(basename "$f")" in ld-linux-*|libc.so.6|libm.so.6|libpthread.so.0|libdl.so.2|librt.so.1|libresolv.so.2|libnss_*) continue ;; esac  # glibc's own
    if readelf -l "$f" 2>/dev/null | grep -q 'Requesting program interpreter'; then patchelf --set-interpreter "$prefix/sysroot/lib/$loader" "$f"; fi
    rp="$(patchelf --print-rpath "$f")"
    patchelf --force-rpath --set-rpath "${rp:+$rp:}$prefix/sysroot/lib" "$f"   # DT_RPATH: also for the libraries they load
done
mkdir -p "$out"
name="apklayer-sysroot-$(uname -m)-$version"
tar -C "$stage" --owner=0 --group=0 --numeric-owner -czf "$out/$name.tar.gz" "${prefix#/}"
echo "sysroot-bundle: $out/$name.tar.gz ($(du -h "$out/$name.tar.gz" | cut -f1)), prefix $prefix, loader $loader, $(ls "$lib" | wc -l) libraries"
