#!/bin/bash
# Fennec (GeckoView) opens a local page through a VIEW intent; checks from the logs and the app's own window (apklayer screenshot, no desktop):
# the page was fetched, it drew (its red box), the GPU process got a window (no "Failed to obtain native window"), and its text drew.
# Usage (on the VM, in the caller's instance: APKLAYER_INSTANCE and the XDG_* dirs as for any run): tests/vm/fennec.sh [page] [port]
#   FENNEC_SECS (first-frame wait, default 35). The profile must be past Fenix's onboarding (a first start shows it on top of the page).
cd "$(dirname "$0")/../.." || exit 1
pkg=org.mozilla.fennec_fdroid; page=${1:-c.html}; port=${2:-18090}
work=$(mktemp -d "${XDG_STATE_HOME:-$HOME/.local/state}/fennec-check.XXXXXX"); trap 'rm -rf "$work"' EXIT
cp tests/vm/www/* "$work/"
(cd "$work" && exec python3 -m http.server "$port" --bind 127.0.0.1 > "$work/www.log" 2>&1) & web=$!
printf 'action=android.intent.action.VIEW\ndata=http://127.0.0.1:%s/%s?run=%s\ncomponent=org.mozilla.fenix.IntentReceiverActivity\n' "$port" "$page" "$$" > "$work/intent.properties"  # a new URL each run: no cached page
secs=${FENNEC_SECS:-35}
APKLAYER_INTENT_FILE="$work/intent.properties" APKLAYER_JOPTS="-Dapklayer.logcat=true -Dapklayer.runms=$(( (secs + 8) * 1000 ))" bin/apklayer run $pkg > "$work/run.out" 2>&1 &
app=$!
sleep "$secs"
bin/apklayer screenshot $pkg "$work/shot.png" > /dev/null 2>&1
wait $app; kill $web 2>/dev/null; wait $web 2>/dev/null
logs="${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}"
fail=0
check() { if [ "$2" = 1 ]; then echo "PASS $1"; else echo "FAIL $1"; fail=1; fi; }
check "page fetched" "$(grep -q "GET /$page" "$work/www.log" && echo 1)"
check "GPU process has a window" "$(grep -a -q "Failed to obtain native window" "$work/run.out" "$logs"/component-*gpu*.log 2>/dev/null || echo 1)"
read -r red dark < <(python3 - "$work/shot.png" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB'); w, h = im.size
px = [im.getpixel((x, y)) for y in range(0, h, 2) for x in range(0, w, 2)]
text = [im.getpixel((x, y)) for y in range(400, min(h, 480)) for x in range(w)]  # c.html's line of text sits below its shapes
print(sum(1 for r, g, b in px if r > 200 and g < 40 and b < 40), sum(1 for p in text if sum(p) < 200))
PY
) || { red=0; dark=0; }
check "page drew (red box: ${red:-0} px)" "$([ "${red:-0}" -gt 200 ] && echo 1)"
check "page text drew (dark: ${dark:-0} px)" "$([ "${dark:-0}" -gt 300 ] && echo 1)"
[ $fail = 0 ] || { [ -f "$work/shot.png" ] && cp "$work/shot.png" "${XDG_STATE_HOME:-$HOME/.local/state}/fennec-check-last.png"; }
exit $fail
