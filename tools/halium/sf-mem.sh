#!/bin/sh
# sf-mem.sh PKG [SECS]: PKG runs SECS seconds, then every apklayer process's name, Rss and Pss (smaps_rollup, kB) and the JVM's heap flags; stopped afterwards
pkg=$1 secs=${2:-30}
apklayer stop >/dev/null 2>&1
apklayer run "$pkg" > "$pkg.mem.log" 2>&1 &
sleep "$secs"
for p in /proc/[0-9]*; do
  [ -r $p/environ ] || continue
  tr '\0' '\n' < $p/environ 2>/dev/null | grep -q '^APKLAYER_INSTANCE=' || continue
  r=$(grep -E '^(Rss|Pss):' $p/smaps_rollup 2>/dev/null | awk '{printf "%s%s ",$1,$2}')
  echo "${p#/proc/} $(tr '\0' ' ' < $p/cmdline | awk '{print $1 " " $(NF-1) " " $NF}' | sed 's|[^ ]*/||g' | cut -c1-80) $r"
done
apklayer stop 2>&1 | tail -1
