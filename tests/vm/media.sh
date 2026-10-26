#!/usr/bin/env bash
# P5.2 MediaPlayer on the VM: tests/apps/hello-media plays a generated 2 s tone; the duration comes from the file,
# the GStreamer engine reaches "playing", and the app's completion callback runs.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; VM="${APKLY_VM:-parallels@10.211.55.4}"; PASS="${APKLY_VM_PASS:-1234}"
"$ROOT/tests/apps/build.sh" hello-media >/dev/null
LOG=$(APKLAYER_JOPTS='-Dapklayer.runms=14000' "$ROOT/scripts/vm-run.sh" "$ROOT/build/hello-media.apk" 2>&1)
sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$VM" 'pkill -x apklayer-shell; pkill -f "[a]pklayer-media"; pkill -f "[a]pklayer.host.Main"; true'
fail=0
check() { if eval "$2"; then echo "PASS  $1"; else echo "FAIL  $1"; fail=1; fi; }
check "getDuration() is the file's real duration (2000 ms)" 'grep -q "prepared, duration=2000" <<<"$LOG"'
check "the GStreamer engine plays" 'grep -q "\[media\] state playing" <<<"$LOG"'
check "no engine error" '! grep -q "media error" <<<"$LOG"'
check "onCompletion runs" 'grep -q "hello-media\] completed" <<<"$LOG"'
check "AudioTrack PCM reaches the audio server" 'grep -q "audio track 22050 Hz, 1 ch" <<<"$LOG" && grep -q "audiotrack wrote" <<<"$LOG"'
check "SoundPool reports the load and plays a stream" 'grep -qE "soundpool loaded status=0 stream=[1-9]" <<<"$LOG"'
exit $fail
