#!/bin/bash
# One timed PGR run on native arm64 where the game's RAW syscalls (ACE's svc from its own unpacked code, past the preload's views) see the paths
# a device has: /data/data|user/0|user_de/0/<pkg>, /sdcard, /storage/emulated/0 and /data/app/~~<tag>/<pkg>-<tag>/{base,split_*.apk,lib/arm64/*.so},
# bind-mounted in a private mount namespace (real empty /data /sdcard /storage dirs must exist: sudo mkdir; remove after).
# Without it ACE's raw opens of /data/user/0/<pkg>/files and of its own APK/libs return ENOENT natively and the translated run takes a different branch.
#   XDG_DATA_HOME=<private home> INST=<instance> APKDIR=<dir with ~~<tag>/<pkg>-<tag>/ symlinks, optional> N=1 tools/ace/pgr-native-ns.sh   (under flock ~/pgr-run.lock)
set -eu
PKG=com.kurogame.gplay.punishing.grayraven.en; HERE=$(cd "$(dirname "$0")" && pwd)
sudo -n unshare -m --propagation private env U=$(id -un) UID_=$(id -u) HOME="$HOME" PKG=$PKG A="$XDG_DATA_HOME/apklayer/$INST" APKDIR="${APKDIR:-}" HERE="$HERE" XDG_DATA_HOME="$XDG_DATA_HOME" INST="$INST" N="${N:-1}" WORK="${WORK:-$HOME/pgr-work}" RUNMS="${RUNMS:-50000}" bash -c '
mount -t tmpfs tmpfs /data; mount -t tmpfs tmpfs /storage
mkdir -p /data/data/$PKG /data/user/0/$PKG /data/user_de/0/$PKG /storage/emulated/0
mount --bind $A/app-data/$PKG/app /data/data/$PKG; mount --bind $A/app-data/$PKG/app /data/user/0/$PKG; mount --bind $A/app-data/$PKG/device /data/user_de/0/$PKG
mount --bind $A/storage/emulated/0 /storage/emulated/0; mount --bind $A/storage/emulated/0 /sdcard
[ -z "$APKDIR" ] || { mkdir -p /data/app; mount --bind "$APKDIR" /data/app; }
exec runuser -u $U -- env HOME="$HOME" XDG_RUNTIME_DIR=/run/user/$UID_ DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$UID_/bus XDG_DATA_HOME="$XDG_DATA_HOME" INST="$INST" WORK="$WORK" N="$N" RUNMS="$RUNMS" bash "$HERE/pgr-timeline.sh"'
