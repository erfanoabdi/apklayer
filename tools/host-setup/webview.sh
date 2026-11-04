#!/usr/bin/env bash
# webview-setup [provider.apk]: one-time host setup for the WebView provider route (needs sudo once); with an APK (com.android.webview) also installs it
# into the current instance's data home (XDG_DATA_HOME), which the webview test group needs.
# Chromium's renderer runs inside an app JVM (an isolated service process of the hub), where its seccomp-bpf sandbox cannot work: the filter applies to the whole
# process and kills the JVM (SIGSYS) on its first syscall outside the renderer's allow-list. The sandbox is therefore switched off for the provider route
# (user decision, 2026-10-27: "Disable the seccomp sandbox totally"); every app JVM shares one Linux uid anyway, so Chromium's sandbox could not be claimed.
# WebView reads extra Chromium switches from /data/local/tmp/webview-command-line on a debuggable build (the runtime reports Build.TYPE userdebug while the provider initialises, ShadowHostWebViewFactory.getProvider).
set -euo pipefail
sudo mkdir -p /data/local/tmp
sudo chmod 1777 /data /data/local /data/local/tmp
f=/data/local/tmp/webview-command-line line="webview --no-sandbox --disable-seccomp-filter-sandbox"
if [ "$(cat "$f" 2>/dev/null)" != "$line" ]; then
    sudo rm -f "$f"  # fs.protected_regular: root cannot reopen another user's file in the sticky directory, only replace it
    echo "$line" | sudo tee "$f" >/dev/null
    sudo chmod 644 "$f"
fi
echo "webview-setup: $f in place (seccomp sandbox off)"
[ $# -eq 0 ] || "$(dirname "$0")/../../bin/apklayer" install "$1"
