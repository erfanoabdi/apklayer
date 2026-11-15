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
# an instance without apps shows the first-run checklist over every page (the manager's Setup::firstRun) unless the setup marker exists: ui.sh ends with no app
marker="${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/setup-done"
[ -e "$marker" ] || { mkdir -p "$(dirname "$marker")"; touch "$marker"; trap 'rm -f "$marker"' EXIT; }
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
# the app detail's Permissions card: the first runtime (dangerous) permission's switch flips its grant (an app without one: skipped)
perm=$("$cli" permissions "$pkg" 2>/dev/null | awk -F'\t' '$2 == "dangerous" { print $1, $3; exit }') || true
if [ -n "$perm" ]; then
    name=${perm% *} was=${perm#* } title=${name##*.}
    title="$(echo "${title:0:1}" | tr a-z A-Z)$(echo "${title:1}" | tr A-Z a-z | tr _ ' ')"
    "$m" --screenshot "$shots/ui-3d-permission.png" --size 1100x1600 --scroll 1500 --app "$pkg" --click "Allow $title" --wait-refresh 1
    now=$("$cli" permissions "$pkg" | awk -F'\t' -v n="$name" '$1 == n { print $3 }')
    [ "$now" != "$was" ] || { echo "FAIL the Permissions switch did not change $name (still $was)"; exit 1; }
    echo "ok   the app detail's permission switch changed $name ($was -> $now)"
fi
"$m" --screenshot "$shots/ui-4-uninstall.png" --size 400x840 --app "$pkg" --click "Uninstall…" --click Uninstall --wait-refresh 1
check "" "uninstalled after the confirmation"
spare="$APKLAYER_INSTANCE-ui"
"$cli" instance remove "$spare" >/dev/null 2>&1 || true
expect=()
[ "${APKLAYER_BINDER:-}" != kernel ] || [ -e "/dev/binderfs/binder-$spare" ] || expect=(--expect "Binder devices needed")  # the userspace binder (the default) needs none
"$m" --screenshot "$shots/ui-5-new-instance.png" --size 400x840 --page 1 --dialog instance --type "Instance name=$spare" --click Create --wait-refresh 1 "${expect[@]}"
"$cli" instances | grep -q "^$spare	" || { echo "FAIL the new instance is not listed"; exit 1; }
echo "ok   instance created through its dialog (and the binder setup offered when its devices are missing)"
"$cli" instance remove "$spare" >/dev/null
# the Hardware page lists a card per domain (only what is on screen is found: the later ones after --scroll) and a domain whose host has no device says so
"$m" --screenshot "$shots/ui-6-domains.png" --size 1100x900 --page 4 --expect "Apply sensors" --expect "Apply gnss"
"$m" --screenshot "$shots/ui-6-domains2.png" --size 1100x900 --page 4 --scroll 1300 --expect "Apply audio" --expect "Apply graphics" --expect "Apply media"
if [ "$("$cli" providers | awk -F'\t' '$1 == "default" && $2 == "sensors" { print $3 }')" = "(none: off)" ]; then
    "$m" --screenshot "$shots/ui-6-nodevice.png" --size 1100x900 --page 4 --expect "Automatic: off"
fi
echo "ok   the Hardware page lists its providers (and says 'Automatic: off' for sensors where the host has none)"
"$cli" providers set sensors fake >/dev/null  # the Hardware page: a backend's option typed in, Apply (the card must be in view: 900 px)
"$m" --screenshot "$shots/ui-6-hardware.png" --size 1100x900 --page 4 --type "sensors accel=1,2,3" --click "Apply sensors" --wait-refresh 1
"$cli" providers | grep -q "^sensors	apklayer	fake	accel=1,2,3$" || { echo "FAIL the Hardware page did not set the sensors option"; exit 1; }
"$cli" providers unset sensors >/dev/null
echo "ok   a provider option set through the Hardware page"
if [ -z "${APKLAYER_BINDER:-}" ]; then  # the Hardware page's binder switch (the environment would override the instance's choice)
    "$cli" binder | grep -q "^binder	userspace	" || { echo "FAIL the instance is not on the userspace binder by default"; exit 1; }
    "$m" --screenshot "$shots/ui-7-binder.png" --size 1100x900 --page 4 --click "Kernel binder" --wait-refresh 1
    "$cli" binder | grep -q "^binder	kernel	instance$" || { echo "FAIL the Hardware page did not choose the kernel binder"; exit 1; }
    expect=()
    "$cli" binder | grep -q "^kernel	ready" || expect=(--expect "Kernel binder setup command")
    "$m" --screenshot "$shots/ui-8-binder.png" --size 1100x900 --page 4 "${expect[@]}"
    "$m" --screenshot "$shots/ui-9-binder.png" --size 1100x900 --page 4 --click "Kernel binder" --wait-refresh 1
    "$cli" binder | grep -q "^binder	userspace	instance$" || { echo "FAIL the Hardware page did not choose the userspace binder again"; exit 1; }
    echo "ok   the binder chosen through the Hardware page (and the kernel's setup command shown when its devices are missing)"
fi
# the Properties page's navigation-bar row (navbar auto|on|off): "On" is stored in the instance's settings
"$m" --screenshot "$shots/ui-10-navbar.png" --size 1100x900 --page 2 --click On --wait-refresh 1
"$cli" settings | grep -qx "navbar	on" || { echo "FAIL the navigation bar row did not set navbar on"; exit 1; }
"$cli" settings set navbar auto >/dev/null
echo "ok   the navigation bar setting through the Properties page"
# the Instances page's GPU rows (only where the host has several GPUs): choose the one the desktop does not use, then "Automatic" again
if [ "$("$cli" gpus | tail -n +2 | grep -c .)" -gt 1 ]; then
    IFS=$'\t' read -r gid gname < <("$cli" gpus | awk -F'\t' 'NR > 1 && $5 == "no" { print $1 "\t" $7; exit }')
    auto="Automatic (the desktop's GPU)"  # the first row (no explicit choice)
    chosen() { "$cli" gpus | awk -F'\t' -v id="$1" '$1 == id && $8 == "yes" { f = 1 } END { exit !f }'; }
    "$m" --screenshot "$shots/ui-11-gpu.png" --size 1100x900 --page 1 --expect "GPU of $APKLAYER_INSTANCE" --click "$gname" --wait-refresh 1
    chosen "$gid" || { echo "FAIL the GPU rows did not choose $gid"; exit 1; }
    "$m" --screenshot "$shots/ui-12-gpu.png" --size 1100x900 --page 1 --click "$auto" --wait-refresh 1
    ! chosen "$gid" || { echo "FAIL the GPU rows' Automatic did not unset the choice"; exit 1; }
    echo "ok   the Instances page's GPU rows chose $gname and Automatic again"
fi
"$cli" stop >/dev/null
echo "PASS manager UI"
"$(dirname "${BASH_SOURCE[0]}")/ui-updater.sh" "$@"
