#!/bin/bash
# hidl-list.sh [INSTANCE]: on a Halium phone, with camera and media = device, the HIDL listing the camera and Codec2 clients start from
# (listManifestByInterface, listByInterface) must name the device's instances inside a RUNNING stack (libhidl 0005: with the instance's own
# hwservicemanager running, the stand-in must still forward to the host). Prints PASS or FAIL per HAL; the stack is stopped at the end.
A=${APKLAYER:-$(command -v apklayer || echo /opt/click.ubuntu.com/apklayer.erfanoabdi/current/usr/bin/apklayer)}
I="$A --instance ${1:-ut}"
fail=0
APKLAYER_JOPTS="-Dapklayer.runms=60000" $I run hello.camera >/dev/null 2>&1 &  # any app: it starts the stack
$I exec libapklayer_servicetool.so wait SurfaceFlinger >/dev/null 2>&1
for fq in android.hardware.camera.provider@2.4::ICameraProvider android.hardware.media.c2@1.0::IComponentStore android.hardware.media.c2@1.2::IComponentStore; do
    out=$($I exec libapklayer_hidltool.so manifest "$fq" 2>&1 | grep -a '^manifest: [0-9]')
    case "$out" in "manifest: 0"*|"") echo "FAIL $fq: $out"; fail=1 ;; *) echo "PASS $fq: $out" ;; esac
done
$I stop >/dev/null 2>&1
wait
exit $fail
