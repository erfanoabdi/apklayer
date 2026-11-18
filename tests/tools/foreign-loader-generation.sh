#!/usr/bin/env bash
# Usage: bash tests/tools/foreign-loader-generation.sh BUILD_DIR ANDROID_FRAMEWORK_JAR
# Use a built Meson tree and a raw (not preinstrumented) Android framework jar.
set -euo pipefail

build=$1
framework=$2
repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT

classpath="$build/runtime/overlay.jar:$build/runtime/host-classes.jar:$framework:$build/deps/libs/*"
javac -cp "$classpath" -d "$scratch/test" \
    "$repo/runtime/host/java/apklayer/host/ShadowHostLoadedApk.java" \
    "$repo/tests/tools/foreign-loader-generation/ForeignLoaderGeneration.java"

for generation in 1 2; do
    output="$scratch/generation-$generation"
    mkdir -p "$output"
    cat > "$scratch/Target.java" <<JAVA
package localfixture;
public class Target {
    public static int generation() { return $generation; }
}
JAVA
    javac -d "$output" "$scratch/Target.java"
    printf '%s' "$generation" > "$output/target-resource.txt"
    jar --create --file "$scratch/$generation.jar" -C "$output" .
done

java --add-opens=java.base/java.lang=ALL-UNNAMED -cp "$scratch/test:$classpath" ForeignLoaderGeneration "$scratch"
