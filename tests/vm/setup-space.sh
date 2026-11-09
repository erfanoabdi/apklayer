#!/bin/bash
# setup-space.sh <apklayer CLI>: `apklayer setup`'s free space checks and data move (common/fetch.cpp runtimeRoom, cli/datamove.cpp) on a small
# loop-mounted file system as the data home, with fake runtime bundles of random (incompressible) data. Run on the test machine; needs root for
# the mounts and /userdata (SUDO, default `sudo -n`). Leaves nothing behind; /userdata is only touched when it does not exist.
set -u
cli="$(realpath "$1")"
SUDO="${SUDO:-sudo -n}"
T="$(mktemp -d "$HOME/setup-space.XXXXXX")"
fails=0
check() { if grep -q -- "$2" "$T/out"; then echo "ok   $1"; else echo "FAIL $1 (no '$2')"; sed 's/^/     /' "$T/out"; fails=$((fails + 1)); fi; }
userdata=0
cleanup() {
    mountpoint -q "$T/home" && $SUDO umount "$T/home"
    [ "$userdata" = 1 ] && $SUDO rm -rf /userdata
    rm -rf "$T" "${XDG_RUNTIME_DIR:-/nonexistent}/apklayer/spacetest"
}
trap cleanup EXIT
small() {  # a fresh 100 MB ext4 as the data home
    mountpoint -q "$T/home" && $SUDO umount "$T/home"
    mkdir -p "$T/home" && truncate -s 100M "$T/small.img" && mkfs.ext4 -q -F "$T/small.img" && $SUDO mount -o loop "$T/small.img" "$T/home" && $SUDO chown "$(id -u):$(id -g)" "$T/home"
}
bundle() {  # bundle <name> <MB>: a runtime's parts and MB of random data, its unpacked size in the zstd frame as runtime-bundle.sh writes it
    local d="$T/src-$1"
    mkdir -p "$d/android-root/system" "$d/android-libs" "$d/aosp/android-all" && echo '{"version": "x"}' > "$d/runtime.json"
    head -c "$(($2 * 1000000))" /dev/urandom > "$d/android-libs/blob"
    tar -C "$d" -cf - . | zstd -q -T0 --stream-size="$(tar -C "$d" -cf - . | wc -c)" -o "$T/$1.tar.zst"
}
run() { env XDG_DATA_HOME="$T/home" XDG_STATE_HOME="$T/state" XDG_CACHE_HOME="$T/cache" APKLAYER_ROOT=/usr/share/apklayer APKLAYER_BUILD=/usr/lib/apklayer \
        "$cli" --instance spacetest setup "$@" > "$T/out" 2>&1 < /dev/null; }

small && bundle big 120 && bundle mid 45 || { echo "FAIL setup-space: no loop mount"; exit 1; }
run --bundle "$T/big.tar.zst"
check "120 MB in ~85 MB: refused before unpacking, with the way out" $'step\truntime\tfailed\tNot enough space for the Android runtime: it needs 13'
check "the move command is named" "apklayer setup --move-data"
[ -z "$(ls -A "$T/home/apklayer/runtime" 2>/dev/null | grep -v '^\.')" ] && echo "ok   nothing unpacked" || { echo "FAIL something unpacked"; fails=$((fails + 1)); }

# a current runtime and an old one: the old one goes first when only that makes room, current stays
rt="$T/home/apklayer/runtime"
mkdir -p "$rt/old" "$rt/cur/android-root/system" "$rt/cur/android-libs" "$rt/cur/aosp/android-all" && cp "$T/src-big/runtime.json" "$rt/cur/" && head -c 10000000 /dev/urandom > "$rt/cur/blob" && head -c 40000000 /dev/urandom > "$rt/old/blob" && ln -sfn cur "$rt/current"
run --update --bundle "$T/mid.tar.zst"
check "fits with the old runtime removed: removed and said" $'step\truntime\trunning\tRemoved runtime old first to make room'
[ -e "$rt/cur/blob" ] && [ ! -e "$rt/old" ] && echo "ok   current kept, old removed" || { echo "FAIL current or old"; fails=$((fails + 1)); }

# the data moved to a bigger file system: the same update then fits
run --move-data "$T/big/share"
check "--move-data" $'step\tdata\tdone'
[ -L "$T/home/apklayer" ] && [ "$(readlink "$T/home/apklayer")" = "$T/big/share" ] && [ -e "$T/big/share/runtime/cur/blob" ] && echo "ok   a symlink in its place, the data there" || { echo "FAIL the move"; fails=$((fails + 1)); }
run --move-data "$T/big/other"
check "a second move is refused" "is already a link to"
run --update --bundle "$T/big.tar.zst"
grep -q "Not enough space" "$T/out" && { echo "FAIL still no space after the move"; fails=$((fails + 1)); } || echo "ok   the big bundle fits after the move"

# first setup on a small home with /userdata/apklayer (the Sailfish RPM's): the data goes there before anything is unpacked
if [ ! -e /userdata ]; then
    userdata=1
    small && $SUDO mkdir -p /userdata/apklayer && $SUDO chmod 1777 /userdata/apklayer
    run --bundle "$T/mid.tar.zst"
    check "first setup: the data to /userdata" "apklayer's data goes to /userdata/apklayer/$(id -un)/share"
    [ -L "$T/home/apklayer" ] && [ -d "/userdata/apklayer/$(id -un)/share/runtime" ] && echo "ok   linked, the runtime on /userdata" || { echo "FAIL first setup's move"; fails=$((fails + 1)); }
else echo "skip /userdata exists here"; fi

[ "$fails" = 0 ] && echo "PASS setup-space" || echo "FAIL setup-space: $fails"
