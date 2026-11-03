#!/bin/bash
# apklayer-manager driven through its own UI (the screenshot mode's scripted input): install, open, stop and uninstall an app, in the instance
# $APKLAYER_INSTANCE (never the default one). Each step ends on its own event (the action's refresh), then the CLI checks the result.
# Usage: manager/tests/ui.sh <manager build dir> <app.apk> <package> <screenshot dir>
set -euo pipefail
b=$1 apk=$2 pkg=$3 shots=$4
[ -n "${APKLAYER_INSTANCE:-}" ] && [ "$APKLAYER_INSTANCE" != default ] || { echo "run in an instance of your own (APKLAYER_INSTANCE)" >&2; exit 2; }
cli="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/bin/apklayer"
m="$b/apklayer-manager"
mkdir -p "$shots"
check() { [ "$(running)" = "$1" ] || { echo "FAIL $2 (running: '$(running)')"; exit 1; }; echo "ok   $2"; }
running() { "$cli" list --tsv | awk -F'\t' -v p="$pkg" '$1 == p { print $6 }'; }
"$cli" uninstall "$pkg" >/dev/null 2>&1 || true

"$m" --screenshot "$shots/ui-1-install.png" --size 400x840 --dialog install --type "APK path=$apk" --click Install --wait-refresh 1
check 0 "installed through the install dialog"
"$m" --screenshot "$shots/ui-2-open.png" --size 1100x760 --app "$pkg" --click Open --wait-refresh 1
check 1 "opened from the app's detail: it runs"
"$m" --screenshot "$shots/ui-3-stop.png" --size 1100x760 --app "$pkg" --click Stop --wait-refresh 1
check 0 "stopped from the app's detail"
"$m" --screenshot "$shots/ui-4-uninstall.png" --size 400x840 --app "$pkg" --click "Uninstall…" --click Uninstall --wait-refresh 1
check "" "uninstalled after the confirmation"
"$cli" stop >/dev/null
echo "PASS manager UI"
