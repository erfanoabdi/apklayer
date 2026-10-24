# apklayer — Project Memory

Living document of facts and decisions told by the user. Keep this up to date.

## What apklayer is
- Project **apklayer** (renamed 2026-10-25 from "apkly" — never use the old name), workspace
  `/Users/erfanabdi/GitHub/apklayer`.
- Same idea as **Mac.apk**, but for **Linux**: run Android APKs **natively on Linux**.
  No emulator, no VM, no "Linux-with-Linux-inside" container approach.
- Target host today: the user's Parallels Ubuntu VM (ARM64); end goal is real Linux desktops
  (Wayland/X11, PipeWire, Mesa).

## Hard rules from the user
- **Do NOT build AOSP** (no Soong builds of los24 in the OrbStack VM or anywhere else).
  "I don't want you to waste my resource and time on it, do not waste time on AOSP."
- **Do NOT use or inspect los24's `out/`** — "it's not related to your work."
- The AOSP tree is a **read-only reference**: read source to copy/understand, don't compile it.
- **Write as little code as possible; copy from AOSP as much as possible.**
- Preferred stack: **C++, Java, Rust**, **Meson** as build system (for apklayer's own code).
- Keep everything as close to AOSP as possible: real `framework.jar` on real ART;
  host-specific work only in a thin native bridge (Mac.apk style), never re-implementations.

## Reference projects on this machine
- `/Users/erfanabdi/GitHub/Mac.apk` — clone of [kksimp/Mac.apk](https://github.com/kksimp/Mac.apk),
  v1.0.2303, AGPL-3.0 (commercial license available). The model to follow.
  - `runtime/` = the C++/ObjC bridge: `src/*.mm` (windowing, audio, input), ANGLE for GL,
    `translator/` (ABI translation for x86 Android libs on ARM Macs), `apk_unpacker`,
    `app_process_group.cpp` (66 lines — the whole per-app process trick), `main.cpp` boots
    HotSpot with classpath = framework jar + loader jar + APK jar, then calls
    `com.macapk.loader.Boot.main(apkPath)`.
  - `runtime/java/` (~3,058 .java) = AOSP framework Java kept intact + androidx.window,
    thin overlays only: `java_base_patch/`, `jdk_unsupported_patch/`.
  - `launcher/` = Swift/SwiftUI app; `tests/test-app` = Kotlin + NDK C (CI release gate).
- `/Users/erfanabdi/GitHub/android_translation_layer` — clone of
  [gitlab.com/android_translation_layer](https://gitlab.com/android_translation_layer) (ATL, fork
  of ChromeOS ARC++/berberis, C++). **User's critique:** ATL re-implements framework APIs
  (`src/api-impl`, `libandroid`, `gstub`) instead of keeping AOSP Java — why it lags behind.
  Not our path. Useful anyway: their **published release prebuilts** and their
  minimal-runtime approach to ART boot without system_server.

## AOSP source (read-only reference)
- Full **LineageOS 24** tree (Android 16-era): `/Users/erfanabdi/OrbStack/ubuntu22-x86/home/erfanabdi/los24`
  (211 GB excl. out) — `art/`, `bionic/`, `libcore/`, `frameworks/base/` complete.
- Mounted from the **OrbStack `ubuntu22-x86` VM**; listings over the mount are slow —
  prefer `orb -m ubuntu22-x86 bash -lc '...'` for targeted reads.
- OrbStack VM is powerful (17 cores, 98 GB RAM, 2.3 TB free) but reserved for the user —
  **no AOSP builds there**.

## Testing Linux VM (Parallels on this Mac)
- `ssh parallels@10.211.55.4` — password `1234` (sshpass installed on the Mac; sudo = same pw).
- Ubuntu 24.04 LTS (Noble), kernel 6.8.0-40-generic, **aarch64** (matches Android arm64-v8a),
  8 CPUs, ~8 GB RAM, 62 GB disk (~49 GB free). "This is going to be your linux to do the testings."
- No binder/ashmem in its kernel — fine: no SurfaceFlinger/binder needed for the window path
  (bridge HWUI directly, like Mac.apk does).

## Environment notes
- Host: macOS (Apple Silicon), zsh (avoid `echo ===` — zsh `=` expansion).
- File tools are confined to the workspace; use shell for paths outside it.
- Open question (asked user, 2026-10-25): where runtime artifacts (ART, boot jars,
  framework.jar, libhwui…) come from if we never build AOSP — ATL release prebuilts vs.
  other sources. Do not resolve this by building AOSP.

## Decisions log
- 2026-10-25: **Approved by user**: compiling the framework jar from los24 sources with
  javac is acceptable (it is not an AOSP/Soong build). Adopt Mac.apk's prebuilt strategy:
  JVM = prebuilt OpenJDK, Skia = prebuilt (Skiko ships linux-arm64), GPU = native Mesa
  (no ANGLE/MoltenVK needed on Linux), vendored small prebuilts where Mac.apk does
  (BouncyCastle etc.).
- 2026-10-25: Use git; commit at every step of work.
- 2026-10-25 (M0 recon): Mac.apk runs **HotSpot OpenJDK 21**, not ART. Framework = synced AOSP
  java compiled via javac --release 21 -> android-framework.jar (java_base_patch via
  --patch-module). Apps: pure-Java **dex2jvm** (runtime/java/com/macapk/dex, 36 files, AGPL)
  converts classes.dex -> JVM jar at install time, cached by FRAMEWORK_ID. Details:
  docs/M0-FINDINGS.md. Open decision for user: port com.macapk.dex (AGPL) vs alternative
  dex->class converter.
- 2026-10-25: Parallels VM prepared for M1: `openjdk-21-jdk-headless` (21.0.12 arm64) installed
  via apt (needed `apt-get update` first). HotSpot prints a harmless SVE warning on this VM —
  pass `-XX:UseSVE=0`. Mesa EGL/GL libs already present.
- 2026-10-25 (M1 smoke PASSED): genuine AOSP classes from los24 (android.util.Base64 unmodified;
  Log/SystemProperties patched thin; plus annotation/internal deps) compile with plain `javac
  --release 21` and run on stock OpenJDK 21 on the bare Ubuntu ARM64 VM:
  `apklayer M1 smoke: base64=aGVsbG8gYXBrbGF5ZXI= roundtrip_ok=true jvm=21.0.12.1`.
  Seed lives in runtime/java (24 files), smoke in tests/smoke. Thin patches marked
  "apklayer patch" inline. Notable stubs so far: minimal android/os/Build.java, minimal
  com.android.internal.util.Preconditions, DeadObjectException (upstream is aidl-generated),
  Log.java edits (no RuntimeInit/FastPrintWriter/LineBreakBufferedWriter), SystemProperties
  Binder no-ops. Compile/run on VM: javac -nowarn -d out $(find java tests/smoke -name "*.java");
  java -XX:UseSVE=0 -cp out apklayer.smoke.Smoke  (sources synced to ~/apklayer-m1 on VM).
