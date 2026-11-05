#!/bin/bash
# build the named modules in ~/los24 (gsi_arm64 userdebug), one build at a time
cd ~/los24 && source build/envsetup.sh >/dev/null && breakfast gsi_arm64 userdebug >/dev/null 2>&1 && flock ~/los24-build.lock m "$@" 2>&1 | tail -${TAILN:-15}
