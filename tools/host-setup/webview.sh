#!/usr/bin/env bash
# webview-setup <provider.apk>: installs the WebView provider (com.android.webview) as a system app into the current instance's data home
# (XDG_DATA_HOME), which the webview test group needs. No root: Chromium's switch file that turns its seccomp sandbox off (it cannot work inside an
# app JVM: the filter applies to the whole process and kills the JVM with SIGSYS; user decision 2026-10-27, "Disable the seccomp sandbox totally")
# is the instance's own /data/local/tmp/webview-command-line, written by the CLI at each launch (cli/run.cpp webviewSandboxOff). Before 2026-11-05
# this script wrote the host's /data/local/tmp with sudo; that file is no longer read (remove it: sudo rm -f /data/local/tmp/webview-command-line).
set -euo pipefail
[ $# -eq 1 ] || { sed -n '2,3p' "$0" >&2; exit 2; }
"$(dirname "$0")/../../bin/apklayer" install --system "$1"  # the WebView provider is a system app (tools/sysapps.list)
