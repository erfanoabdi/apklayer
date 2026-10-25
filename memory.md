# apklayer — Project Memory

Living document of facts and decisions told by the user. Keep this up to date.
The full chronological log up to the first Wayland frames is in `archive/HISTORY.md`.

## What apklayer is
- Project **apklayer** (renamed 2026-10-25 from "apkly"; never use the old name), workspace
  `/Users/erfanabdi/GitHub/apklayer`.
- Same idea as **Mac.apk**, but for **Linux**: run Android APKs **natively on Linux**.
  No emulator, no VM, no "Linux-with-Linux-inside" container approach.
- Target host today: the user's Parallels Ubuntu VM (ARM64). End goal is real Linux desktops
  (Wayland/X11, PipeWire, Mesa).

## Hard rules from the user
- **Do NOT build AOSP** (no Soong builds of los24 in the OrbStack VM or anywhere else).
  "I don't want you to waste my resource and time on it, do not waste time on AOSP."
- **Do NOT use or inspect los24's `out/`**: "it's not related to your work."
- The AOSP tree is a **read-only reference**: read source to copy/understand, and use its
  prebuilts (e.g. JDK) locally; don't compile it.
- **Write as little code as possible; copy from AOSP/Robolectric as much as possible.**
- **Robolectric method** (2026-10-25): unmodified `android-all` + shadows. No hand-patched
  framework copies ("manually patched files … won't be good to replicate by the android
  version updates").
- **Prefer local artifacts over downloads** (2026-10-25: "stop downloads try to use local stuff").
- Preferred stack: C++, Java, Rust; Meson for apklayer's own native code.
- Report actively, don't pause; commit at every step.

## Current state (2026-10-25, M5 done)
- uibench runs with full UI + mouse/keyboard on the VM's Wayland desktop (user tested it).
- One command: `APKLAYER_GFX=NATIVE ./scripts/apklayer-rob.sh [app.apk]` (default uibench).
  It syncs tests/rob, tests/stubs, runtime/dex2jvm and bridge to the VM, rebuilds, converts
  the DEX, writes test_config.properties and runs. Close the window to exit.
  Verification flags: `APKLAYER_JOPTS='-Dapklayer.frames=150 -Dapklayer.snapframes=140
  -Dapklayer.script=40:tap:100,80+80:tap:100,129'` (snapshots → /tmp/apklayer-frame-N.png on VM).
- Architecture: docs/ARCHITECTURE.md. **Plan of record: docs/PLAN.md** (all decisions made;
  follow its phase order and "Immediate next tasks").

## Key technical facts learned (don't re-discover)
- Robolectric reads `android_resource_apk` ONLY from classpath
  `/com/android/tools/test_config.properties` (AGP contract), not from -D props. With it,
  AOSP's PackageParser reads manifest + resources from the APK; no text manifest needed.
- LEGACY graphics is a recording fake (no text). NATIVE = real HWUI/Skia.
- Robolectric's native runtime has NO linux-arm64 build (checked SDK 27, 36, 37 and
  dist-compat). In AOSP it's a prebuilt from a custom Soong host branch, so building it =
  building AOSP (needs user approval).
- VM has Rosetta for Linux (binfmt RosettaLinux, amd64 foreign arch, libc6:amd64). x86_64 JDK =
  local los24 `prebuilts/jdk/jdk21/linux-x86` → VM `~/jdk21-x86`. Under Rosetta, HotSpot C2
  bus-errors and G1 crashes: use `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC`.
- dex2jvm reads the framework class hierarchy from ITS OWN classpath: run it with
  `android-all` on the cp, or the output fails verification (e.g. AbsActionBarView.access$101).
  Don't let it see the Mac's Android SDK either.
- dex2jvm output needs `@DoNotInstrument` (tests/rob/.../DenyInstrument): its `<init>` lowering
  collides with Robolectric's constructor renaming.
- Only `tests/stubs/android/os/Build.java` is needed on the outer classpath (conscrypt reads
  SDK_INT). android-stub.jar (API 35) covers shadow signatures newer than 27.
- Robolectric source has an experimental **Simulator** module (SandboxBuilder, AppLoader,
  FrameListener, Mouse/KeyboardHandler): the planned replacement for the JUnit driver.
- AOSP HWUI host variant (what Robolectric's prebuilt is) compiles only `platform/host` stubs;
  the GPU pipelines (SkiaOpenGL/SkiaVulkan, EglManager, VulkanManager) are device-only in
  frameworks/base/libs/hwui/Android.bp. GPU HWUI and native arm64 both need our own Meson
  build of AOSP native libs (PLAN P7). That compiles AOSP C++ but isn't a Soong build, so it
  needs the user's OK first.
- User (2026-10-25): native linux-arm64 WILL be supported, but later. Rosetta/FEX are not a
  product path, only the dev-VM stopgap until PLAN P7.
- `pkill -f <pattern>` over ssh can kill the ssh shell itself; use `pkill -x` or a `[x]yz` pattern.

## Reference projects on this machine
- `/Users/erfanabdi/GitHub/Mac.apk`: clone of kksimp/Mac.apk v1.0.2303 (AGPL-3.0). HotSpot +
  javac-compiled patched AOSP framework + dex2jvm + ObjC host bridge. Source of our dex2jvm.
- `/Users/erfanabdi/GitHub/robolectric`: Robolectric source (MIT): sandbox, shadows,
  nativeruntime, simulator.
- `/Users/erfanabdi/GitHub/android_translation_layer`: ATL (re-implements framework APIs).
  Not our path; useful for its bionic-on-glibc native-lib loading (roadmap Phase 3).

## AOSP source (read-only reference)
- LineageOS 24 tree (Android 16-era): `/Users/erfanabdi/OrbStack/ubuntu22-x86/home/erfanabdi/los24`
  (211 GB excl. out). Mounted from the OrbStack `ubuntu22-x86` VM; listings over the mount
  are slow, so prefer `orb -m ubuntu22-x86 bash -c '...'`.
- OrbStack VM (x86_64, 17 cores, 98 GB RAM) is reserved for the user: **no AOSP builds there**.

## Testing Linux VM (Parallels on this Mac)
- `ssh parallels@10.211.55.4`, password `1234` (sshpass on the Mac; sudo = same password).
- Ubuntu 24.04, kernel 6.8, **aarch64**, 8 CPUs, ~8 GB RAM, GNOME Wayland (`wayland-0`,
  XDG_RUNTIME_DIR=/run/user/1000). OpenJDK 21 arm64 (`-XX:UseSVE=0`), meson/ninja/gcc,
  libwayland-dev, wayland-protocols. No binder/ashmem in the kernel.
- Layout: `~/rob/{libs,android-all,android-stub.jar}` (Robolectric deps), `~/jdk21-x86`,
  `~/apklayer-m1/` (synced sources; bridge build in `bridge/b`), `~/apklayer-apps/<name>.apk`.
  Old dirs `~/apklayer-mac`, `~/apklayer-m1/runtime/java*` belong to the archived java_mac era.

## Environment notes
- Host: macOS (Apple Silicon), zsh (avoid `echo ===`, because zsh expands `=`).
- Mac has JDK 21 at `/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home`
  and the mac-arm64 Robolectric native runtime works there, so fast headless iteration is
  possible on the Mac (copy `~/rob` from the VM to a scratch dir).
