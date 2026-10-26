#!/bin/sh
# aosp-build: build modules in the LineageOS tree in OrbStack (docs/AOSP-BUILDS.md: log each build there first; never touch out/).
#   scripts/aosp-build.sh <module>...        full build step: Soong analysis + compile (--soong-only: our modules have no Make deps)
#   scripts/aosp-build.sh --ninja <output>...  source-only edits: straight to ninja, no analysis, all errors at once (-k 0). Outputs are
#                                              e.g. out/soong/.intermediates/vendor/apklayer/hals/graphics/egl/libapklayer_egl/android_arm64_armv8-a_shared/libapklayer_egl.so
# The environment of `breakfast gsi_arm64 userdebug` (45 s) is cached in ~/.apklayer-aosp.env after the first use.
# The log goes to ~/apklayer-aosp-build.last.log; the last lines (errors, result) are printed.
set -u
mode=soong
[ "${1:-}" = --ninja ] && { mode=ninja; shift; }
orb -m ubuntu22-x86 bash -c '
cd ~/los24 || exit 1
ENVF=~/.apklayer-aosp.env
if [ ! -f $ENVF ]; then
    . build/envsetup.sh >/dev/null 2>&1 && breakfast gsi_arm64 userdebug >/dev/null 2>&1 || { echo "breakfast failed"; exit 1; }
    export -p > $ENVF
fi
. $ENVF
LOG=~/apklayer-aosp-build.last.log
if [ "$0" = ninja ]; then
    prebuilts/build-tools/linux-x86/bin/ninja -f out/soong/build.lineage_gsi_arm64.ninja -k 0 "$@" > $LOG 2>&1
else
    build/soong/soong_ui.bash --make-mode --soong-only "$@" > $LOG 2>&1
fi
rc=$?
grep -a "error:\|FAILED\|build completed\|Build Succeeded" $LOG | head -20 | cut -c1-500
echo "exit=$rc (log: $LOG)"
' "$mode" "$@"
