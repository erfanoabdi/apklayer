#!/bin/sh
# sf-shot.sh PKG [SECS [OUT.png]]: one app on a phone, started, screenshotted (apklayer screenshot) and stopped in one command; its log is PKG.run.log
pkg=$1 secs=${2:-20} out=${3:-$1.png}
apklayer stop >/dev/null 2>&1
apklayer run "$pkg" > "$pkg.run.log" 2>&1 &
sleep "$secs"
apklayer screenshot "$pkg" "$out" 2>&1 | tail -2
apklayer stop 2>&1 | tail -1
