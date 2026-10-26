#!/bin/sh
# sync-aosp: copy apklayer's AOSP-side sources (aosp/) into the tree at ~/los24/vendor/apklayer (OrbStack), where
# `m <module>` builds them. Only that directory is written; never touches out/.
set -e
cd "$(dirname "$0")/.."
orb -m ubuntu22-x86 mkdir -p /home/erfanabdi/los24/vendor/apklayer
tar cf - -C aosp . | orb -m ubuntu22-x86 tar xf - -C '/home/erfanabdi/los24/vendor/apklayer'
