#!/bin/bash
# files.sh [authority [document [uri]]]: (VOLUMES=1: the storage an ordinary app sees, below) the storage provider's roots and the home directory listing, as DocumentsUI queries them (hello-files is a privileged test package: tests/groups.toml setup). READ=1 reads a file; OWNDATA=1 writes through /data/data/<pkg> and /data/user/0/<pkg> instead (paths.c) and reads back from getFilesDir().
# On the VM, in the group's instance (tests/run files): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
A=${1:-com.android.externalstorage.documents} D=${2:-linux-home:} out=$APKLY_STATE/files.out
bin/apklayer stop >/dev/null 2>&1
if [ -n "$SCOPED" ]; then  # another package's files on the instance's external storage, made behind the app's back
    n=${APKLAYER_INSTANCE:-default}; [ $n != default ] || n=0; ext=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$n/storage/emulated/0
    mkdir -p $ext/Android/data/hello.scopedother/files $ext/Android/obb/hello.scopedother; echo s > $ext/Android/data/hello.scopedother/files/secret; echo s > $ext/Android/obb/hello.scopedother/secret
fi
if [ -n "$OWNDATA" ]; then  # a stale link into another instance's data (an instance dir copied with its absolute links) must be replaced by the own one
    n=${APKLAYER_INSTANCE:-default}; [ $n != default ] || n=0; dh=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer; stale=$dh/$n/stale-decoy
    mkdir -p $stale $dh/$n/data/user/0; ln -sfn $stale $dh/$n/data/user/0/hello.files
fi
APKLAYER_JOPTS="-Dapklayer.runms=60000 -Dhello.authority=$A -Dhello.doc=$D ${3:+-Dhello.uri=$3} ${READ:+-Dhello.read=$READ} ${OWNDATA:+-Dhello.owndata=1} ${LIBCORE:+-Dhello.libcore=1} ${VOLUMES:+-Dhello.volumes=1} ${SCOPED:+-Dhello.scoped=1 -Dhello.scoped.other=hello.scopedother}" bin/apklayer run hello.files > $out 2>&1 || true
grep -a "\[volumes\]" $out
grep -a "\[files\]" $out | grep -av "\] entry" -A12 | head -40
grep -a -h "not started" $APKLY_STATE/component-*.log | cut -c1-300
grep -a -h -B1 -A6 "Exception" $APKLY_STATE/component-*.log | head -24
bin/apklayer stop >/dev/null 2>&1
# VOLUMES=1: what an ordinary app (no MANAGE_EXTERNAL_STORAGE) sees, as on Android 16: not a storage manager, its own Android/data/<pkg>/files and cache, the primary volume
volumes() { grep -aq "\[volumes\] external /storage/emulated/0 state=mounted manager=false" $out && grep -aq "\[volumes\] files /storage/emulated/0/Android/data/hello.files/files exists=true cache=/storage/emulated/0/Android/data/hello.files/cache " $out \
    && grep -aq "\[volumes\] volume /storage/emulated/0 desc=.* primary=true removable=false emulated=true state=mounted" $out && grep -aq "\[volumes\] DONE" $out; }
if [ -n "$LIBCORE" ]; then grep -a "\[libcore\]" $out; grep -aq "\[libcore\] cleaner ran=true" $out && grep -aq "shm prot=5 perms=r-xs" $out && grep -aq "shm prot=0 perms=---s" $out && grep -aq "shm prot=3 perms=rw-s" $out && grep -aq "\[libcore\] DONE" $out; exit; fi
if [ -n "$VOLUMES" ]; then volumes; exit; fi
if [ -n "$SCOPED" ]; then rm -rf $ext/Android/data/hello.scopedother $ext/Android/obb/hello.scopedother; grep -a "\[files\] scoped" $out; grep -aq "\[files\] scoped own=true dataHidden=true obbHidden=true" $out && grep -aq "\[files\] scoped link=true" $out; exit; fi
[ -z "$OWNDATA" ] || { [ "$(readlink $dh/$n/data/user/0/hello.files)" = $dh/$n/app-data/hello.files/app ] && [ -z "$(ls -A $stale)" ]; rc=$?; rmdir $stale 2>/dev/null; [ $rc = 0 ] || { echo "stale data link kept"; exit 1; }; }
grep -aq "\[files\] DONE" $out && { [ -z "$OWNDATA" ] || grep -aq "\[files\] owndata java=java os=os nio=true other=false" $out; }

