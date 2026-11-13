#!/bin/sh
# tools/device-profile.sh <profile>|off [instance]: which device an instance presents. The default is the Pixel 5 (bridge/native/apklayer.prop). A profile from
# bridge/native/profiles/<profile>.prop (pixel5, desktop) is written as instance property overrides (`apklayer prop set`; ro.* are write-once and the overrides
# load first, so they win), from the instance's next start; `off` removes every override this script wrote, back to the default. The CPU half is the property
# berberis.cpuinfo (the translator's /proc/cpuinfo and MIDR_EL1): `pixel5` or, for any other profile, its name (the translator's generic core).
set -eu
here="$(cd "$(dirname "$0")" && pwd)"; dir="$here/../bridge/native/profiles"
[ $# -ge 1 ] || { echo "usage: $0 <profile>|off [instance]" >&2; exit 2; }
profile=$1; shift
inst=""; [ $# -lt 1 ] || inst="--instance $1"
keys() { grep -hE '^[a-z]' "$dir"/*.prop | cut -d= -f1 | sort -u; }
if [ "$profile" = off ]; then
  for k in $(keys) berberis.cpuinfo; do apklayer $inst prop unset "$k" >/dev/null; done
  echo "device profile: the default (Pixel 5)"; exit 0
fi
[ -f "$dir/$profile.prop" ] || { echo "no profile $profile (have: $(cd "$dir" && ls *.prop | sed 's/\.prop$//' | tr '\n' ' '))" >&2; exit 1; }
grep -E '^[a-z]' "$dir/$profile.prop" | while IFS== read -r k v; do apklayer $inst prop set "$k" "$v" >/dev/null; done
apklayer $inst prop set berberis.cpuinfo "$profile" >/dev/null
echo "device profile $profile set; restart the instance (apklayer stop)"
