#!/bin/sh
# build-deb.sh <codename> <version> <prebuilts dir> <out dir>: the apklayer deb of this machine's CPU for one OS release (noble, trixie), the way
# .github/workflows/release.yml builds it (docs/RELEASES.md). Run in the source tree on that OS, Build-Depends installed (apt-get build-dep ./).
# Fetches the build inputs (network) into $APKLAYER_RELEASE_INPUTS (default ~/.cache/apklayer/release), writes debian/changelog
# (<version>-1~<codename>1) and debian/<codename>.substvars, then runs dpkg-buildpackage; the deb lands in <out dir>.
set -eu
[ $# -eq 4 ] || { sed -n '2,5p' "$0" >&2; exit 2; }
codename="$1" version="$2" P="$(cd "$3" && pwd)" out="$(mkdir -p "$4" && cd "$4" && pwd)"
cd "$(dirname "$0")/../.."
IN="${APKLAYER_RELEASE_INPUTS:-$HOME/.cache/apklayer/release}"
mkdir -p "$IN"
deb="$version-1~${codename}1"

[ -z "$(find "$P" -name '*.part000' | head -1)" ] || sh "$P/join-parts.sh" "$P"
bt_rev="$(cat patches/bionic_translation/REVISION)"
[ -d "$IN/bionic_translation/.git" ] || git clone -q https://gitlab.com/android_translation_layer/bionic_translation "$IN/bionic_translation"
git -C "$IN/bionic_translation" checkout -q "$bt_rev"
APKLAYER_DEPS_LOCAL="$P/common" APKLAYER_DEPS_SKIP='android-all/*' tools/fetch-deps.sh "$IN/deps"
manager/fetch-slint.sh "$IN/slint"

# per-release differences: Ubuntu keeps binder_linux in linux-modules-extra, which only its kernel metapackages pull in; Debian builds it into its kernel
# a recommendation, not a dependency: a hard one would pull the generic kernel onto systems running another flavour (lowlatency, cloud, OEM, a custom one)
case "$codename" in
    noble) printf '%s\n' 'apklayer:Depends=' 'apklayer:Recommends=linux-modules-extra-generic | linux-image-generic | linux-image-generic-hwe-24.04' ;;
    *) printf '%s\n' 'apklayer:Depends=' 'apklayer:Recommends=' ;;
esac > "debian/$codename.substvars"
if [ -f "debian/patches/$codename/series" ]; then cp "debian/patches/$codename"/* debian/patches/; fi  # none so far

cat > debian/changelog <<EOF
apklayer ($deb) $codename; urgency=medium

  * Release $version: https://github.com/erfanoabdi/apklayer/releases/tag/v$version

 -- Erfan Abdi <erfangplus@gmail.com>  $(date -R)
EOF

APKLAYER_PREBUILTS="$P" APKLAYER_BT_SRC="$IN/bionic_translation" APKLAYER_DEPS="$IN/deps" APKLAYER_SLINT="$IN/slint" \
    dpkg-buildpackage -b -us -uc
mv "../apklayer_${deb}_"*.deb "$out/"
rm -f "../apklayer_${deb}_"*.buildinfo "../apklayer_${deb}_"*.changes
ls -l "$out"/apklayer_"$deb"_*.deb
