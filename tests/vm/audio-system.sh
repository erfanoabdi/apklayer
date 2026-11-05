#!/bin/bash
# The complete original audio services (audioserver).
# On the VM, in the group's instance (tests/run audio-system: APKLAYER_INSTANCE, APKLY_STATE = its logs, APKLAYER_NATIVE).
set -euo pipefail
: "${APKLAYER_NATIVE:=$HOME/arm64-native}" "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-audio-pipewire.so"
trap 'bin/apklayer stop >/dev/null 2>&1; true' EXIT  # the stack stages its HAL manifests in its own instance root (cli/stack.cpp): nothing shared to clean up
python3 -u tests/vm/audio-system-run.py
grep -a 'PASS original audio services' $APKLY_STATE/tool.log
python3 tests/lib/instance.py clean
