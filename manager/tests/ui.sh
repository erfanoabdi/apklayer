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
entry="${XDG_DATA_HOME:-$HOME/.local/share}/applications/apklayer-$pkg.desktop"
"$m" --screenshot "$shots/ui-3b-drawer.png" --size 1100x760 --app "$pkg" --click "Show in app drawer" --wait-refresh 1
grep -qx NoDisplay=true "$entry" || { echo "FAIL the drawer switch did not hide $pkg"; exit 1; }
"$m" --screenshot "$shots/ui-3c-drawer.png" --size 400x840 --app "$pkg" --click "Show in app drawer" --wait-refresh 1
! grep -qx NoDisplay=true "$entry" || { echo "FAIL the drawer switch did not show $pkg"; exit 1; }
echo "ok   the app detail's drawer switch hides and shows its launcher"
"$m" --screenshot "$shots/ui-4-uninstall.png" --size 400x840 --app "$pkg" --click "Uninstall…" --click Uninstall --wait-refresh 1
check "" "uninstalled after the confirmation"
spare="$APKLAYER_INSTANCE-ui"
"$cli" instance remove "$spare" >/dev/null 2>&1 || true
expect=()
[ -e "/dev/binderfs/binder-$spare" ] || expect=(--expect "Binder devices needed")
"$m" --screenshot "$shots/ui-5-new-instance.png" --size 400x840 --page 1 --dialog instance --type "Instance name=$spare" --click Create --wait-refresh 1 "${expect[@]}"
"$cli" instances | grep -q "^$spare	" || { echo "FAIL the new instance is not listed"; exit 1; }
echo "ok   instance created through its dialog (and the binder setup offered when its devices are missing)"
"$cli" instance remove "$spare" >/dev/null
"$cli" providers set sensors fake >/dev/null  # the Hardware page: a backend's option typed in, Apply (the card must be in view: 900 px)
"$m" --screenshot "$shots/ui-6-hardware.png" --size 1100x900 --page 4 --type "sensors accel=1,2,3" --click "Apply sensors" --wait-refresh 1
"$cli" providers | grep -q "^sensors	apklayer	fake	accel=1,2,3$" || { echo "FAIL the Hardware page did not set the sensors option"; exit 1; }
"$cli" providers unset sensors >/dev/null
echo "ok   a provider option set through the Hardware page"
"$cli" stop >/dev/null
echo "PASS manager UI"
"$(dirname "${BASH_SOURCE[0]}")/ui-updater.sh" "$@"
