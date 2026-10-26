#!/bin/sh
# aosp-term: a persistent terminal for the LineageOS tree in OrbStack (user, 2026-10-26: run breakfast once, keep building there).
# docs/AOSP-BUILDS.md: log each build there first; never touch out/.
#   scripts/aosp-term.sh start              open the screen session "aosp" and run envsetup + breakfast gsi_arm64 userdebug once
#   scripts/aosp-term.sh run <command...>   run a command in it (e.g. m libapklayer_egl), wait for it, print the errors/result
#   scripts/aosp-term.sh stop               close the session
# Waiting is a blocking read of a FIFO the command writes its exit code to: no polling. Output: ~/apklayer-aosp-build.last.log
set -u
ORB="orb -m ubuntu22-x86"
case "${1:-}" in
start)
    $ORB bash -c '
        screen -S aosp -X quit >/dev/null 2>&1
        rm -f /tmp/aosp.ready; mkfifo /tmp/aosp.ready
        screen -dmS aosp bash
        screen -S aosp -X stuff "cd ~/los24 && . build/envsetup.sh >/dev/null 2>&1 && breakfast gsi_arm64 userdebug >~/apklayer-aosp-breakfast.log 2>&1; echo \$? > /tmp/aosp.ready\n"
        echo "breakfast exit: $(cat /tmp/aosp.ready)"; rm -f /tmp/aosp.ready'
    ;;
run)
    shift
    cmd="$*"
    $ORB bash -c '
        fifo=/tmp/aosp.fifo.$$; rm -f $fifo; mkfifo $fifo
        log=~/apklayer-aosp-build.last.log
        screen -S aosp -X stuff "( $1 ) > $log 2>&1; echo \$? > $fifo\n"
        rc=$(cat $fifo); rm -f $fifo
        grep -a "error:\|FAILED\|build completed\|Build Succeeded" $log | head -25 | cut -c1-500
        echo "exit=$rc (log: $log)"' _ "$cmd"
    ;;
stop)
    $ORB bash -c 'screen -S aosp -X quit'
    ;;
*)
    echo "usage: $0 start | run <command...> | stop" >&2; exit 2 ;;
esac
