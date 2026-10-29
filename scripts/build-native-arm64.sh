#!/bin/sh
# the arm64 name of scripts/build-native.sh (kept for the notes and habits that use it)
exec "$(dirname "$0")/build-native.sh" "$@"
