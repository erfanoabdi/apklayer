# M0 findings — how Mac.apk actually works (read-only recon)

## The big one: no ART — it's HotSpot
`runtime/src/main.cpp` links `libjvm` and boots **OpenJDK HotSpot (21 default, 25 experimental,
`MACAPK_JDK` switch)**. Classpath = `android-framework.jar` + `macapk-loader.jar` + per-app
converted jar; entry point `com.macapk.loader.Boot.main(apkPath)`.

## Framework = AOSP Java compiled with javac
- `runtime/java/` (~3,058 .java) is a **synced copy** of AOSP framework sources (android.*,
  androidx.window, xmlpull, fmod bindings…) kept in-tree, plus patches.
- `runtime/scripts/build.sh`: `javac --release 21 @sources` → everything except `com/macapk/*`
  is packed into `android-framework.jar` (+ bundled fonts). `java_base_patch/` compiles
  separately with `--patch-module java.base/...`; `jdk_unsupported_patch/` fills JDK gaps
  (e.g. `sun/misc/Unsafe.java`).
- A **FRAMEWORK_ID content digest** folds JDK version + framework jar digest into app caches:
  switching JDK or framework re-converts every app once.

## App code: dex2jvm (pure Java, 36 files)
`runtime/java/com/macapk/dex/` ("dex2jvm", marker file `dex2jvm-commit`) converts **DEX →
JVM .class jars at install time**; also backs dynamic loading entry points
(`dalvik/system/MacApkDexConverter.java` serves InMemoryDexClassLoader, packers, dynamic
feature modules). This is Mac.apk's crown jewel — AGPL-3.0.

## Native libs
`runtime/translator/` (own C++ ABI translator, for x86 Android .so on ARM Macs) +
`apk_native_exec.cpp` (dlopen bridge). On an ARM64 Linux host, arm64-v8a .so load natively —
we likely need none of this initially.

## Prebuilts (all arm64 macOS binaries in-tree)
ANGLE `libEGL/libGLESv2.dylib`, MoltenVK+libvulkan, Skiko `libskiko-macos-arm64.dylib`
(Skia/Metal, never rebuilt), `bcprov-jdk18on-1.79.jar`, fishhook/stb_vorbis (vendored C).

## apklayer mapping
| Mac.apk            | apklayer (Linux)                                   |
|--------------------|----------------------------------------------------|
| brew openjdk@21    | apt `openjdk-21-jdk-headless` (21.0.4 arm64 in Ubuntu 24.04) |
| ANGLE+MoltenVK     | native Mesa EGL/GLES (+Vulkan) — no translation    |
| Skiko macos dylib  | Skiko **linux-arm64** artifact (same 0.150.2 pin)  |
| synced runtime/java| javac over **los24** `frameworks/base/core/java` + libcore (user-approved) |
| dex2jvm (AGPL)     | port `com.macapk.dex` (license: match AGPL) vs dex2jar — user decision |
| Swift launcher     | C++/Rust CLI + bridge (Meson)                      |
