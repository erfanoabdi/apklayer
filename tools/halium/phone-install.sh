#!/bin/bash
# phone-install.sh <ut|sfos> <dir>: installs a build on an owner's phone from the Mac over the LAN (docs/HALIUM.md "The phone route"). <dir> holds the
# package (ut: apklayer.erfanoabdi_<version>_arm64.click from tools/release/click.sh; sfos: apklayer-<version>-1.aarch64.rpm from sailfish-rpm.sh, and
# any other .rpm of that run the phone lacks) and the runtime bundle (apklayer-runtime-arm64-*.tar.zst, tools/release/runtime-bundle.sh).
# ut: `gdbus ... com.lomiri.click.Install` (no root), then `apklayer setup --bundle`. sfos: `rpm -Uvh --replacepkgs` as root, then `apklayer setup
# --update --bundle` as defaultuser (without --update a phone that has a runtime keeps it) (the bundle on /userdata: the Quintus' home partition is small). The copies on the phone are removed at the end.
set -eu
os=${1:-}; dir=${2:-}
[ -d "$dir" ] || { sed -n '2,7p' "$0" >&2; exit 2; }
repo=$(cd "$(dirname "$0")/../.." && pwd)
get() { sed -n "s/^$1=//p" "$repo/tests/vm/local.conf" | head -1; }
bundle=$(ls "$dir"/apklayer-runtime-arm64-*.tar.zst | head -1)
case $os in
ut)
    host=$(get UBPORTS); R=/home/${host%@*}/apklayer-phone-install; A=/opt/click.ubuntu.com/apklayer.erfanoabdi/current/usr/bin/apklayer
    click=$(ls "$dir"/apklayer.erfanoabdi_*_arm64.click | head -1)
    ssh "$host" "mkdir -p $R"; scp -q "$click" "$bundle" "$host:$R/"
    ssh "$host" "gdbus call --system -d com.lomiri.click -o /com/lomiri/click -m com.lomiri.click.Install $R/$(basename "$click") && $A setup --bundle $R/$(basename "$bundle") | grep '^step' | cut -c1-160" ;;
sfos)
    host=$(get SAILFISH); R=/userdata/apklayer/phone-install-${host%@*}; root=root@${host#*@}
    ssh "$host" "mkdir -p $R"; scp -q "$dir"/*.rpm "$bundle" "$host:$R/"
    ssh "$root" "cd $R && rpm -Uvh --replacepkgs *.rpm 2>&1 | tail -4"
    ssh "$host" "apklayer setup --update --bundle $R/$(basename "$bundle") | grep '^step' | cut -c1-160" ;;
*) echo "phone-install: ut or sfos" >&2; exit 2 ;;
esac
ssh "$host" "rm -rf $R"
echo "phone-install $os: done ($(ssh "$host" "$([ $os = ut ] && echo $A || echo apklayer) --version | head -1"))"
