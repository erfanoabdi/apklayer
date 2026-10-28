#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
VM="${APKLY_VM:-utm@192.168.64.7}"
PASS="${APKLY_VM_PASS:-1234}"
COPYFILE_DISABLE=1 tar cf - aosp/hals/audio/manifest.xml aosp/hals/audio/effects-manifest.xml aosp/hals/audio/audio_policy_configuration.xml aosp/hals/audio/audio_effects_config.xml tests/vm/audio-system-run.py tests/hals/fortify-read-test.c |
    sshpass -p "$PASS" ssh "$VM" 'tar xf - -C ~/apklayer-m1'
sshpass -p "$PASS" ssh "$VM" bash -s <<'REMOTE'
set -euo pipefail
cd ~/apklayer-m1
export XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE="$HOME/arm64-native" APKLAYER_AUDIO=1 APKLAYER_AUDIO_SERVICES=1
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-hal-audio.so"
trap 'bin/apklayer stop >/dev/null 2>&1; pkill -9 -x tail || true; python3 -c '\''from pathlib import Path; p=Path.home()/"arm64-native/android-root/vendor/etc/vintf/manifest"; [(p/n).unlink(missing_ok=True) for n in ("apklayer-audio.xml","apklayer-audio-effects.xml")]'\''' EXIT
cc -Wall -Werror tests/hals/fortify-read-test.c -ldl -o /tmp/apklayer-fortify-read-test
LD_LIBRARY_PATH="$HOME/bt-arm/bionic_translation/b" /tmp/apklayer-fortify-read-test "$HOME/bt-arm/bionic_translation/b/libc_bio.so.0"
python3 -u tests/vm/audio-system-run.py
grep -a 'PASS original audio services' $HOME/.local/state/apklayer/default/tool.log
if ps -eo args | grep -E '[a]pklayer.host.Main|[b]tload|[a]pklayer-shell' | grep -v grep; then exit 1; fi
REMOTE
