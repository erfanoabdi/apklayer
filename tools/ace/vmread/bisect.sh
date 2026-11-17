# bisect over the readable absent pages (mode asr): drive on the AWS host
cd ~/pgr-vmread
run() { PROBE=asr:$1:$2 RUNMS=30000 TS=30 flock -n ~/pgr-run.lock bash run-aws-v.sh base z$1_$2 | grep -o "storm_lines=[0-9]*"; }
r=$(run 0 100000); N=$(grep -o "total [0-9]*" v-base-z0_100000/probe.log | cut -d" " -f2); echo "all: $r total=$N"
lo=0; hi=$N
while [ $((hi-lo)) -gt 1 ]; do
  mid=$(( (lo+hi)/2 ))
  r=$(run $lo $mid); echo "$lo:$mid $r"
  if [ "$r" != "storm_lines=0" ]; then hi=$mid; continue; fi
  r=$(run $mid $hi); echo "$mid:$hi $r"
  if [ "$r" != "storm_lines=0" ]; then lo=$mid; continue; fi
  echo "combination across halves at $lo:$mid:$hi"; break
done
echo "final $lo:$hi"
grep "^page" v-base-z${lo}_${mid:-x}/probe.log | head -5
