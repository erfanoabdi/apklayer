# M2b recon — what it takes to run a real APK (from Mac.apk sources)

## Target APK: tests/uibench.apk (copied from /Users/erfanabdi/TestApps)
- 2.4 MB, single `classes.dex`, **zero .so files** (pure Java, appcompat-based) →
  no ABI translation needed; ideal first GUI target.

## How Mac.apk launches an app (com.macapk.loader.Boot, 4,483 lines)
- Native main() boots HotSpot with classpath `android-framework.jar + macapk-loader.jar +
  <app jar>` and calls `com.macapk.loader.Boot.main(apkPath)`.
- Boot re-implements the AOSP startup chain **as a driver, not a fork**: it replays
  `ActivityThread.handleBindApplication` → `LoadedApk.makeApplicationInner` →
  `Instrumentation.newApplication` → `Application.attachBaseContext` → Activity instantiation,
  with per-step feature gates ("pipeline ON/OFF") and line-referenced comments back to AOSP.
- No system_server: ActivityThread is instantiated in-process; `Looper.macapkIsActivityThread`
  thread-identity checks replace binder lookups.
- Loader also carries: NativeLoaderShim (dlopen of app .so via the native bridge),
  TouchDispatcher (input → ViewRootImpl), FwGapNewClasses (framework gap-fill), monet, etc.
- App DEX is converted by **com.macapk.dex (dex2jvm, 36 pure-Java files)** to a JVM jar at
  install time, cached by FRAMEWORK_ID digest.

## apklayer plan (decided, AGPL-compatible — matches Mac.apk license)
1. **Port `com.macapk.dex`** from Mac.apk into runtime/java (copy, not rewrite) — the dex→class
   converter. Verify it compiles standalone against javac 21.
2. Build a **runner jar** (`com.apklayer.loader.Boot`): start from Boot.java but Linux-first —
   skip Mac-only gates (pairip, Metal, fmod) and drive the same AOSP chain.
3. Grow the framework seed toward `android.app.*` (ActivityThread, LoadedApk, ContextImpl,
   ViewRootImpl…) + `android.content.res` (XmlBlock/AssetManager) — same copy+thin-patch loop;
   resource/native layers (hwui/graphics) are the long pole and will ride on Skiko + our
   Wayland bridge (M2a).
