#!/bin/bash
# Off The Road 2 (user-supplied APK, not in the repo) on the GPU route: installs it, runs it with native resources, checks real frames.
# Usage: OTR2_APK=/path/to/OTR-2.apk tests/vm/otr2.sh
set -euo pipefail
cd "$(dirname "$0")/../.."
. tests/vm/config.sh
APK="${OTR2_APK:?set OTR2_APK to the OTR2 APK}"
vm() { sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" "$@"; }
sshpass -p "$PASS" scp -q "$APK" "$VM:otr2.apk"
COPYFILE_DISABLE=1 tar cf - tests/vm/otr2-run.py tests/vm/uinput-keys.c | vm 'tar xf - -C ~/apklayer 2>/dev/null'
vm 'cd ~/apklayer; export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native"; bin/apklayer install $HOME/otr2.apk 2>&1 | grep -a installed'
python3 tests/vm/otr2-driver.py "$VM" "$PASS" "${TMPDIR:-/tmp}"
