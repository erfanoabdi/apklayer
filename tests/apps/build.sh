#!/usr/bin/env bash
# Builds a sample APK from tests/apps/<name>/ (AndroidManifest.xml, src/**/*.java, optional *.c -> libapp.so for x86_64)
# with the local Android SDK + NDK. Usage: build.sh <name> [out.apk]  (default build/<name>.apk)
# Optional in the app's directory: split.list (lines "<split>:<aapt2 config>": config splits, written beside the APK as <out>-split_<split>.apk),
# sign (a key name: the APKs are signed with build/keys/<name>.jks, made on first use; a second name: rotated from that key), embed.list (test apps built into this one's assets).
set -euo pipefail
NAME="$1"; SELF="$(cd "$(dirname "$0")" && pwd)/build.sh"; cd "$(dirname "$0")/$NAME"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK=$(ls -d "$SDK"/ndk/* | tail -1); BT=$(ls -d "$SDK"/build-tools/* | tail -1); AJ="$SDK/platforms/android-36.1/android.jar"
OUT="${2:-$PWD/../../../build/$NAME.apk}"; W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$W/cls" "$W/aidl" "$(dirname "$OUT")"
LIBS=""
if ls *.c >/dev/null 2>&1; then
  mkdir -p "$W/lib/x86_64"
  "$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/x86_64-linux-android26-clang)" -shared -fPIC -O2 -o "$W/lib/x86_64/libhello.so" *.c -lEGL -lGLESv2 -landroid -llog
  LIBS="lib/x86_64/libhello.so"
fi
if [ -f aidl.list ]; then
  while IFS= read -r source; do
    [ -n "$source" ] || continue
    "$BT/aidl" -p"$SDK/platforms/android-36.1/framework.aidl" -I"$PWD/../aidl" -o"$W/aidl" "$PWD/../aidl/$source"
  done < aidl.list
fi
javac -nowarn -d "$W/cls" -cp "$AJ" $(find -H src "$W/aidl" -name '*.java')
"$BT"/d8 --min-api 26 --lib "$AJ" --output "$W" $(find "$W/cls" -name '*.class')
RES=()
if [ -d res ]; then "$BT"/aapt2 compile --dir res -o "$W/res.zip"; RES=("$W/res.zip"); fi  # an app with resources (its own 0x7f package)
ASSETS=()
[ ! -d assets ] || ASSETS=(-A "$PWD/assets")
if [ -f embed.list ]; then  # other test APKs (and their splits) as assets/<app>.apk
  mkdir -p "$W/embedded"; ASSETS+=(-A "$W/embedded")
  while IFS= read -r app; do [ -z "$app" ] || "$SELF" "$app" "$W/embedded/$app.apk" >/dev/null; done < embed.list
fi
SPLITS=()
if [ -f split.list ]; then while IFS=: read -r split config; do [ -z "$split" ] || SPLITS+=(--split "$W/split_$split.apk:$config"); done < split.list; fi
"$BT"/aapt2 link ${ASSETS[@]+"${ASSETS[@]}"} -o "$W/base.apk" -I "$AJ" ${RES[@]+"${RES[@]}"} ${SPLITS[@]+"${SPLITS[@]}"} --manifest AndroidManifest.xml
(cd "$W" && zip -q base.apk classes.dex $LIBS)
sign() { cp "$1" "$2"; }
key() { local ks="$PWD/../../../build/keys/$1.jks"; mkdir -p "$(dirname "$ks")"
  [ -f "$ks" ] || keytool -genkeypair -keystore "$ks" -alias key -storepass apklayer -keypass apklayer -dname "CN=$1" -keyalg RSA -validity 10000 -noprompt >/dev/null 2>&1
  echo "$ks"; }
if [ -f sign ]; then
  read -r KEY PAST < sign; KS=$(key "$KEY"); SIGNERS=(--ks "$KS" --ks-pass pass:apklayer)
  if [ -n "${PAST:-}" ]; then  # "<key> <past key>": a v3 key rotation from the past key to the key (the signing lineage)
    OLD=(--ks "$(key "$PAST")" --ks-pass pass:apklayer)
    "$BT"/apksigner rotate --out "$W/lineage" --old-signer "${OLD[@]}" --new-signer "${SIGNERS[@]}"
    SIGNERS=("${OLD[@]}" --next-signer "${SIGNERS[@]}" --lineage "$W/lineage")
  fi
  sign() { "$BT"/zipalign -f 4 "$1" "$1.aligned" && "$BT"/apksigner sign --v4-signing-enabled false "${SIGNERS[@]}" --out "$2" "$1.aligned" 2>/dev/null; }
fi
sign "$W/base.apk" "$OUT"
for f in "$W"/split_*.apk; do [ ! -f "$f" ] || sign "$f" "${OUT%.apk}-$(basename "$f")"; done
echo "$OUT"
