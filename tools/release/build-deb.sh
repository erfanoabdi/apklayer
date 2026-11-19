#!/bin/sh
# build-deb.sh <codename> <version> <prebuilts dir> <out dir>: the apklayer deb (and apklayer-dev) of this machine's CPU for one OS release (noble, trixie, resolute), the way
# .github/workflows/release.yml builds it (docs/RELEASES.md). Run in the source tree on that OS, Build-Depends installed (apt-get build-dep ./).
# Fetches Slint for the manager, writes debian/changelog (<version>-1~<codename>1) and debian/<codename>.substvars, then runs dpkg-buildpackage
# (debian/rules: the Meson build, which fetches bionic_translation and the pinned jars itself; network); the deb lands in <out dir>.
set -eu
[ $# -eq 4 ] || { sed -n '2,5p' "$0" >&2; exit 2; }
codename="$1" version="$2" P="$(cd "$3" && pwd)" out="$(mkdir -p "$4" && cd "$4" && pwd)"
cd "$(dirname "$0")/../.."
deb="$version-1~${codename}1"

[ -z "$(find "$P" -name '*.part000' | head -1)" ] || sh "$P/join-parts.sh" "$P"
slint="${APKLAYER_SLINT:-$HOME/.cache/apklayer/slint}"
manager/fetch-slint.sh "$slint"

# ANGLE (runtime/angle, README.md there): a local x86_64 build compiles it (tools/angle/build.sh) into this tree's runtime/angle, reusing its
# checkout in APKLAYER_ANGLE_WORK (the first time: about 20 GB and some hours; then only what changed). GitHub's release builds and arm64
# machines package the committed files (the angle workflow builds those; the Android x86 libraries need an x86_64 host). APKLAYER_ANGLE_BUILD=0 skips it.
# APKLAYER_REPO=<checkout>: this tree is a copy of it (build-local-deb.sh); changed ANGLE files are copied back there.
if [ "${APKLAYER_ANGLE_BUILD:-1}" != 0 ] && [ "${GITHUB_ACTIONS:-}" != true ] && [ "$(uname -m)" = x86_64 ]; then
    tools/angle/build.sh "${APKLAYER_ANGLE_WORK:-$HOME/.cache/apklayer/angle-build}" runtime/angle
    # a copy of a checkout (build-local-deb.sh): the libraries that changed go back into that checkout's runtime/angle, to be committed
    if [ -n "${APKLAYER_REPO:-}" ] && [ "$(cd "$APKLAYER_REPO" && pwd)" != "$(pwd)" ]; then
        changed=0
        for f in $(cd runtime/angle && find vendor host -name '*.so') SHA256SUMS; do
            if ! cmp -s "runtime/angle/$f" "$APKLAYER_REPO/runtime/angle/$f"; then
                mkdir -p "$(dirname "$APKLAYER_REPO/runtime/angle/$f")"
                cp "runtime/angle/$f" "$APKLAYER_REPO/runtime/angle/$f"
                echo "build-deb: updated runtime/angle/$f in $APKLAYER_REPO"
                changed=1
            fi
        done
        [ "$changed" = 1 ] || echo "build-deb: ANGLE unchanged"
    fi
fi

# per-release differences: Ubuntu keeps binder_linux in linux-modules-extra, which only its kernel metapackages pull in; Debian builds it into its kernel.
# Only the kernel binder (apklayer binder kernel) needs it, the userspace one is the default: a suggestion
case "$codename" in
    noble) printf '%s\n' 'apklayer:Depends=' 'apklayer:Suggests=linux-modules-extra-generic | linux-image-generic | linux-image-generic-hwe-24.04' ;;
    resolute) printf '%s\n' 'apklayer:Depends=' 'apklayer:Suggests=linux-modules-extra-generic | linux-image-generic' ;;
    *) printf '%s\n' 'apklayer:Depends=' 'apklayer:Suggests=' ;;
esac > "debian/$codename.substvars"
if [ -f "debian/patches/$codename/series" ]; then cp "debian/patches/$codename"/* debian/patches/; fi  # none so far

cat > debian/changelog <<EOF
apklayer ($deb) $codename; urgency=medium

  * Release $version: https://github.com/erfanoabdi/apklayer/releases/tag/v$version

 -- Erfan Abdi <erfangplus@gmail.com>  $(date -R)
EOF

APKLAYER_PREBUILTS="$P" APKLAYER_SLINT="$slint" dpkg-buildpackage -b -us -uc
mv "../apklayer_${deb}_"*.deb "../apklayer-dev_${deb}_"*.deb "$out/"
rm -f "../apklayer_${deb}_"*.buildinfo "../apklayer_${deb}_"*.changes
ls -l "$out"/apklayer_"$deb"_*.deb "$out"/apklayer-dev_"$deb"_*.deb
