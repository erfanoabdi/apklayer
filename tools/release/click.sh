#!/bin/sh
# click.sh <version> <prebuilts dir> <out dir> [<deb>]: the Ubuntu Touch 24.04 click (arm64) of apklayer, <out>/apklayer.erfanoabdi_<version>_arm64.click
# (docs/RELEASES.md "The click", docs/history/PLAN-2026-11-10.md "Plan: Ubuntu Touch click package"). Its tree is the noble deb's (tools/release/build-deb.sh, built
# here unless <deb> names one) unpacked as it is, plus the runtime's own JDK image (jdk-image.sh: patched java.base, CDS archive; JDK 25 when the machine has one), which the CLI
# takes before any other Java (cli/env.cpp findJava). The manager is the click's app; unconfined (click/apklayer.apparmor). Run on noble arm64 with
# the deb's Build-Depends and `click` installed.
set -eu
[ $# -ge 3 ] || { sed -n '2,6p' "$0" >&2; exit 2; }
version="$1" P="$(cd "$2" && pwd)" out="$(mkdir -p "$3" && cd "$3" && pwd)" deb="${4:-}"
[ -z "$deb" ] || deb="$(realpath "$deb")"
cd "$(dirname "$0")/../.."
if [ -z "$deb" ]; then
    sh tools/release/build-deb.sh noble "$version" "$P" "$out/deb"
    deb="$(ls "$out"/deb/apklayer_"$version"-1~noble1_arm64.deb)"
fi
root="$(mktemp -d)" && trap 'rm -rf "$root"' EXIT
dpkg-deb -x "$deb" "$root"
# the deb's root-side files have no use in a click (no postinst runs): the sandbox's AppArmor profile and udev rule, the units, /etc
rm -rf "$root/etc" "$root/usr/lib/udev" "$root/usr/lib/systemd" "$root/usr/share/apklayer/apparmor" "$root/usr/share/doc"
# the image's JDK: JDK 25 when this machine has one (APKLAYER_CLICK_JAVA, or a java-25/temurin-25 under /usr/lib/jvm; the AOT cache, cli/env.cpp cdsArgs)
java="${APKLAYER_CLICK_JAVA:-$(ls /usr/lib/jvm/java-25-openjdk-arm64/bin/java /usr/lib/jvm/temurin-25-jdk/bin/java 2>/dev/null | head -1)}"
sh tools/release/jdk-image.sh "$java" "$root/usr/lib/apklayer/native"
[ -f "$root/usr/lib/apklayer/native/jdk/apklayer-stamp" ] || { echo "click.sh: no JDK image (needs a JDK 25: $java)" >&2; exit 1; }
# the manager's entry, paths relative to the click (lomiri-app-launch runs it from there)
sed -e 's#^Exec=.*#Exec=usr/bin/apklayer-manager#' -e 's#^Icon=.*#Icon=usr/share/icons/hicolor/scalable/apps/apklayer.svg#' -e '/^MimeType=/d' \
    data/apklayer-manager.desktop > "$root/apklayer.desktop"
# the "share" app (docs/HALIUM.md "Choosing and sharing files"): Share / Open with in other apps starts it (the manifest's content-hub hook names its peer file); no NoDisplay: lomiri-app-launch refuses to start such an app ("not meant to be displayed"), so it has its own drawer entry
sed -e 's#^Exec=.*#Exec=usr/bin/apklayer-manager --share#' -e 's#^Icon=.*#Icon=usr/share/icons/hicolor/scalable/apps/apklayer.svg#' -e '/^NoDisplay=/d' data/apklayer-share.desktop > "$root/apklayer-share.desktop"
cp data/content-hub-peer.json "$root/content-hub-peer.json"
cp click/apklayer.apparmor "$root/"
sed "s/@VERSION@/$version/" click/manifest.json > "$root/manifest.json"
chmod 755 "$root"
(cd "$out" && click build --no-validate "$root")
ls -l "$out/apklayer.erfanoabdi_${version}_arm64.click"
