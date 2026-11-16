#!/bin/bash
# One timed PGR run with the true timeline (pgr-timeline.bt): ACE's files reset first (moved to ace-before-<N>, what the run wrote copied to ace-after-<N>),
# core dumps off for the run (a process that dies of SIGSEGV otherwise lingers 80-130 s in systemd-coredump), the run's trace in <work>/timeline-<N>.txt.
# Run on the host that has the instance (the Mac cannot reach the tester PC except through git: copy-paste the text over ssh stdin, never scp).
#   INST=trial2 WORK=~/pgr-work N=1 [PW=<sudo password>] [RUNMS=120000] [APKLAYER=apklayer] tools/ace/pgr-timeline.sh
# x86_64 hosts: kprobe force_sig_fault; arm64: arm64_force_sig_fault too. Restores the core_pattern at the end.
set -u
INST=${INST:?instance}; WORK=${WORK:?work dir}; N=${N:-1}; PW=${PW:-}; RUNMS=${RUNMS:-120000}; APKLAYER=${APKLAYER:-apklayer}
PKG=com.kurogame.gplay.punishing.grayraven.en
F=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$INST/app-data/$PKG/app/files
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$WORK"
sd() { if [ -n "$PW" ]; then sudo -S -p '' "$@" <<<"$PW"; else sudo -n "$@"; fi; }
case "$(uname -m)" in aarch64) FAULT='kprobe:arm64_force_sig_fault, kprobe:force_sig_fault';; *) FAULT='kprobe:force_sig_fault';; esac
sed "s|@FAULT@|$FAULT|" "$HERE/pgr-timeline.bt" > "$WORK/timeline.bt"
[ -f "$WORK/core-pattern-saved" ] || cat /proc/sys/kernel/core_pattern > "$WORK/core-pattern-saved"
sd sysctl -q -w kernel.core_pattern='|/bin/true'
mkdir -p "$WORK/ace-before-$N" "$WORK/ace-after-$N"
for f in "$F"/ano_tmp "$F"/ace_shell_di.dat "$F"/ano_app_* "$F"/fake_* "$F"/atti_*; do [ -e "$f" ] && mv "$f" "$WORK/ace-before-$N/"; done
OUT=$WORK/timeline-$N.txt; : > "$OUT"
# STRLEN=200 for an old bpftrace (default 64, the tester PC); bpftrace 0.25 (AWS) has a long default and fails to load with the variable set
sd env ${STRLEN:+BPFTRACE_MAX_STRLEN=$STRLEN} bpftrace "$WORK/timeline.bt" > "$OUT" 2>&1 &
grep -m1 -q -i -e READY -e error < <(tail -f -n+1 "$OUT")
if grep -q -i error "$OUT"; then cat "$OUT"; sd sysctl -q -w kernel.core_pattern="$(cat "$WORK/core-pattern-saved")"; exit 1; fi
if [ -n "${EXTRA_BT:-}" ]; then   # a second bpftrace script (ace-sysdiff-*.bt) over the same run; @BRIDGE@ = the instance's translator
  BR=$(readlink -f "${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/runtime/current/android-libs/libberberis_arm64.so")
  sed -e "s|@BRIDGE@|$BR|g" -e "s|@FAULT@|$FAULT|" "$HERE/$EXTRA_BT" > "$WORK/extra.bt"
<<<<<<< HEAD
  sd env ${STRLEN:+BPFTRACE_MAX_STRLEN=$STRLEN} BPFTRACE_PERF_RB_PAGES=${RBPAGES:-8192} bpftrace $(grep -q "system(" "$WORK/extra.bt" && echo --unsafe) "$WORK/extra.bt" > "$WORK/extra-$N.txt" 2>&1 &
=======
  sd env ${STRLEN:+BPFTRACE_MAX_STRLEN=$STRLEN} BPFTRACE_PERF_RB_PAGES=${RBPAGES:-8192} bpftrace --unsafe "$WORK/extra.bt" > "$WORK/extra-$N.txt" 2>&1 &
  grep -m1 -q -i -e READY -e error < <(tail -f -n+1 "$WORK/extra-$N.txt")   # attached before the game starts (a late attach missed the libanogs open)
>>>>>>> worktree-agent-af56ce931f4556412
fi
S=$(date +%s.%N)
APKLAYER_JOPTS=-Dapklayer.runms=$RUNMS $APKLAYER --instance "$INST" run $PKG > "$WORK/run-$N.log" 2>&1; rc=$?
echo "run $N rc=$rc wall $(echo "$(date +%s.%N) - $S" | bc)"
$APKLAYER --instance "$INST" stop >/dev/null 2>&1
sd pkill -INT -f "[b]pftrace $WORK/timeline.bt"
sd pkill -INT -f "[b]pftrace .*$WORK/extra.bt"
pkill -f "[t]ail -f -n+1 $OUT"; pkill -f "[t]ail -f -n+1 $WORK/extra-$N.txt"
cp -a "$F"/ano_tmp "$F"/ace_shell_di.dat "$F"/ano_app_* "$F"/fake_* "$F"/atti_* "$WORK/ace-after-$N/" 2>/dev/null
sd sysctl -q -w kernel.core_pattern="$(cat "$WORK/core-pattern-saved")"
echo "core_pattern now: $(cat /proc/sys/kernel/core_pattern)"
echo "ace-after-$N: $(ls "$WORK/ace-after-$N" | tr '\n' ' ')"
grep -a -e T0 -e EXIT -e FAULT -e RTSQ -e TGKILL -e ' SIG ' "$OUT" | grep -a -v -e 'sig 11 code 1 .*java' | cut -c1-200 | head -${HEAD:-40}
