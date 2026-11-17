set -u
INST=$1; O=$2; T=$3; RUNMS=$4; PKG=com.kurogame.gplay.punishing.grayraven.en
export XDG_RUNTIME_DIR=/run/user/1000
set -a; eval "$(systemctl --user show-environment | sed 's/^\([A-Za-z_0-9]*\)=\(.*\)$/\1="\2"/')"; set +a
rm -rf $O; mkdir -p $O
python3 -I $HOME/pgr-vmread/sampler.py $O $T $5 $$ > $O/htab.log 2>&1 &
SP=$!
APKLAYER_PTRACER=1 APKLAYER_JOPTS=-Dapklayer.runms=$RUNMS apklayer --instance $INST run $PKG > $O/run.log 2>&1 &
RP=$!
wait $SP
tail -8 $O/htab.log
apklayer --instance $INST stop >/dev/null 2>&1
wait $RP
du -sh $O
