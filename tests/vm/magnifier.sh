#!/bin/bash
# The surfaces a text drag makes: a child layer under the window's own SurfaceControl and the text magnifier (Magnifier.show on an EditText).
# On the VM, in the group's instance (tests/run magnifier): APKLAYER_INSTANCE, APKLY_STATE (its logs) and hello.input installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/magnifier.out
bin/apklayer stop >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=8000 -Dhello.magnifier=1" bin/apklayer run hello.input > $out 2>&1
status=$?
grep -a "\[hello-input\] \(child\|magnifier\|pixel\)\|SIGSEGV\|Exception" $out | head -20
bin/apklayer stop >/dev/null 2>&1
echo "app exit status $status"
# PixelCopy.SUCCESS is 0
[ $status = 0 ] && grep -aq "child layer valid=true" $out && grep -aq "pixel copy result=0 " $out && grep -aq "magnifier dismissed" $out
