#!/bin/bash
# files.sh [authority [document [uri]]]: (VOLUMES=1: the storage an ordinary app sees, below) the storage provider's roots and the home directory listing, as DocumentsUI queries them (hello-files is a privileged test package: tests/groups.toml setup). READ=1 reads a file; OWNDATA=1 writes through /data/data/<pkg> and /data/user/0/<pkg> instead (paths.c) and reads back from getFilesDir().
# On the VM, in the group's instance (tests/run files): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
A=${1:-com.android.externalstorage.documents} D=${2:-linux-home:} out=$APKLY_STATE/files.out
bin/apklayer stop >/dev/null 2>&1
APKLAYER_JOPTS="-Dapklayer.runms=60000 -Dhello.authority=$A -Dhello.doc=$D ${3:+-Dhello.uri=$3} ${READ:+-Dhello.read=$READ} ${OWNDATA:+-Dhello.owndata=1} ${VOLUMES:+-Dhello.volumes=1}" bin/apklayer run hello.files > $out 2>&1 || true
grep -a "\[volumes\]" $out
grep -a "\[files\]" $out | grep -av "\] entry" -A12 | head -40
grep -a -h "not started" $APKLY_STATE/component-*.log | cut -c1-300
grep -a -h -B1 -A6 "Exception" $APKLY_STATE/component-*.log | head -24
bin/apklayer stop >/dev/null 2>&1
# VOLUMES=1: what an ordinary app (no MANAGE_EXTERNAL_STORAGE) sees, as on Android 16: not a storage manager, its own Android/data/<pkg>/files and cache, the primary volume
volumes() { grep -aq "\[volumes\] external /storage/emulated/0 state=mounted manager=false" $out && grep -aq "\[volumes\] files /storage/emulated/0/Android/data/hello.files/files exists=true cache=/storage/emulated/0/Android/data/hello.files/cache " $out \
    && grep -aq "\[volumes\] volume /storage/emulated/0 desc=.* primary=true removable=false emulated=true state=mounted" $out && grep -aq "\[volumes\] DONE" $out; }
if [ -n "$VOLUMES" ]; then volumes; exit; fi
grep -aq "\[files\] DONE" $out && { [ -z "$OWNDATA" ] || grep -aq "\[files\] owndata java=java os=os nio=true other=false" $out; }
