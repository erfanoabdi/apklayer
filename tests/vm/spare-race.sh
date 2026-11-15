#!/bin/bash
# Rapid `apklayer run` / `stop <pkg>` cycles (the X23 round of 2026-11-15 left `run` processes blocked in takeAppSpare's socket read and several
# app-spare holders alive after `stop <pkg>`): every `run` ends once the app is stopped or its run time is over, no holder outlives its launcher (apart from
# the instance's one idle spare), and a full `apklayer stop` then leaves no process of the instance. PKG (default hello.input), CYCLES (default 12).
[ -n "${APKLAYER:-}" ] || cd "$(dirname "$0")/../.."  # APKLAYER: the CLI of an installed package (a phone)
A=${APKLAYER:-bin/apklayer}
pkg=${1:-hello.input} cycles=${2:-12}
inst=${APKLAYER_INSTANCE:-default}
state="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/$inst"
export APKLAYER_DISPLAY_WIDTH=480 APKLAYER_DISPLAY_HEIGHT=400 APKLAYER_JOPTS='-Dapklayer.runms=6000'
fail() { echo "FAIL $*"; $A stop >/dev/null 2>&1; exit 1; }
declare -A own
for ((p = $$; p > 1; )); do own[$p]=1; read -r stat < /proc/$p/stat; stat=${stat##*) }; set -- $stat; p=$2; done
# processes of the instance (its environment) whose command line contains $1
procs() {
    local p env c
    for d in /proc/[0-9]*; do
        p=${d#/proc/}
        [ -n "${own[$p]}" ] || [ "$p" = "$BASHPID" ] && continue
        { mapfile -d '' env < "$d/environ"; } 2>/dev/null || continue
        [[ " ${env[*]} " == *" APKLAYER_INSTANCE=$inst "* ]] || continue
        c=$(tr '\0' ' ' < "$d/cmdline"); [[ "$c" == *"$1"* ]] && echo "$p ${c:0:150}"
    done
}
$A stop >/dev/null 2>&1
[ -z "$(procs '')" ] || fail "processes before the test"
# the cold start (stack, hub; a phone takes a minute) is not what is tested: one short launch first, to its end
$A run "$pkg" >/dev/null 2>&1
# the launch of a cycle takes the instance's spare when it is ready (its log says "preloaded"), and the app then lives long: a stop that misses
# it leaves the run waiting for the app until the deadline below. A spare's JVM carries no package name until it renames itself (seconds on a
# phone): `stop <package>` right after the launch took it must still find the launch (run.cpp's record names the process)
spare_ready() { for ((w = 0; w < 120; w++)); do grep -q preloaded "$state/app-spare.log" 2>/dev/null && return; read -t 0.5 <> <(:) || true; done; }
export APKLAYER_JOPTS='-Dapklayer.runms=300000'
# the window made certain: the spare's JVM stopped (SIGSTOP) takes the launch but cannot rename itself, so it stays unnamed until it is killed
spare_ready
jvm=$(pgrep -P "$(procs ' app-spare ' | head -1 | cut -d' ' -f1)" | head -1)
[ -n "$jvm" ] || fail "no spare JVM to hold"
kill -STOP "$jvm"
$A run "$pkg" >/dev/null 2>&1 & pid=$!
for ((w = 0; w < 100; w++)); do [ -e "$state/run.$pid" ] && break; read -t 0.2 <> <(:) || true; done
[ -e "$state/run.$pid" ] || fail "the launch that took the stopped spare left no record"
[[ "$(tr '\0' ' ' < /proc/$jvm/cmdline)" == *--spare* ]] || fail "the taken spare JVM is not the unnamed one: the test does not hold the window"
$A info "$pkg" | grep -q $'^running\t1' || fail "info does not know the launch that took the spare ($(cat "$state/run.$pid" | tr '\n' ' '))"
$A stop "$pkg" >/dev/null 2>&1
for ((w = 0; w < 60; w++)); do kill -0 $pid 2>/dev/null || break; read -t 0.5 <> <(:) || true; done  # the stopped JVM takes SIGKILL after 5 s
kill -0 $pid 2>/dev/null && fail "stop $pkg did not end the launch that took the spare"
runs=()
for ((i = 0; i < cycles; i++)); do
    spare_ready
    $A run "$pkg" >/dev/null 2>&1 & pid=$!; runs+=($pid)
    # the launch is recorded once it has its JVM (the spare's, taken, or a cold one): stopped at once, but every third one is left running
    for ((w = 0; w < 100; w++)); do [ -e "$state/run.$pid" ] && break; kill -0 $pid 2>/dev/null || break; read -t 0.2 <> <(:) || true; done
    ((i % 3 == 0)) || $A stop "$pkg" >/dev/null 2>&1
done
$A stop "$pkg" >/dev/null 2>&1
# every run ends (an app lives 5 minutes unless it is stopped): 40 s in all
for ((t = 0; t < 80; t++)); do
    live=0; for p in "${runs[@]}"; do kill -0 "$p" 2>/dev/null && live=$((live + 1)); done
    ((live == 0)) && break
    read -t 0.5 <> <(:) || true
done
((live == 0)) || fail "$live of ${#runs[@]} run processes alive after 40 s: $(for p in "${runs[@]}"; do kill -0 "$p" 2>/dev/null && echo "[$p wchan=$(cat /proc/$p/wchan 2>/dev/null)]"; done) holders: $(procs ' app-spare ')"
# a holder serves one launcher (a hub-started component host runs for good) or is the instance's one idle spare
# (a holder started by the last launch may still be on its way to find the lock taken and end: 20 s)
for ((t = 0; t < 40; t++)); do
    # wc, not grep -c: the grep of this very pipeline has the pattern in its command line and the instance's environment
    holders=$(procs ' app-spare ' | wc -l)
    launchers=$(procs 'apklayer' | grep -E ' (run|component-host) ' | wc -l)
    ((holders <= launchers + 1)) && break
    read -t 0.5 <> <(:) || true
done
((holders <= launchers + 1)) || fail "$holders app-spare holders for $launchers launchers after the runs ended: $(procs ' app-spare ')"
$A stop >/dev/null 2>&1
left=$(procs ''); records=$(cd "$state" && ls run.* 2>/dev/null)
[ -z "$left" ] || fail "left after stop:"$'\n'"$left"
[ -z "$records" ] || fail "run records left after stop: $records"
echo "PASS spare-race: $cycles run/stop cycles, every run ended, $holders spare holder(s) for $launchers launchers, nothing left after stop"
