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
- **AOSP builds (updated 2026-10-26, supersedes the 2026-10-25 rules)**: the user built LineageOS 24 in
  OrbStack for apklayer: tree `~/los24`, product `breakfast gsi_arm64 userdebug`, its `out/` (115 GB,
  `out/target/product/generic_arm64`) is ours to use. "you can also build libs and targets if you need".
  Build only what is needed (log each build in docs/AOSP-BUILDS.md first), on the OrbStack VM,
  `cd ~/los24 && source build/envsetup.sh && breakfast gsi_arm64 userdebug && m <target>`.
  **Never wipe/clean `~/los24/out`** (user: "it's gonna take very long time"): no rm -rf out, m clean, installclean, other products.
  Earlier (now void): separate OUT_DIR and "never touch los24's out/".
- The AOSP tree is a **read-only reference**: read source to copy/understand, and use its
  prebuilts (e.g. JDK) locally; don't compile it.
- **Write as little code as possible; copy from AOSP/Robolectric as much as possible.**
- **Robolectric method** (2026-10-25): unmodified `android-all` + shadows. No hand-patched
  framework copies ("manually patched files … won't be good to replicate by the android
  version updates").
- **Prefer local artifacts over downloads** (2026-10-25: "stop downloads try to use local stuff").
- Preferred stack: C++, Java, Rust; Meson for apklayer's own native code.
- Report actively, don't pause; commit at every step.

## Current state (2026-10-26)
- P1 (CLI, install cache, shell protocol) and P2 (desktop window/input: decorations, keyboard/D-pad,
  wheel, resize, HiDPI plumbing, lifecycle, clipboard, orientation, IME, touch) are done; details and
  the open caveats (2x output, real IME not exercised) are in docs/PLAN.md. P3 corpus: 7/17 T1 pass.
  P4 native libs: apps' `.so` load through bionic_translation (NativeLoader + JNI helper); NDK shims
  in `bridge/ndk` (log, android, EGL over bundled Mesa llvmpipe); SurfaceView surfaces via
  `HostSurfaces` (native GL frames are composited into the window); sample `tests/apps/hello-gl2`.
  Java GLSurfaceView does not render (EGL10 classes are stubs in the sandbox; see PLAN P4.3).
- Session rules are in `CLAUDE.md` (no sleeps, 3-second rule, reuse local sources first).
- Commands: `bin/apklayer install|run|list|uninstall`; apps in ~/.local/share/apklayer/apps/<pkg>
  (data/ persists via links). On the VM: `scripts/vm-run.sh [app.apk]`; headless on the Mac:
  `scripts/check.sh`; tests: `tests/p2-host.sh`, `tests/vm/e2e.sh`, `tests/vm/native.sh [apk]`,
  `scripts/corpus.sh`. Test APKs: the user's `~/TestApps`. Never `pkill -f` a pattern that appears in your
  own command line (it kills itself).
- Architecture: docs/ARCHITECTURE.md. **Plan of record: docs/PLAN.md.**

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
- dex2jvm output needs `@DoNotInstrument` (runtime/host/java/.../DenyInstrument): its `<init>` lowering
  collides with Robolectric's constructor renaming.
- **SDK 36 (Android 16) is live** (P1.4): android-all-instrumented 16-robolectric-13921718-i7.
  Needs Robolectric's `--add-opens` flags (`runtime/host/jvm.args`, passed as `@argfile`).
  The `Build` outer stub is no longer needed (archived). `android-stub.jar` IS still needed
  (outer cp needs android.app.Application etc.) = SDK `platforms/android-36.1/android.jar`
  (the Mac's android-36 platform is a partial install).
- Deps: `deps/deps.lock` (sha256) + `scripts/fetch-deps.sh` (local-first) → `~/.cache/apklayer/deps`
  on the Mac (source of truth); vm-run.sh rsyncs it to the VM's same path. `~/rob` on the VM is
  legacy (8.1 era) and can be deleted.
- Robolectric 4.17 knows SDKs up to 37 (17-robolectric-15733970); `org.robolectric:simulator:4.17`
  is published on Maven (in the lock).
- Robolectric source has an experimental **Simulator** module (SandboxBuilder, AppLoader,
  FrameListener, Mouse/KeyboardHandler). **Now the host** (P1.5): `apklayer.host.Main` uses the
  published simulator-4.17.jar (SandboxBuilder, FixedConfiguration, ScreenUpdateHandler); no JUnit.
  Gotchas: `RoboMonitoringInstrumentation.startActivitySyncInternal` NPEs in windowFocusChanged
  (view root null), so use `Robolectric.buildActivity(cls, intent).setup()` like the simulator.
  `LazyApplication` is in `org.robolectric.annotation.experimental` in 4.17. Robolectric's
  window-list listener (forces a capture when windows change) only exists for SDK > 36, so the
  host invalidates/toggles visibility to get fresh frames. takeScreenshot ignores FLAG_DIM_BEHIND
  (no dim behind dialogs yet; P2).
- AOSP HWUI host variant (what Robolectric's prebuilt is) compiles only `platform/host` stubs;
  the GPU pipelines (SkiaOpenGL/SkiaVulkan, EglManager, VulkanManager) are device-only in
  frameworks/base/libs/hwui/Android.bp. GPU HWUI and native arm64 both need our own Meson
  build of AOSP native libs (PLAN P7): one Soong x86_64 host build in OrbStack gives the
  compdb, then Meson rebuilds for x86_64 + arm64.
- User (2026-10-25): native linux-arm64 WILL be supported, but later. Rosetta/FEX are not a
  product path, only the dev-VM stopgap until PLAN P7.
- P1.3 build: top-level Meson (`meson setup b && ninja -C b`) on Linux; jars are custom_targets via
  scripts/javac-jar.sh (Meson's jar() compiles per file and can't resolve dex2jvm's flat
  `com.macapk.dex/` dir). The Mac has no meson/ninja (not installed: avoid downloads), so check.sh
  uses javac directly. Exclude macOS `._*` AppleDouble files (COPYFILE_DISABLE=1 for tar).
- P1.6 shell: `apklayer-shell` daemon (bridge/shell/, see docs/ARCHITECTURE.md) on `$XDG_RUNTIME_DIR/apklayer/shell.sock`;
  vm-run (re)starts it detached, log /tmp/apklayer-shell.log. App side = pure Java ShellClient.
  Rosetta flake: Robolectric TempDirectory cleanup at JVM exit sometimes SIGSEGVs in HotSpot
  (LinkResolver); harmless, gone with native arm64 (P7). The VM is ~2x slower than the Mac, so
  scripted snapshots there need ~1.5 s more after a launch.
- `pkill -f <pattern>` over ssh can kill the ssh shell itself; use `pkill -x` or a `[x]yz` pattern.

## Reference projects on this machine
- `/Users/erfanabdi/GitHub/Mac.apk`: clone of kksimp/Mac.apk v1.0.2303 (AGPL-3.0). HotSpot +
  javac-compiled patched AOSP framework + dex2jvm + ObjC host bridge. Source of our dex2jvm.
- `/Users/erfanabdi/GitHub/robolectric`: Robolectric source (MIT): sandbox, shadows,
  nativeruntime, simulator.
- `/Users/erfanabdi/GitHub/android_translation_layer`: ATL (re-implements framework APIs).
  Not our path; useful for its bionic-on-glibc native-lib loading (roadmap Phase 3).

## AOSP source and build tree
- LineageOS 24 tree (Android 16-era), **built by the user for apklayer**: `~/los24` in the OrbStack VM, i.e.
  `/Users/erfanabdi/OrbStack/ubuntu22-x86/home/erfanabdi/los24` on the Mac (211 GB excl. 115 GB `out/`). Mounted from the OrbStack `ubuntu22-x86` VM; listings over the mount
  are slow, so prefer `orb -m ubuntu22-x86 bash -c '...'`.
- OrbStack VM (x86_64, 17 cores, 98 GB RAM, 2.2 TB free): where AOSP builds run (see the rule above;
  `out/` already holds the device build, the x86_64 *host* variants of modules such as `libandroid_runtime` are not built yet). Soong facts: `libandroid_runtime` is host_supported (the
  layoutlib/Robolectric native runtime). Soong cross-hosts are only linux_bionic arm64 and musl
  arm/arm64, with **no glibc arm64 host target**, hence PLAN P7's compdb → Meson step.

## Testing Linux VM (Parallels on this Mac)
- `ssh parallels@10.211.55.4`, password `1234` (sshpass on the Mac; sudo = same password).
- Ubuntu 24.04, kernel 6.8, **aarch64**, 8 CPUs, ~8 GB RAM, GNOME Wayland (`wayland-0`,
  XDG_RUNTIME_DIR=/run/user/1000). OpenJDK 21 arm64 (`-XX:UseSVE=0`), meson/ninja/gcc,
  libwayland-dev, wayland-protocols. No binder/ashmem in the kernel.
- Layout: `~/.cache/apklayer/deps` (mirror of the Mac cache), `~/jdk21-x86`, `~/apklayer-m1/`
  (synced sources; shell build in `bridge/shell/b`), `~/apklayer-build/` (compiled host/d2j/cfg),
  `~/apklayer-apps/<name>.apk`.
  Old dirs `~/apklayer-mac`, `~/apklayer-m1/runtime/java*` belong to the archived java_mac era.

## Environment notes
- Host: macOS (Apple Silicon), zsh (avoid `echo ===`, because zsh expands `=`).
- Mac has JDK 21 at `/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home`
  and the mac-arm64 Robolectric native runtime works there, so fast headless iteration is
  possible on the Mac using `~/.cache/apklayer/deps` (same jars as the VM).

## VM testing toolbox (2026-10-26)
- Screenshots of the VM desktop: `prlctl capture "Ubuntu 24.04 (with Rosetta)" --file x.png` on the Mac
  (the guest's own screenshot D-Bus API is denied; the capture does not include the cursor).
- Real input: `tests/vm/uinput-keys.c` (compile with gcc on the VM, run with sudo): keyboard device +
  an absolute-pointer device (permille coordinates). VM screen is 1024x768. Wheel events from this
  device only sometimes reach the compositor, so the VM wheel check may SKIP.
- GNOME on the VM (mutter 46) has no cursor-shape-v1 and no server-side decorations; the shell draws
  its own title bar (decor.c) and sets resize cursors through the wl_cursor theme.
- `tests/p2-host.sh` / `tests/vm/e2e.sh` accept group names; run only what the change touched.
- Gotchas learned: Robolectric's `UiAutomation` key injection skips `ViewRootImpl` (no focus
  navigation) and offers wheel events to the first touch-modal root; dialogs are separate windows
  and must be ordered by window type; the window root has no input focus unless the host sets it.
- Frame size drives the shell buffers (`ShellClient.present(px, w, h)`); a shm file is never
  truncated/reused across resizes (the compositor may still read it).

## P2 done (2026-10-26) — what to remember
- Shell protocol additions (all documented in bridge/shell/shell.h): KEY 105, SCROLL 106, CONFIGURE 107,
  STATE 108 (active/minimized), CLIPBOARD 109 / CLIPBOARD_SET 5, IME 110 / TEXT_INPUT 6, TOUCH 111;
  INPUT types 3 = hover, 4 = right click. Host event tags (HostInput): 12 key, 13 wheel, 14 configure,
  15 state, 16 touch (0..4 are the old pointer types: tags must never collide).
- Orientation: the host reshapes the window from `getRequestedOrientation()` (followOrientation).
- Verification: `tests/p2-host.sh [group]`, `tests/vm/e2e.sh [group]`; script steps in ScriptRunner
  (tap, key, keydown/keyup, wheel, configure, state, touch, hover, rclick, ime, clipin, clipcopy, orient).
- P3.1: `scripts/fetch-corpus.sh [tier]` pins F-Droid APKs in tests/corpus/corpus.lock; cache in
  ~/.cache/apklayer/corpus. The user said: do not chase individual failing corpus apps; fix only shared,
  framework-level causes.

## P4 notes (2026-10-26)
- Native build: `scripts/build-native.sh` (OrbStack x86_64 VM `ubuntu22-x86`; needs `~/GitHub/bionic_translation`). Output
  `build/native/x86_64/` (bionic libs, libapklayer_native.so, bundled libEGL/libunwind/... because the Rosetta userspace is minimal,
  and `share/bionic_translation/cfg.d` mapping `libc.so -> libc_bio.so.0` etc.). bin/apklayer sets `LD_LIBRARY_PATH` and `XDG_DATA_DIRS`
  to that dir; `-Dapklayer.native` / `-Dapklayer.libdir` point the host at it.
- The bionic linker needs `_r_debug_ptr` set by the host (done in the JNI helper's constructor), or it crashes in
  `apkenv_notify_gdb_of_load`. gdb cannot ptrace under Rosetta: debug with a SIGSEGV handler + `addr2line` on a debug build.
- Test: `tests/vm/native.sh` (CPU-Z). Corpus apps with native libs mostly also need NDK shims (liblog etc.) next.

## Running the GPU route (device-library flavor), 2026-10-27
On the arm64 VM: `XDG_RUNTIME_DIR=/run/user/1000 APKLAYER_NATIVE=~/arm64-native ~/apklayer-m1/bin/apklayer run <installed package>`; `bin/apklayer stop` ends it.
That alone starts the services (`scripts/arm64-stack.sh none`, ready marker `/tmp/apklayer-stack/ready`), uses the arm64 JDK, and sets the environment from `scripts/native-env.sh`.
Install apps first with the default flavor (the tree flavor's describe step is not wired). Logs: `/tmp/apklayer-stack/*.log` (services), the app's stdout. `servicetool list|check|dump NAME` (btload --main) inspects services (SurfaceFlinger dump shows layers).
Capture caveat: `prlctl capture` is reliable for the GPU window now (it showed the UiBench list); Robolectric patches live in `runtime/patches/` (compiled into native-runtime.jar by `scripts/build-native-arm64.sh`).

## Open problems on the GPU route (2026-10-27): see docs/PLAN.md "P7.1 GPU route: next steps" (crash on resize with a dialog open, layer not resized, dialog margin, mapping/focus, headless primary, multi-app, text input/clipboard, cleanup).
