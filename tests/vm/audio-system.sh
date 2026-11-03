#!/bin/bash
# The complete original audio services (audioserver).
# On the VM, in the group's instance (tests/run audio-system: APKLAYER_INSTANCE, APKLY_STATE = its logs, APKLAYER_NATIVE).
set -euo pipefail
: "${APKLAYER_NATIVE:=$HOME/arm64-native}" "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
export APKLAYER_AUDIO=1 APKLAYER_AUDIO_SERVICES=1
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-hal-audio.so"
trap 'bin/apklayer stop >/dev/null 2>&1; true; python3 -c '\''import os; from pathlib import Path; p=Path(os.environ["APKLAYER_NATIVE"])/"android-root/vendor/etc/vintf/manifest"; [(p/n).unlink(missing_ok=True) for n in ("apklayer-audio.xml","apklayer-audio-effects.xml")]'\''' EXIT
python3 -u tests/vm/audio-system-run.py
grep -a 'PASS original audio services' $APKLY_STATE/tool.log
python3 tests/lib/instance.py clean
