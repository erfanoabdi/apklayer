#!/usr/bin/env bash
# Usage: bash tests/tools/apk-set-version.sh /path/to/aapt2 /path/to/android.jar
set -euo pipefail

aapt2=$1
android_jar=$2
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
javac -d "$scratch/classes" "$repo/runtime/host/java/apklayer/tools/ApkSet.java"

make_apk() {
    local name=$1 low=$2 major=$3 split=$4
    cat > "$scratch/AndroidManifest.xml" <<MANIFEST
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="test.apkset" android:versionCode="$low" $major $split>
    <application android:hasCode="false"/>
</manifest>
MANIFEST
    "$aapt2" link -I "$android_jar" --manifest "$scratch/AndroidManifest.xml" -o "$scratch/$name.apk"
}

stage() {
    java -cp "$scratch/classes" apklayer.tools.ApkSet \
        "$scratch/$1" x86_64 "$scratch/$2.apk" "$scratch/$3.apk"
}

reject_version() {
    if stage "$1" "$2" "$3" 2>"$scratch/error"; then
        echo "Unexpectedly accepted $2 and $3" >&2
        exit 1
    fi
    rg -Fq "$4" "$scratch/error"
}

feature='split="feature"'
major_one='android:versionCodeMajor="1"'
make_apk base 42 "$major_one" ''
make_apk matching 42 "$major_one" "$feature"
make_apk wrong-major 42 'android:versionCodeMajor="2"' "$feature"
make_apk wrong-low 43 "$major_one" "$feature"
make_apk legacy-base 42 '' ''
make_apk legacy-split 42 '' "$feature"
make_apk zero-split 42 'android:versionCodeMajor="0"' "$feature"

stage matched base matching
test -f "$scratch/matched/base.apk"
test -f "$scratch/matched/split_feature.apk"
stage legacy legacy-base legacy-split
stage explicit-zero legacy-base zero-split
reject_version different-major base wrong-major 'has long versionCode 8589934634, the others 4294967338'
reject_version missing-major base legacy-split 'has long versionCode 42, the others 4294967338'
reject_version different-low wrong-low base 'has long versionCode 4294967338, the others 4294967339'
echo 'APK-set version checks passed'
