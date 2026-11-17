# usage: bash tester-snap.sh <instance> <outdir> <step> <total> <runms>  (foreground; instance fresh-data, no login)
set -u
INST=$1; O=$2; STEP=$3; TOTAL=$4; RUNMS=$5; PKG=com.kurogame.gplay.punishing.grayraven.en
export XDG_RUNTIME_DIR=/run/user/1000
set -a; eval "$(systemctl --user show-environment | sed 's/^\([A-Za-z_0-9]*\)=\(.*\)$/\1="\2"/')"; set +a
rm -rf $O; mkdir -p $O
python3 -I $HOME/pgr-vmread/snap.py $O $STEP $TOTAL $$ > $O/snap.log 2>&1 &
SP=$!
APKLAYER_PTRACER=1 APKLAYER_JOPTS=-Dapklayer.runms=$RUNMS apklayer --instance $INST run $PKG > $O/run.log 2>&1 &
RP=$!
wait $SP
cat $O/snap.log | tail -5
apklayer --instance $INST stop >/dev/null 2>&1
wait $RP
du -sh $O
