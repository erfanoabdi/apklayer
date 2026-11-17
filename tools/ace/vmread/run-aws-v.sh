# usage: [RUNMS=100000] bash run-aws-v.sh VARIANT TAG   (VARIANT: base|pin1|hog|nice19|nicehog)  native game in pgr-native-ns, thread sampler 1 Hz
set -u
V=$1; TAG=$2; RUNMS=${RUNMS:-100000}
W=$HOME/pgr-vmread; O=$W/v-$V-$TAG; sudo -n rm -rf $O $W/ace-before; mkdir -p $O $W/ace-before
PKG=com.kurogame.gplay.punishing.grayraven.en
export XDG_DATA_HOME=$HOME/pgr-mapfix-xdg INST=mfn
F=$XDG_DATA_HOME/apklayer/mfn/app-data/$PKG/app/files
for f in "$F"/ano_tmp "$F"/ace_shell_di.dat "$F"/ano_app_* "$F"/fake_* "$F"/atti_*; do [ -e "$f" ] && mv "$f" $W/ace-before/; done
APKDIR=$HOME/pgr-mapfix-work/apkdir
PRE=""; HOGS=""
case $V in pin1) PRE="taskset -c 0";; nice19) PRE="nice -n 19";; nicehog) PRE="nice -n 19";; esac
case $V in hog|nicehog) for i in 1 2 3 4; do sh -c 'while :; do :; done' & HOGS="$HOGS $!"; done;; esac
[ -n "${PROBE:-}" ] && sudo -n python3 $W/probe.py $PROBE 14 $(ls /proc | sort -n | tail -1) $O/probe.maps > $O/probe.log 2>&1 &
[ -n "${RESMAP:-}" ] && sudo -n python3 $W/respmap.py $O/r $RESMAP $(ls /proc | sort -n | tail -1) > $O/resmap.log 2>&1 &
sudo -n python3 $W/tsamp.py $O/threads.txt 0 ${TS:-100} $(ls /proc | sort -n | tail -1) 1 > $O/tsamp.log 2>&1 &
TP=$!
sudo -n unshare -m --propagation private env U=$(id -un) UID_=$(id -u) HOME="$HOME" PKG=$PKG A="$XDG_DATA_HOME/apklayer/$INST" APKDIR="$APKDIR" XDG_DATA_HOME="$XDG_DATA_HOME" INST=$INST PRE="$PRE" RUNMS=$RUNMS O=$O bash -c '
mount -t tmpfs tmpfs /data; mount -t tmpfs tmpfs /storage
mkdir -p /data/data/$PKG /data/user/0/$PKG /data/user_de/0/$PKG /storage/emulated/0
mount --bind $A/app-data/$PKG/app /data/data/$PKG; mount --bind $A/app-data/$PKG/app /data/user/0/$PKG; mount --bind $A/app-data/$PKG/device /data/user_de/0/$PKG
mount --bind $A/storage/emulated/0 /storage/emulated/0; mount --bind $A/storage/emulated/0 /sdcard
mkdir -p /data/app; mount --bind "$APKDIR" /data/app
exec runuser -u $U -- env HOME="$HOME" XDG_RUNTIME_DIR=/run/user/$UID_ DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$UID_/bus XDG_DATA_HOME="$XDG_DATA_HOME" INST="$INST" PRE="$PRE" RUNMS=$RUNMS O=$O bash -c "APKLAYER_JOPTS=-Dapklayer.runms=$RUNMS $PRE apklayer --instance $INST run $PKG > $O/run.log 2>&1; apklayer --instance $INST stop >/dev/null 2>&1"'
for h in $HOGS; do kill $h; done
sudo -n kill $TP 2>/dev/null; wait 2>/dev/null
R=$(grep -a -c 'invalid size\|double free\|corrupted\|free(): ' $O/run.log); L=$(tail -1 $O/threads.txt | cut -d' ' -f1)
echo "$V $TAG storm_lines=$R last_thread_sample_t=$L $(grep -a -m1 'invalid size\|double free\|free(): ' $O/run.log)"
