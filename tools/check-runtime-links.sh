#!/bin/sh
# check-runtime-links.sh <dir>...: every symlink under the given trees (a package staging dir, an installed prefix, the runtime tree setup-runtime.sh
# made) must resolve when its target is relative, and must not point into a build root (/cache, /build, /tmp, the tree itself, and $APKLAYER_BUILD_ROOT, e.g. /home/builder, for a build there): such a
# link works on the build host only (the RPM's libjavacore.so pointed into /cache/prebuilts). Absolute links to system paths (/lib/..., the host's libm
# and loader) are left alone: a staging dir cannot resolve them. Exit 1 with the bad links listed; release jobs run it on the staging dir after
# `meson install` (DESTDIR) and on the runtime tree after setup.
set -u
[ $# -gt 0 ] || { echo "usage: $0 <dir>..." >&2; exit 2; }
for root in "$@"; do
    root=$(cd "$root" && pwd) || exit 2
    find "$root" -type l | while read -r l; do
        case "$l" in */bridge/native/android-root/*) continue ;; esac  # the overlay setup-runtime.sh copies into the runtime: its links resolve there
        t=$(readlink "$l")
        case "$t" in
            "$root"*|/cache/*|/build/*|/tmp/*|"${APKLAYER_BUILD_ROOT:-/nonexistent}"/*) echo "check-runtime-links: $l -> $t (build-root path)" ;;
            /*) ;;
            *) [ -e "$l" ] || echo "check-runtime-links: $l -> $t (dangling)" ;;
        esac
    done
done | grep . && exit 1
exit 0
