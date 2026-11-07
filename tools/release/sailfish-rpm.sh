#!/bin/sh
# sailfish-rpm.sh <version> <prebuilts dir> <out dir>: the Sailfish OS RPMs (aarch64) of this tree's HEAD, built in the Sailfish OS build root
# (rpm/Dockerfile: the Platform SDK's SailfishOS-5.0.0.62 aarch64 target as a native aarch64 container; docs/HALIUM.md "Building for Sailfish OS")
# the way .github/workflows/release.yml builds them: apklayer and apklayer-devel (rpm/apklayer.spec), and what Sailfish's repositories lack:
# temurin-21-jdk, libmd, libbsd, libunwind (rpm/*.spec, sources in rpm/sources). Needs docker on an aarch64 host (the Mac's OrbStack, GitHub's
# ubuntu-24.04-arm), 7z (or 7zz), curl; downloads go to $APKLAYER_SAILFISH_CACHE (default ~/.cache/apklayer/sailfish) once. The RPMs land in <out dir>.
set -eu
if [ "${1:-}" = --in-root ]; then  # inside the build root: <version>; /work the tree, /cache the sources and prebuilts, /out the RPMs
    version="$2" top=/root/rpmbuild
    mkdir -p "$top/SOURCES" && cp /cache/sources/* /cache/apklayer-"$version".tar.gz "$top/SOURCES/"
    # what Sailfish lacks, in dependency order; one already in <out dir> (same version) is reused
    for spec in temurin-21-jdk libmd libbsd libunwind; do
        v="$(rpmspec -q --qf '%{version}-%{release}\n' "/work/rpm/$spec.spec" | head -1)"
        [ -e "/out/$spec-$v.aarch64.rpm" ] || { rpmbuild -bb --define "_topdir $top" "/work/rpm/$spec.spec" && cp "$top"/RPMS/*/"$spec"*-"$v".aarch64.rpm /out/; }
        rm -f /out/*-debuginfo-* /out/*-debugsource-*
        zypper -n --no-gpg-checks in --allow-unsigned-rpm /out/"$spec"*-"$v".aarch64.rpm
    done
    set -- --define "_topdir $top" --define "apklayer_version $version" --define "apklayer_prebuilts /cache/prebuilts" /work/rpm/apklayer.spec
    zypper -n in $(rpmspec -q --buildrequires "$@" | sed 's/ .*//' | sort -u)
    rpmbuild -bb "$@"
    cp "$top"/RPMS/*/apklayer-*"$version"-*.rpm /out/
    exit 0
fi
[ $# -eq 3 ] || { sed -n '2,7p' "$0" >&2; exit 2; }
version="$1" P="$(cd "$2" && pwd)" out="$(mkdir -p "$3" && cd "$3" && pwd)"
cd "$(dirname "$0")/../.."
src="$(pwd)" cache="${APKLAYER_SAILFISH_CACHE:-$HOME/.cache/apklayer/sailfish}"
image=apklayer-sailfish-build:5.0.0.62  # the release of rpm/sources' SDK target
mkdir -p "$cache/sources"

# the sources (rpm/sources), each checked
grep -v '^#' rpm/sources | while read -r sum url; do
    f="$cache/sources/$(basename "$url")"
    case "$f" in *SDK_Target*) f="$cache/target.tar.7z" ;; esac
    [ -s "$f" ] || curl -sfL --retry 5 -o "$f" "$url"
    case "$sum" in
        md5:*) have="md5:$(if command -v md5sum >/dev/null; then md5sum "$f" | cut -d' ' -f1; else md5 -q "$f"; fi)" ;;
        *) have="$(sha256sum "$f" 2>/dev/null || shasum -a 256 "$f")"; have="${have%% *}" ;;
    esac
    [ "$have" = "$sum" ] || { echo "sailfish-rpm: $f: checksum $have, want $sum" >&2; rm -f "$f"; exit 1; }
done

# the build root image, once per release
if ! docker image inspect "$image" >/dev/null 2>&1; then
    ctx="$cache/image"
    mkdir -p "$ctx"
    [ -s "$ctx/target.tar" ] || "$(command -v 7z || command -v 7zz)" x -y -so "$cache/target.tar.7z" > "$ctx/target.tar"
    cp rpm/Dockerfile "$ctx/"
    docker build --platform linux/arm64 -t "$image" "$ctx"
    rm -f "$ctx/target.tar"
fi

# the prebuilts' common and arm64 parts, joined (join-parts.sh) in a copy: the checkout stays as it is
mkdir -p "$cache/prebuilts"
for d in common arm64; do rsync -a --delete "$P/$d/" "$cache/prebuilts/$d/"; done
find "$cache/prebuilts" -name '*.part000' | while read -r first; do b="${first%.part000}"; cat "$b".part??? > "$b" && rm -f "$b".part???; done

git archive --prefix="apklayer-$version/" -o "$cache/apklayer-$version.tar.gz" HEAD
# the Java libraries this machine already fetched (fetch-dep.py looks in ~/.cache/apklayer/deps): no second download from Maven
deps="$HOME/.cache/apklayer/deps"; mkdir -p "$deps"
docker run --rm --platform linux/arm64 -v "$src:/work:ro" -v "$deps:/root/.cache/apklayer/deps:ro" -v "$cache:/cache" -v "$out:/out" "$image" sh /work/tools/release/sailfish-rpm.sh --in-root "$version"
rm -f "$cache/apklayer-$version.tar.gz"
ls -l "$out"/*.rpm
