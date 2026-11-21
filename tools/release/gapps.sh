#!/bin/sh
# gapps.sh <Google apps zip> <out dir>: the parts of a Google apps zip apklayer uses, for `apklayer setup --gapps` (cli/gapps.cpp): <out>/apks/*.apk
# (system_ext and product priv-app/app) and <out>/etc/{permissions,sysconfig,default-permissions}/*.xml (both partitions in one, where
# HostSystemConfig reads them through APKLAYER_EXTRA_ETC: not copied into <android-root>/system/etc, which a package build serves read-only).
# Layouts: apklayer's gapps release (the gapps branch packed, data/downloads.json) and MindTheGapps (the zip holds system/{product,system_ext}/...; arm64 ships GmsCore inside the gmssystem APEX, an EROFS image that
# fsck.erofs from erofs-utils unpacks) and LiteGapps (files/files.tar.xz holds <cpu>/<sdk>/system/...).
# Left out: the Pixel feature lists (pixel_experience_*: Pixel-only features) and the dialer support library (its jar is not installed).
set -eu
[ $# -eq 2 ] || { sed -n '2,6p' "$0" >&2; exit 2; }
zip="$1" out="$2"
rm -rf "$out" && mkdir -p "$out/x" "$out/apks" "$out/etc"
if unzip -l "$zip" files/files.tar.xz >/dev/null 2>&1; then  # LiteGapps
    unzip -p "$zip" files/files.tar.xz | tar -xJf - -C "$out/x"
    sys="$(find "$out/x" -mindepth 3 -maxdepth 3 -type d -name system | head -1)"
else  # apklayer's gapps zip, MindTheGapps
    unzip -q "$zip" 'system/*' -d "$out/x"
    sys="$out/x/system"
fi
[ -n "$sys" ] && [ -d "$sys" ] || { echo "apklayer: $zip has no system tree (neither MindTheGapps nor LiteGapps)" >&2; exit 1; }
find "$sys" -name '*.apk' -path '*app/*' ! -name '*-splitconfig-*' -exec cp {} "$out/apks/" \;
# Play services as an APEX (MindTheGapps arm64: product/apex/com.google.android.gmssystem*.apex): its APK from the payload image
if [ ! -f "$out/apks/GmsCore.apk" ]; then
    for apex in "$sys"/product/apex/com.google.android.gmssystem*.apex; do
        [ -f "$apex" ] || continue
        command -v fsck.erofs >/dev/null || { echo "apklayer: $(basename "$apex") holds Play services: install erofs-utils (fsck.erofs) to unpack it" >&2; exit 1; }
        mkdir -p "$out/x/apex"
        unzip -p "$apex" apex_payload.img > "$out/x/apex/payload.img"
        fsck.erofs --extract="$out/x/apex/root" "$out/x/apex/payload.img" >/dev/null
        gms="$(find "$out/x/apex/root" -name '*.apk' -path '*priv-app/*' | grep -i -E '/(GmsCore|PrebuiltGmsCore)[^/]*\.apk$' | head -1)"
        [ -n "$gms" ] && cp "$gms" "$out/apks/GmsCore.apk"
    done
fi
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
