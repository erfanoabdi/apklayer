#!/bin/bash
# audio-host.sh aidl|system: PCM of the audio HAL over remote AIDL/FMQ (aidl) or of the original AudioTrack through the audio services
# (system) reaches a private PipeWire sink. On the VM, in the group's instance (tests/run audio-aidl, audio-track: APKLAYER_INSTANCE, APKLY_STATE = its logs, APKLAYER_NATIVE).
set -euo pipefail
: "${APKLAYER_NATIVE:=$HOME/arm64-native}" "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
export APKLAYER_HOST_LIBS="$APKLAYER_NATIVE/libapklayer-hal-graphics.so:$APKLAYER_NATIVE/libapklayer-audio-pipewire.so"
cp --remove-destination b/bridge/hals/audio/libapklayer-audio-pipewire.so "$APKLAYER_NATIVE/"
trap 'bin/apklayer stop >/dev/null 2>&1; true' EXIT
python3 -u tests/vm/audio-host.py "$1"
python3 tests/lib/instance.py clean
