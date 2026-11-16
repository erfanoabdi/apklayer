#!/bin/bash
# PGR on the tester PC: N timed runs on the copy instance with ONE destination blocked (iptables OUTPUT, this uid only), the net tracer ace-net.bt on every run.
# Run inside the lock:  flock ~/pgr-run.lock env VARIANT=reject|drop|none DST=1.2.3.4 DPORT=1234 N0=1 N1=8 PW=<sudo pw> tools/ace/pgr-netblock.sh
# The rule is inserted per run and removed (-D, never a flush) after it and in the exit trap; iptables -S before/after must be identical (logged).
set -u
VARIANT=${VARIANT:-none}; DST=${DST:-}; DPORT=${DPORT:-}; N0=${N0:-1}; N1=${N1:-1}; PW=${PW:?}; W=${W:-$HOME/pgr-netg/w-$VARIANT}
HERE=$(cd "$(dirname "$0")" && pwd); LIB=$HOME/.local/share/apklayer/runtime/current/android-libs/libberberis_arm64.so
WANT=${WANT:-383d5a48c06b2da0a8fbf0c9a5fe01e1174fc4106e686089934a4741878cda9c}
sd() { sudo -S -p '' "$@" <<<"$PW"; }
mkdir -p "$W"; UID_=$(id -u)
case $VARIANT in reject) T="REJECT --reject-with tcp-reset";; drop) T=DROP;; none) T=;; *) exit 2;; esac
# DST = comma list; one rule per address (-C/-D of exactly what -I inserted, never a flush)
rule() { echo "OUTPUT -d $1 -p tcp -m multiport --dports $DPORT -m owner --uid-owner $UID_ -j $T"; }
insrule() { [ -n "$T" ] && for d in ${DST//,/ }; do sd iptables -I $(rule $d); done; }
unrule() { [ -n "$T" ] && for d in ${DST//,/ }; do while sd iptables -C $(rule $d) 2>/dev/null; do sd iptables -D $(rule $d); done; done; }
trap 'unrule; sd iptables -S > "$W/ipt-trap.txt"' EXIT
sd iptables -S > "$W/ipt-before.txt"
export BERBERIS_MODE=lite-translate-or-interpret WAYLAND_DISPLAY=wayland-1 XDG_RUNTIME_DIR=/run/user/$UID_
for N in $(seq "$N0" "$N1"); do
  S=$(sha256sum "$LIB" | cut -d' ' -f1); [ "$S" = "$WANT" ] || { echo "LIB SHA MISMATCH $S"; exit 3; }
  insrule
  echo "== $VARIANT run $N lib ${S:0:8}"
  # SHOTS="120 280": screenshots of the game window that many seconds after T0 (stopped with the run)
  SHP=; if [ -n "${SHOTS:-}" ]; then ( until grep -q ' T0 ' "$W/timeline-$N.txt" 2>/dev/null; do sleep 0.5; done; P=0; for t in $SHOTS; do sleep $((t-P)); P=$t; apklayer --instance pgrshared screenshot com.kurogame.gplay.punishing.grayraven.en "$W/shot-$N-$t.png" >/dev/null 2>&1; done ) & SHP=$!; fi
  INST=pgrshared WORK=$W N=$N PW=$PW RUNMS=${RUNMS:-75000} STRLEN=200 EXTRA_BT=ace-netblock.bt HEAD=60 "$HERE/pgr-timeline.sh" | grep -a -e '^run ' -e ' T0 ' -e ' EXIT ' -e FAULT -e TGKILL -e RTSQ | cut -c1-160 | tail -14
  [ -n "$SHP" ] && { pkill -P $SHP 2>/dev/null; kill $SHP 2>/dev/null; }
  [ -n "$T" ] && echo "rule hits: $(sd iptables -L OUTPUT -nvx | grep -a "multiport" | awk '{print $1" pkts"}')"
  unrule
  # the ACE net thread's servers (Tencent Cloud 101.32.143.x / 129.226.x.x; port 10012 gets the 325-byte send); NETGREP overrides
  grep -a -E 'CONNECT |SENDTO|RECVFROM' "$W/extra-$N.txt" | grep -a -E "${NETGREP:-101\.32\.143\.|129\.226\.}" | grep -a -v ':443 ret 0' | cut -c1-150 | head -${NETHEAD:-16}
done
unrule; sd iptables -S > "$W/ipt-after.txt"
diff "$W/ipt-before.txt" "$W/ipt-after.txt" >/dev/null && echo "iptables -S before == after" || echo "IPTABLES DIFFERS"
