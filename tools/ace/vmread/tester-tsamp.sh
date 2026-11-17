# usage: bash tester-tsamp.sh INST OUTDIR T1 RUNMS   (translated game, thread sampler 1 Hz)
set -u
INST=$1; O=$2; T1=$3; RUNMS=$4; PKG=com.kurogame.gplay.punishing.grayraven.en
export XDG_RUNTIME_DIR=/run/user/1000
set -a; eval "$(systemctl --user show-environment | sed 's/^\([A-Za-z_0-9]*\)=\(.*\)$/\1="\2"/')"; set +a
rm -rf $O; mkdir -p $O
python3 -I $HOME/pgr-vmread/tsamp.py $O/threads.txt 0 $T1 $$ 1 > $O/tsamp.log 2>&1 &
SP=$!
BERBERIS_LOG_FREES=1 APKLAYER_PTRACER=1 APKLAYER_JOPTS=-Dapklayer.runms=$RUNMS apklayer --instance $INST run $PKG > $O/run.log 2>&1 &
RP=$!
wait $SP
apklayer --instance $INST stop >/dev/null 2>&1
wait $RP
echo "$INST storm_lines=$(grep -a -c 'no glibc chunk' $O/run.log) first=$(grep -a -n -m1 'no glibc chunk' $O/run.log | cut -c1-60)"
