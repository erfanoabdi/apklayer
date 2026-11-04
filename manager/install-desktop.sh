#!/bin/sh
# install-desktop.sh [manager binary]: the manager's desktop entry and icon in ~/.local/share, for a manager built from the source tree
# (the deb installs them system-wide). GNOME's app grid and search then open it. Default binary: manager/b/apklayer-manager of this tree.
set -e
cd "$(dirname "$0")/.."
bin="$(realpath "${1:-manager/b/apklayer-manager}")"
[ -x "$bin" ] || { echo "install-desktop: no manager at $bin (build it: manager/meson.build)" >&2; exit 1; }
share="${XDG_DATA_HOME:-$HOME/.local/share}"
install -Dm644 data/apklayer.svg "$share/icons/hicolor/scalable/apps/apklayer.svg"
install -Dm644 data/apklayer-256.png "$share/icons/hicolor/256x256/apps/apklayer.png"
mkdir -p "$share/applications"
sed "s|^Exec=.*|Exec=$bin|" data/apklayer-manager.desktop > "$share/applications/apklayer-manager.desktop"
command -v update-desktop-database >/dev/null && update-desktop-database -q "$share/applications" || true
echo "installed $share/applications/apklayer-manager.desktop -> $bin"
