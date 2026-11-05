#!/bin/sh
# sync-aosp: mirror apklayer's AOSP-side sources (aosp/) into the tree at ~/los24/vendor/apklayer (OrbStack), where
# `m <module>` builds them, and commit the change there (vendor/apklayer has its own git history).
# Usage: tools/sync-aosp.sh "commit message" [path under aosp/ ...].  Only that directory is written; never touches out/.
# With paths, only those subtrees are replaced (parallel worktrees: a whole-tree sync deletes another worktree's new modules mid-build).
set -e
cd "$(dirname "$0")/.."
msg="${1:-sync from apklayer $(git rev-parse --short HEAD)}"
dest=/home/erfanabdi/los24/vendor/apklayer
orb -m ubuntu22-x86 mkdir -p "$dest"
shift || true
if [ $# -gt 0 ]; then
    for p in "$@"; do
        [ -e "aosp/$p" ] || { echo "sync-aosp: no aosp/$p" >&2; exit 1; }
        orb -m ubuntu22-x86 rm -rf "$dest/$p"
        tar cf - -C aosp "$p" | orb -m ubuntu22-x86 tar xf - -C "$dest"
    done
else
    orb -m ubuntu22-x86 find "$dest" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
    tar cf - -C aosp . | orb -m ubuntu22-x86 tar xf - -C "$dest"
fi
# the host HALs' C headers, which the Android-side code includes (the host side is the single source)
for hal in bridge/hals/*/include; do
    d=$(basename "$(dirname "$hal")")
    orb -m ubuntu22-x86 mkdir -p "$dest/hals/$d/host-include"
    tar cf - -C "$hal" . | orb -m ubuntu22-x86 tar xf - -C "$dest/hals/$d/host-include"
    tar cf - -C bridge/hals/include . | orb -m ubuntu22-x86 tar xf - -C "$dest/hals/$d/host-include"  # apklayer/plugin.h, which they include
done
# the message travels through stdin into a file (any character is safe in it), then git commit -F
printf '%s\n\nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>\n' "$msg" | orb -m ubuntu22-x86 sh -c "cat > /tmp/apklayer-sync-msg"
orb -m ubuntu22-x86 sh -c "cd $dest && { [ -d .git ] || git init -q; } && git add -A && { git diff --cached --quiet || git commit -q -F /tmp/apklayer-sync-msg; } && git log --oneline | head -1"
