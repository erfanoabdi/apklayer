#!/usr/bin/env bash
# vm-run: build apklayer on the VM and run an APK there through the CLI (bin/apklayer).
# Usage: vm-run.sh [app.apk]        (default: tests/uibench/uibench.apk)
#   Mirrors the deps cache (deps/deps.lock, filled by scripts/fetch-deps.sh on the Mac) and the
#   sources to the VM, builds with Meson, restarts the session shell (fresh binary), then
#   `apklayer run <apk>` (installs it: convert, describe, menu entry; then runs it).
# The app JVM is the x86_64 JDK under Rosetta (C1 + Serial GC: C2 and G1 crash there), because
#   Robolectric's native runtime is x86_64-only on Linux. Development stopgap until PLAN P7.
# APKLAYER_JOPTS: extra app JVM flags for verification, e.g.
#   -Dapklayer.script=2000:tap:100,80+4000:back -Dapklayer.snapms=1500,3500 -Dapklayer.runms=6000
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/tests/uibench/uibench.apk}"
NAME="$(basename "$APK" .apk | tr -c 'A-Za-z0-9._\n-' '_')"   # the VM copy gets a shell-safe name
VM="${APKLY_VM:-utm@192.168.64.7}"
VM_PASS="${APKLY_VM_PASS:-1234}"
DEPS="$HOME/.cache/apklayer/deps"
vm() { sshpass -p "$VM_PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }

"$ROOT/scripts/fetch-deps.sh" "$DEPS" | tail -1
vm "mkdir -p .cache/apklayer apklayer-apps"
rsync -a --delete -e "sshpass -p $VM_PASS ssh -o StrictHostKeyChecking=no" "$DEPS/" "$VM:.cache/apklayer/deps/"

RSYNC="rsync -a --delete -e 'sshpass -p $VM_PASS ssh -o StrictHostKeyChecking=no'"
vm "mkdir -p apklayer-m1"
# incremental: only changed sources are copied and rebuilt (the Meson build dir b/ and native/ are kept)
(cd "$ROOT" && eval $RSYNC --exclude='._*' --exclude=b meson.build meson_options.txt bin runtime bridge "$VM:apklayer-m1/" && \
  eval $RSYNC scripts/javac-jar.sh "$VM:apklayer-m1/scripts/" 2>/dev/null || { vm "mkdir -p apklayer-m1/scripts"; eval $RSYNC scripts/javac-jar.sh "$VM:apklayer-m1/scripts/"; })
vm "cd apklayer-m1 && { [ -d b ] && meson setup --reconfigure b >/dev/null 2>&1 || meson setup b >/dev/null; } && { ninja -C b > /tmp/ninja.log 2>&1 || { grep -a -B2 -A4 'error' /tmp/ninja.log | head -30; exit 1; }; }"
if [ -d "$ROOT/build/native/x86_64" ]; then  # bionic linker + JNI loader (scripts/build-native.sh)
  eval $RSYNC "$ROOT"/build/native/x86_64/ "$VM:apklayer-m1/native/"
fi
sshpass -p "$VM_PASS" scp -q -o StrictHostKeyChecking=no "$APK" "$VM:apklayer-apps/$NAME.apk"

vm bash -s <<EOF
export XDG_RUNTIME_DIR=/run/user/1000 WAYLAND_DISPLAY=wayland-0
mkdir -p \$HOME/.config/apklayer
rm -f \$HOME/.config/apklayer/env  # no x86 JVM stopgap: the VM's own arm64 java
export APKLAYER_JOPTS="$APKLAYER_JOPTS" APKLAYER_DEBUG_INPUT="${APKLAYER_DEBUG_INPUT:-}"
${APKLAYER_PICKER:+export APKLAYER_PICKER='$APKLAYER_PICKER'}
${APKLAYER_CHOOSER:+export APKLAYER_CHOOSER='$APKLAYER_CHOOSER'}
pkill -x apklayer-shell || true
# a file, not a pipe: the detached session shell would otherwise hold the pipe open after the app exits
\$HOME/apklayer-m1/bin/apklayer run \$HOME/apklayer-apps/$NAME.apk > /tmp/apklayer-run.log 2>&1 < /dev/null
grep -vE '^OpenJDK' /tmp/apklayer-run.log
sed 's/^/  /' \$XDG_RUNTIME_DIR/apklayer-shell.log | tail -6
EOF
