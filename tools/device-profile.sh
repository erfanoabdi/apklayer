#!/bin/sh
# tools/device-profile.sh <profile>|off [instance]: the instance presents a device from bridge/native/profiles/<profile>.prop (Build.*, the ro.product.*
# of every partition, the SoC): each line becomes an instance property override (`apklayer prop set`), from the instance's next start. `off` removes
# every key the profiles define. Only the property half; the translator's /proc/cpuinfo is berberis.cpuinfo (docs/PLAN.md "Device profile").
set -eu
here="$(cd "$(dirname "$0")" && pwd)"; dir="$here/../bridge/native/profiles"
[ $# -ge 1 ] || { echo "usage: $0 <profile>|off [instance]" >&2; exit 2; }
profile=$1; shift
inst=""; [ $# -lt 1 ] || inst="--instance $1"
keys() { grep -hE '^[a-z]' "$dir"/*.prop | cut -d= -f1 | sort -u; }
if [ "$profile" = off ]; then
  for k in $(keys); do apklayer $inst prop unset "$k" >/dev/null; done
  apklayer $inst prop unset berberis.cpuinfo >/dev/null
  echo "device profile off"; exit 0
fi
[ -f "$dir/$profile.prop" ] || { echo "no profile $profile (have: $(cd "$dir" && ls *.prop | sed 's/\.prop$//' | tr '\n' ' '))" >&2; exit 1; }
grep -E '^[a-z]' "$dir/$profile.prop" | while IFS== read -r k v; do apklayer $inst prop set "$k" "$v" >/dev/null; done
apklayer $inst prop set berberis.cpuinfo "$profile" >/dev/null
echo "device profile $profile set; restart the instance (apklayer stop)"
