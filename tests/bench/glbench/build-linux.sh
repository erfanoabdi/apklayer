#!/bin/sh
# Builds the native Linux glbench (linux.c + bench.c) into $1 (default: build/glbench beside this script's repo root).
# Needs: wayland-scanner, wayland-protocols, libwayland-dev, libegl-dev, libgles-dev, libgbm-dev.
set -e
here=$(cd "$(dirname "$0")" && pwd)
out=${1:-$here/../../../build/glbench}
gen=$(mktemp -d)
trap 'rm -rf "$gen"' EXIT
xml=$(pkg-config --variable=pkgdatadir wayland-protocols)/stable/xdg-shell/xdg-shell.xml
wayland-scanner client-header "$xml" "$gen/xdg-shell-client-protocol.h"
wayland-scanner private-code "$xml" "$gen/xdg-shell-protocol.c"
mkdir -p "$(dirname "$out")"
cc -O2 -Wall -I"$gen" -I"$here" -o "$out" "$here/linux.c" "$here/bench.c" "$gen/xdg-shell-protocol.c" \
    $(pkg-config --cflags --libs wayland-client wayland-egl egl glesv2 gbm)
echo "$out"
