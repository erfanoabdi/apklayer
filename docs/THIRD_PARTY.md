# Third-party code and artifacts

## In this tree

| Path | Origin | License |
|---|---|---|
| `runtime/dex2jvm/` (`com.macapk.dex`) | Mac.apk (kksimp), DEX→JVM converter, copied verbatim | AGPL-3.0 |
| `tests/uibench/uibench.apk` | AOSP UiBench test app | Apache-2.0 |
| `archive/**` | AOSP (Apache-2.0) and Mac.apk (AGPL-3.0) code from earlier milestones; not built | as marked in each origin |

apklayer itself is AGPL-3.0-or-later (PLAN D5), compatible with all of the above.

## Consumed at runtime (not vendored; pinned in deps/deps.lock, cached in ~/.cache/apklayer/deps)

| Artifact | Origin | License |
|---|---|---|
| `android-all-instrumented-16-robolectric-13921718-i7.jar` (SDK 36) | Robolectric build of unmodified AOSP | Apache-2.0 |
| Robolectric 4.17 (`robolectric`, `shadows-framework`, `sandbox`, `resources`, `nativeruntime`, `simulator`, …) | robolectric.org | MIT |
| `nativeruntime-dist-compat-1.0.19` (HWUI/Skia/SQLite natives) | Robolectric prebuilt from AOSP sources | Apache-2.0 (AOSP) |
| `android-stub.jar` (SDK platform android-36.1 `android.jar`) | Android SDK platform | Apache-2.0 |
| JDK 21 linux-x86 (`~/jdk21-x86`) | LOCAL copy of los24 `prebuilts/jdk/jdk21/linux-x86` | GPLv2 + Classpath Exception |
