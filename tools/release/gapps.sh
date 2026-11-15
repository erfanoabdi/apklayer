#!/bin/sh
# gapps.sh <LiteGapps zip> <out dir>: the parts of a LiteGapps "lite" zip (Magisk/recovery module: files/files.tar.xz holds <cpu>/<sdk>/system/...)
# apklayer uses, for `apklayer setup --gapps` (cli/gapps.cpp): <out>/apks/*.apk (system_ext and product priv-app/app) and <out>/etc/{permissions,
# sysconfig,default-permissions}/*.xml (both partitions in one, as HostSystemConfig reads <android-root>/system/etc). Left out: the Pixel feature
# lists (pixel_experience_*: Pixel-only features) and the dialer support library (its jar is not installed).
set -eu
[ $# -eq 2 ] || { sed -n '2,5p' "$0" >&2; exit 2; }
zip="$1" out="$2"
rm -rf "$out" && mkdir -p "$out/x" "$out/apks" "$out/etc"
unzip -p "$zip" files/files.tar.xz | tar -xJf - -C "$out/x"
sys="$(find "$out/x" -mindepth 3 -maxdepth 3 -type d -name system | head -1)"
[ -n "$sys" ] || { echo "apklayer: $zip has no <cpu>/<sdk>/system tree" >&2; exit 1; }
find "$sys" -name '*.apk' -path '*app/*' -exec cp {} "$out/apks/" \;
for part in system_ext product; do
    for d in permissions sysconfig default-permissions; do
        [ -d "$sys/$part/etc/$d" ] || continue
        mkdir -p "$out/etc/$d"
        for f in "$sys/$part/etc/$d"/*.xml; do
            case "$(basename "$f")" in pixel_experience_*|com.google.android.dialer.support.xml) ;; *) cp "$f" "$out/etc/$d/" ;; esac
        done
    done
done
rm -rf "$out/x"
