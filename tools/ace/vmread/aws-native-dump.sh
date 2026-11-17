set -u
W=$HOME/pgr-vmread; mkdir -p $W
PKG=com.kurogame.gplay.punishing.grayraven.en
export XDG_DATA_HOME=$HOME/pgr-mapfix-xdg INST=mfn
F=$XDG_DATA_HOME/apklayer/mfn/app-data/$PKG/app/files
sudo -n rm -rf $W/ace-before $W/d1; mkdir -p $W/ace-before
for f in "$F"/ano_tmp "$F"/ace_shell_di.dat "$F"/ano_app_* "$F"/fake_* "$F"/atti_*; do [ -e "$f" ] && mv "$f" $W/ace-before/; done
APKDIR=$HOME/pgr-mapfix-work/apkdir
sudo -n python3 $W/dump40.py $W/d1 ${T:-40} 1047000 > $W/dump.log 2>&1 &
DP=$!
sudo -n unshare -m --propagation private env U=$(id -un) UID_=$(id -u) HOME="$HOME" PKG=$PKG A="$XDG_DATA_HOME/apklayer/$INST" APKDIR="$APKDIR" XDG_DATA_HOME="$XDG_DATA_HOME" INST=$INST bash -c '
mount -t tmpfs tmpfs /data; mount -t tmpfs tmpfs /storage
mkdir -p /data/data/$PKG /data/user/0/$PKG /data/user_de/0/$PKG /storage/emulated/0
mount --bind $A/app-data/$PKG/app /data/data/$PKG; mount --bind $A/app-data/$PKG/app /data/user/0/$PKG; mount --bind $A/app-data/$PKG/device /data/user_de/0/$PKG
mount --bind $A/storage/emulated/0 /storage/emulated/0; mount --bind $A/storage/emulated/0 /sdcard
mkdir -p /data/app; mount --bind "$APKDIR" /data/app
exec runuser -u $U -- env HOME="$HOME" XDG_RUNTIME_DIR=/run/user/$UID_ DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/$UID_/bus XDG_DATA_HOME="$XDG_DATA_HOME" INST="$INST" bash -c "APKLAYER_JOPTS=-Dapklayer.runms=${RUNMS:-60000} apklayer --instance $INST run $PKG > $HOME/pgr-vmread/run.log 2>&1; apklayer --instance $INST stop >/dev/null 2>&1"'
wait $DP
cat $W/dump.log
du -sh $W/d1
