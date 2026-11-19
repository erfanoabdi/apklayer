#!/bin/sh
# build-local-deb.sh <out dir> [codename]: the deb of this checkout's HEAD for this machine (default codename: this OS's), built in a clean copy
# (git archive into a temporary directory beside <out dir>, removed afterwards) with tools/release/build-deb.sh and the prebuilts in
# APKLAYER_PREBUILTS (default ~/apklayer-build-deb/prebuilts). On x86_64 that build compiles ANGLE too; libraries that changed are copied
# back into this checkout's runtime/angle (and SHA256SUMS), ready to commit. Version: <latest v tag>+local<date>.git<sha>.
set -eu
[ $# -ge 1 ] || { sed -n '2,6p' "$0" >&2; exit 2; }
repo=$(cd "$(dirname "$0")/../.." && pwd)
out=$(mkdir -p "$1" && cd "$1" && pwd)
codename=${2:-$(. /etc/os-release && echo "$VERSION_CODENAME")}
prebuilts=${APKLAYER_PREBUILTS:-$HOME/apklayer-build-deb/prebuilts}
sha=$(git -C "$repo" rev-parse --short=8 HEAD)
base=$(git -C "$repo" describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null | sed 's/^v//')
version="${base:-0.0.0}+local$(date +%Y%m%d).git$sha"
work=$(mktemp -d "$(dirname "$out")/deb-$sha.XXXXXX")
trap 'rm -rf "$work"' EXIT
git -C "$repo" archive HEAD | tar x -C "$work"
cd "$work"
APKLAYER_REPO="$repo" tools/release/build-deb.sh "$codename" "$version" "$prebuilts" "$out"
