#!/bin/bash
# ut-shot.sh PKG OUT.png [JOPTS]: on a phone, one app runs RUNMS (default 25000), SurfaceFlinger's composition of it is saved as OUT.png at the
# run's end (the log line "ending: run time", no sleeping), then the stack stops. INSTANCE (default ut), APKLAYER (the CLI), TRIGGER (a regex).
# Apps that need a permission: `apklayer permissions grant PKG android.permission.X` first. The app's log is OUT.png.log.
A=${APKLAYER:-$(command -v apklayer || echo /opt/click.ubuntu.com/apklayer.erfanoabdi/current/usr/bin/apklayer)}
A="$A --instance ${INSTANCE:-ut}"
pkg=$1 out=$2
export APKLAYER_JOPTS="-Dapklayer.runms=${RUNMS:-25000} -Dapklayer.tracewindows=true ${3:-}"
rm -f "$out"; : > "$out.log"; shot=0
while IFS= read -r line; do
    printf '%s\n' "$line" >> "$out.log"
    if [ $shot = 0 ] && printf '%s' "$line" | grep -qE "${TRIGGER:-ending: run time}"; then shot=1; $A screenshot "$pkg" "$out" >/dev/null 2>&1 && echo "SHOT $out"; fi
done < <($A run "$pkg" 2>&1)
$A stop >/dev/null 2>&1
[ -s "$out" ] || { echo "no screenshot"; exit 1; }
