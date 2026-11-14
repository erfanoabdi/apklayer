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
# mount=[NAME:]DEGREES turns the orientation of the named camera only
mounted=$(CAMERA_WAIT=apklayer-test-camera,apklayer-test-mjpeg b/bridge/hals/camera/camera-pipewire-test mount=apklayer-test-mjpeg:90) || { echo "$mounted"; exit 1; }
orient() { echo "$1" | sed -n "s/^camera [^ ]* \"$2\" .*orientation \([0-9]*\),.*/\1/p"; }
o0=$(orient "$list" apklayer-test-mjpeg); o1=$(orient "$mounted" apklayer-test-mjpeg)
[ "$o1" = "$(( (o0 + 90) % 360 ))" ] && [ "$(orient "$mounted" apklayer-test-camera)" = "$(orient "$list" apklayer-test-camera)" ] || { echo "FAIL mount: $o0 -> $o1"; exit 1; }
echo "mount ok: apklayer-test-mjpeg orientation $o0 -> $o1"
[ "${CAMERA_APP:-0}" = 1 ] || exit 0
out=$APKLY_STATE/camera-pipewire.out
bin/apklayer stop >/dev/null 2>&1
trap 'kill $fixture 2>/dev/null; bin/apklayer stop >/dev/null 2>&1; bin/apklayer providers unset camera >/dev/null; bin/apklayer config unset camera-mount >/dev/null 2>&1' EXIT
bin/apklayer providers set camera pipewire >/dev/null
# the provider numbers the cameras in the backend's listing order (a host with real cameras lists them too)
id_of() { echo "$list" | grep '^camera ' | grep -n " \"$1\" " | cut -d: -f1 | awk '{print $1 - 1}'; }
for cam in apklayer-test-camera apklayer-test-mjpeg; do
    APKLAYER_JOPTS="-Dapklayer.runms=25000 -Dhello.id=$(id_of $cam)" bin/apklayer run hello.camera > $out.$cam 2>&1 || true
    grep -a "\[cam\]" $out.$cam
    bin/apklayer stop >/dev/null 2>&1
    # the frames are the fixture's SMPTE bars, not the fake's: a YUV frame of the asked size, not black (the MJPEG one: decoded by the provider)
    grep -aq "\[cam\] opened $(id_of $cam)" $out.$cam && grep -a "\[cam\] yuv .* 640x480" $out.$cam | grep -qv "640x480\( 16/128/128\)*$" || { echo "FAIL $cam in the app"; exit 1; }
done
# the camera-mount setting (apklayer config) is the provider's mount= option: the named camera's orientation turns by 90, the other one's stays
orientation_of() { sed -n "s/.*\[cam\] camera $(id_of $2) .*orientation=\([0-9]*\).*/\1/p" $1 | head -1; }
bin/apklayer config set camera-mount apklayer-test-mjpeg:90 >/dev/null
APKLAYER_JOPTS="-Dapklayer.runms=25000 -Dhello.id=$(id_of apklayer-test-mjpeg)" bin/apklayer run hello.camera > $out.mounted 2>&1 || true
bin/apklayer stop >/dev/null 2>&1
for cam in apklayer-test-mjpeg apklayer-test-camera; do
    before=$(orientation_of $out.$cam $cam); after=$(orientation_of $out.mounted $cam)
    if [ $cam = apklayer-test-mjpeg ]; then want=$(( (before + 90) % 360 )); else want=$before; fi
    [ -n "$before" ] && [ "$after" = "$want" ] || { echo "FAIL camera-mount setting: $cam orientation $before -> $after (wanted $want)"; exit 1; }
done
echo "camera-mount setting ok"
