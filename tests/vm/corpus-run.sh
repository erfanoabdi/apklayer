#!/bin/bash
# On the VM: install and run each given APK (by package name, from DIR) and print a one-line verdict per app (errors, alive after SECS).
# Usage: tests/vm/corpus-run.sh DIR pkg...      CORPUS_SECS=22
cd "$(dirname "$0")/../.." || exit 1
dir=$1; shift
for pkg in "$@"; do
  inst=$(bin/apklayer install "$dir/$pkg.apk" 2>&1 | grep -v "wrapped\|log 6" | tail -1 | cut -c1-80)
  case "$inst" in *installed*) ;; *) echo "== $pkg: INSTALL FAILED: $inst"; continue;; esac
  echo "== $pkg"
  APPCHECK_SECS=${CORPUS_SECS:-22} APPCHECK_LINES=4 tests/vm/appcheck.sh "$pkg" 2>&1 | cut -c1-200
done
