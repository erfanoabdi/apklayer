#!/bin/sh
# click.sh <version> <prebuilts dir> <out dir> [<deb>]: the Ubuntu Touch 24.04 click (arm64) of apklayer, <out>/apklayer.erfanoabdi_<version>_arm64.click
# (docs/RELEASES.md "The click", docs/PLAN.md "Plan: Ubuntu Touch click package"). Its tree is the noble deb's (tools/release/build-deb.sh, built
# here unless <deb> names one) unpacked as it is, plus the runtime's own JDK image (jdk-image.sh: patched java.base, CDS archive), which the CLI
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
java="${APKLAYER_CLICK_JAVA:-/usr/lib/jvm/java-21-openjdk-arm64/bin/java}"
sh tools/release/jdk-image.sh "$java" "$root/usr/lib/apklayer/native"
[ -f "$root/usr/lib/apklayer/native/jdk/apklayer-stamp" ] || { echo "click.sh: no JDK image (needs a JDK 21 with jmods: $java)" >&2; exit 1; }
# the manager's entry, paths relative to the click (lomiri-app-launch runs it from there)
sed -e 's#^Exec=.*#Exec=usr/bin/apklayer-manager#' -e 's#^Icon=.*#Icon=usr/share/icons/hicolor/scalable/apps/apklayer.svg#' \
    data/apklayer-manager.desktop > "$root/apklayer.desktop"
cp click/apklayer.apparmor "$root/"
sed "s/@VERSION@/$version/" click/manifest.json > "$root/manifest.json"
chmod 755 "$root"
(cd "$out" && click build --no-validate "$root")
ls -l "$out/apklayer.erfanoabdi_${version}_arm64.click"
