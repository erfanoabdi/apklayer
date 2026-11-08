#!/bin/sh
# build.sh <prebuilts> <out dir> [version]: the apklayer Flatpak of this machine's CPU, as a single-file bundle
# <out>/apklayer-<version>-<arch>.flatpak (docs/INSTALL.md "Flatpak"). <prebuilts>: a checkout of the prebuilts branch (joined here when split).
# Needs flatpak and flatpak-builder; the runtime, SDK and openjdk21 extension come from Flathub (--user).
set -eu
here=$(cd "$(dirname "$0")" && pwd)
P=$(cd "$1" && pwd)
mkdir -p "$2"
OUT=$(cd "$2" && pwd)
V=${3:-dev}
[ -z "$(find "$P" -name '*.part000' | head -1)" ] || sh "$P/join-parts.sh" "$P"
ln -sfn "$P" "$here/prebuilts"  # the manifest's prebuilts source
flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak-builder --user --install-deps-from=flathub --force-clean --disable-rofiles-fuse --state-dir="$OUT/state" --repo="$OUT/repo" \
    "$OUT/build" "$here/io.github.erfanoabdi.Apklayer.yml"
flatpak build-bundle --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo "$OUT/repo" \
    "$OUT/apklayer-$V-$(flatpak --default-arch).flatpak" io.github.erfanoabdi.Apklayer
rm -rf "$OUT/build"
