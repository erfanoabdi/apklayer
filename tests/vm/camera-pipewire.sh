#!/bin/bash
# The PipeWire camera backend: a camera node made by GStreamer's pipewiresink (colour bars, YUY2 640x480; a second one MJPEG 1280x720) on the desktop's PipeWire, listed and
# streamed by the backend (camera-pipewire-test), then the same through hello-camera's camera2 path when CAMERA_APP=1. The node is this script's.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
export PIPEWIRE_RUNTIME_DIR=${PIPEWIRE_RUNTIME_DIR:-/run/user/$(id -u)}
gst-launch-1.0 -q videotestsrc pattern=smpte ! video/x-raw,format=YUY2,width=640,height=480,framerate=30/1 ! \
    pipewiresink mode=provide stream-properties="props,media.class=Video/Source,media.role=Camera,node.name=apklayer-test-camera,node.description=apklayer-test-camera" >/dev/null 2>&1 &
fixture=$!
# a webcam's larger size, offered only as MJPEG: listed, streamed as JPEG frames (the provider decodes them)
gst-launch-1.0 -q videotestsrc pattern=smpte ! video/x-raw,width=1280,height=720,framerate=30/1 ! jpegenc ! image/jpeg,width=1280,height=720,framerate=30/1 ! \
    pipewiresink mode=provide stream-properties="props,media.class=Video/Source,media.role=Camera,node.name=apklayer-test-mjpeg,node.description=apklayer-test-mjpeg" >/dev/null 2>&1 &
fixture="$fixture $!"
trap 'kill $fixture 2>/dev/null' EXIT
list=$(CAMERA_WAIT=apklayer-test-camera,apklayer-test-mjpeg b/bridge/hals/camera/camera-pipewire-test) || { echo "$list"; exit 1; }
echo "$list"
echo "$list" | grep -q '^apklayer-test-mjpeg: .* MJPG 1280x720, first byte 255' || { echo "FAIL the MJPEG-only camera"; exit 1; }
[ "${CAMERA_APP:-0}" = 1 ] || exit 0
out=$APKLY_STATE/camera-pipewire.out
bin/apklayer stop >/dev/null 2>&1
trap 'kill $fixture 2>/dev/null; bin/apklayer stop >/dev/null 2>&1; bin/apklayer providers unset camera >/dev/null' EXIT
bin/apklayer providers set camera pipewire >/dev/null
APKLAYER_JOPTS="-Dapklayer.runms=25000" bin/apklayer run hello.camera > $out 2>&1 || true
grep -a "\[cam\]" $out
bin/apklayer stop >/dev/null 2>&1
# the frames are the fixture's SMPTE bars, not the fake's: a YUV frame of the asked size is enough
grep -aq "\[cam\] opened" $out && grep -aq "\[cam\] yuv .* 640x480" $out
