#!/usr/bin/env bash
# webview-setup: one-time host setup for the WebView provider route (needs sudo once).
# Chromium's renderer runs inside an app JVM (an isolated service process of the hub), where its seccomp-bpf sandbox cannot work: the filter applies to the whole
# process and kills the JVM (SIGSYS) on its first syscall outside the renderer's allow-list. The sandbox is therefore switched off for the provider route
# (user decision, 2026-10-27: "Disable the seccomp sandbox totally"); every app JVM shares one Linux uid anyway, so Chromium's sandbox could not be claimed.
# WebView reads extra Chromium switches from /data/local/tmp/webview-command-line on a debuggable build (the runtime reports Build.TYPE userdebug on this route).
set -euo pipefail
sudo mkdir -p /data/local/tmp
sudo chmod 1777 /data /data/local /data/local/tmp
echo "webview --no-sandbox --disable-seccomp-filter-sandbox" | sudo tee /data/local/tmp/webview-command-line >/dev/null
sudo chmod 644 /data/local/tmp/webview-command-line
echo "webview-setup: /data/local/tmp/webview-command-line written (seccomp sandbox off)"
