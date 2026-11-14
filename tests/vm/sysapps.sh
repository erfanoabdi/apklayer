#!/bin/sh
# tests/vm/sysapps.sh <package>...: installs the system apps among them that this data home lacks, from $APKLAYER_PREBUILTS/<arch>/system-apps
# (tools/sysapps.list names the APK; the WebView provider is the split webview.apk.partNNN). Others (user-supplied APKs) are left: tests/run skips those groups.
set -eu
cd "$(dirname "$0")/../.."
dir="$APKLAYER_PREBUILTS/$(case "$(uname -m)" in aarch64) echo arm64;; *) echo x86_64;; esac)/system-apps"
have="$(bin/apklayer list 2>/dev/null || true)"
for pkg in "$@"; do
    echo "$have" | grep -q "$pkg" && continue
    if [ "$pkg" = com.android.webview ]; then
        if [ -f "$dir/webview.apk" ]; then apk="$dir/webview.apk"; else
            [ -f "$dir/webview.apk.part000" ] || continue
            apk="$(mktemp)"; cat "$dir"/webview.apk.part??? > "$apk"
        fi
    else
        name="$(grep -v '^#' tools/sysapps.list | awk -F'\t' -v p="$pkg" '$1 == p && $2 != "-" {n = split($2, a, "/"); sub(/\.apk$/, "", a[n]); print a[n]}')"
        apk="$dir/$name.apk"
        [ -n "$name" ] && [ -f "$apk" ] || continue
    fi
    bin/apklayer install --system "$apk" 2>&1 | grep -a 'installed\|rror\|xception' | head -3 || true
    [ "$apk" = "$dir/webview.apk" ] || [ "$pkg" != com.android.webview ] || rm -f "$apk"
done
