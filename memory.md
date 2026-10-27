# apklayer — Project Memory

> Current authorization (2026-10-27): the user asked the assistant to build anything needed in OrbStack `~/los24` autonomously while they sleep. This supersedes older instructions below to ask them to build. Only incremental builds of `gsi_arm64 userdebug`; never clean or wipe `out/`.

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
Tree-flavor install now converts against its own framework and renders metadata/icons using its arm64 runtime. Logs: `/tmp/apklayer-stack/*.log` (services), the app's stdout. `servicetool list|check|dump NAME` (btload --main) inspects services (SurfaceFlinger dump shows layers).
Capture caveat: `prlctl capture` is reliable for the GPU window now (it showed the UiBench list); Robolectric patches live in `runtime/patches/` (compiled into native-runtime.jar by `scripts/build-native-arm64.sh`).

## Open problems on the GPU route (2026-10-27): see docs/PLAN.md "P7.1 GPU route: next steps" (crash on resize with a dialog open, dialog margin, mapping/focus, headless primary, multi-app, text input/clipboard, cleanup).

## GPU follow-up (2026-10-27)
- User authorized autonomous incremental los24 builds this session, superseding the older ask-to-build rule. Never clean out/.
- Found stale /tmp/apklayer-stack/ready race: ensure_stack must clear it before starting a replacement.
- RenderEngine waits five seconds for service.sf.cache_dir_available without Android init. arm64-stack now creates real cache files under android-root/data/misc/surfaceflinger and announces readiness. Files must exist for the current path overlay to redirect them.
- An unmapped dmabuf-only primary cannot deliver Wayland frame callbacks; shell maps an initial blank shm frame (same memfd pattern as decorations), enabling cold-start focus/vsync. Verified cold startup and real menu clicks into Dialog List.
- Full SurfaceFlinger dumps abort at default_pthread_mutex_init in bionic_translation; --list works. Regression uses --list.
- libapklayer_vdisplay rebuilt using its four generated ninja compile/link/strip/copy commands against existing dependencies: normal ninja traversal hit unrelated ABI-dump Duplicate root dir errors. No cleaning.
- Resize regression status: one complete pass (real resize to 615x823, real Back removed the dialog layer, clipboard step and new-size dmabufs verified). Other runs exposed a bionic_translation default_pthread_mutex_init assertion and coordinate/focus sensitivity; A1 is not complete. Temporary diagnostic instrumentation is installed in VM ~/bt-arm/bionic_translation/pthread_wrapper/libpthread.c and rebuilt libpthread_bio.so.0 (also copied into ~/arm64-native); its original source is unchanged on the Mac. Remove the diagnostic or retain a reproducible patch when diagnosis is complete.
- Timed-mutex corruption diagnosed and fixed: missing bionic_pthread_mutex_timedlock let glibc overwrite the translated mutex pointer with lock state 2. The existing patch script now adds the same unwrap as normal locking; both native build paths apply it. VM libpthread rebuilt and installed; static-init/contention/reuse test passes, and full SurfaceFlinger dump now succeeds. Temporary mutex-debug instrumentation removed. A subsequent resize exposed a separate EGL_NOT_INITIALIZED issue: Android libgui SyncFeatures expects reference-counted eglInitialize/eglTerminate; EGL fix and GL smoke test are being built.
- EGL lifetime fix verified: reference-counted initialize/terminate, matching AOSP egl_display_t, prevents libgui SyncFeatures from terminating the host display under RenderEngine. Rebuilt EGL + existing gltest; live-context borrow + 12 frames and 480x320→640x400 resize pass. Full SurfaceFlinger dumps plus UiBench dialog resize to 908x1392, real Back removing its layer and post-resize main-loop/frames pass.
- Covered-primary startup stall fixed by routing pending vsync requests through the active mapped surface, using real Wayland frame callbacks. Native clients without their own toplevel can request timing too. Packaged GPU regression passes with screenshots before/after real resize, Back removes the dialog layer, and dmabufs match the final size. The screenshots still show the dialog shifted right: surfaceInsets were not subtracted from its layer position; A2 next.
- A2 done: BLAST overwrites buffer-layer destination geometry, so WMS placement must use a parent container. WindowSurface.java handles parent position (-surfaceInsets), layer stack/z and removal; frame requests are bounded to the display. Actual screenshots measure 16px/16px margins before/after resize. tests/vm/gpu-check-image.py checks the captures; A3 (remove initial inactive workaround) next.
- A3 done: removed AppHost’s GPU-only initial-inactive exception; the display is mapped before the app’s first frame. Full regression including actual margin checks passes without it. The image checker handles Mutter moving an oversized window’s decoration above the screen by finding the neutral content edges. A4 headless primary next.
- A4 done: composer primary uses headless host timing connection, no WINDOW_CREATE; only virtual-display app windows are visible. Real output mode sets refresh period. Full GPU/no-primary/image checks and smoke tool in its own virtual display pass. Host callback struct ABI unchanged. A5 multiple simultaneous app JVMs next.
- A5 done: concurrent cold launches share a locked service startup (inotify/pidfd readiness), with PID-unique layer stacks and instance/launch-tagged window titles. Sink logs use per-launch names plus the legacy latest-per-package symlink. Fixed stale pointer_decor_win on window destruction: a queued pointer leave used to redraw a destroyed title surface and crash the shared shell. Multi-app regression passes two GPU JVMs, actual pointer focus, Back closing one, survivor maximize/resize and main-loop/frame delivery, one service manager and full cleanup. Full independent phones/binder/data remain deferred P9. A6 proxy text input/clipboard next.

- A6 done: proxy ids now route IME enable/commit; per-window enable follows real Wayland entry. Clipboard I/O is asynchronous, outgoing selections own their text, echo suppression is per JVM. Fixed zero-length ShellClient clipboard sends and Ctrl/Alt/Meta characters in the tree key-map shadow. tests/vm/input.sh passes real typing, Ctrl+V host paste, app copy to wl-paste, actual IBus ni preedit/你好 commit and a 20 KB transfer. Restores the previous input source and closes children. A7 runtime-overlay/startup cleanup next.
- A7 overlay packaging: scripts/build-runtime-overlay.sh compiles all tree overlays with errors propagated, then atomically publishes the jar while preserving its shim. build-native-arm64 uses it. Full compilation on the VM passed; forced compiler failure left the existing jar hash unchanged.

- A7 done: all ro.* write-once semantics restored; real system hyphen data is the shared instance path (collect-android-root includes it). tests/vm/properties.sh checks cross-process writes and independent stores; two-app test passed strict semantics. Tree install/run share native environment/JVM options, conversion uses and hashes the chosen framework, install errors propagate. hello.input tree install passed with a rendered icon. build-tree-host.sh compiles all sources from scratch, adapts ShadowProvider.reset for the older tree and extracts unchanged FrameListener/ScreenUpdateHandler from pinned simulator-4.17 (the older simulator lacks them). Full fresh host+overlay build passed GPU regression and screenshot margins. Trace flags honor false/0; BT packed relocation prints use existing TRACE; build-native-arm64 names actual versioned ninja library targets (symlink targets did not rebuild). Services-only stack no longer starts tail. Clean-machine provisioning is still release work. B media HALs next.
- B media started: native bridge/hals/media decoder API reuses GStreamer with reference-counted raw YUV samples (no RGBA files), bounded input, registry-derived availability and real timestamps/EOS. Builds/tests on VM with newly installed libgstreamer1.0-dev + libgstreamer-plugins-base1.0-dev. codec-test passes real VP8 encode/decode at two sizes, YUV=81/90/240 for red, PTS, empty poll, EOS and retained frame ownership after decoder teardown. Hardware dmabuf output is implemented as sample metadata but unverified here; no app playback claim. Codec2 adapter next; reuse local frameworks/av/media/codec2/components/base SimpleC2Component/Interface and hal/aidl ComponentStore (vendor.cpp's example store is empty, not useful as implementation).
- B Codec2 baseline: aosp/hals/media/codec2 uses real SimpleC2Component/Interface, host VP8 decode and flexible-YUV C2GraphicBlocks. Service --probe config/query and --decode generated IVF work checks pass; tests/vm/codec2.sh verifies three frames, exact ordinals/EOS and gralloc YUV=81/90/240, then closes all services. Upstream C2Buffer mapReadView always chose DMA-heap cast if not ION, rejecting C2AllocatorBlob; scripts/patch-codec2.sh dispatches non-DMA-heap allocations through virtual map. libcodec2_vndk rebuilt and installed. Missing platform variants libcodec2_soft_common, libcodec2_aidl_V2 and libcodec2_hidl_plugin_stub also built/staged. Actual Soong setup needs source envsetup + breakfast gsi_arm64 userdebug (cp2a release). Generated graph; broad dependency build stopped, module-only commands used with exact generated rsp content. Commands in Orb /tmp/apklayer-codec2-commands.sh etc. Foreground stack now propagates tool exit status; native-env allows explicit APKLAYER_HOST_LIBS override for media tests. Service not yet enabled by default: next real AIDL discovery/MediaCodec JNI, invisible/drain cases, video presentation and audio. Existing VM cannot verify hardware decode.
- B remote Codec2 verified: opt-in APKLAYER_CODEC2=1 stages only its real VINTF fragment and starts the AIDL service; default stacks remove that fragment. media.c2.hal.selection=aidl and debug.stagefright.c2-poolmask=786432 select Android’s actual BLOB input / BufferQueue output paths (no fake DMA-heap device). Android Codec2Client now sends compressed BLOB input across binder and returns actual gralloc NV12 output to the client. tests/vm/codec2.sh passes remote discovery/interface, three visible frames plus a stream with one hidden VP8 reference frame, timestamps/frame indices/EOS, pixel values, process exit and cleanup. codec2-run.py owns the stack and uses inotify/selectors with a 3-second idle stall cutoff and total limit.
- Remote client exit initially hung in global std::condition_variable destructor because glibc cond_destroy waits for sleeping workers, whereas AOSP bionic just marks state destroyed and returns 0. Reproducible BT patch tracks waiters and destruction in one atomic state, retains native cond until the final waiter returns/cancels, then destroys/unmaps it. tests/vm/pthread-cond-destroy.c passes static/explicit init, active destruction, cancellation and timed/clock waits. Full GPU dialog/resize/margins/Back regression passes afterward. VM BT rebuilt and libpthread copied into N. build-native-arm64 uses ninja’s default BT target instead of guessing library version suffixes (pthread .so.0 is a real target, dl .so.0 is a symlink). Next: real MediaCodec JNI/shadow selection and video layer presentation, then audio/media player.

- B native MediaCodec JNI verified: opt-in -Dapklayer.nativemedia=true uses real libmedia_jni, actual AOSP media.player/resource_manager (APKLAYER_MEDIA_SERVICES=1), Codec2 and gralloc. tests/vm/native-media.sh passes discovery in Application.onCreate, createDecoderByType, three VP8 frames with getOutputImage YUV=81/90/240, exact PTS/EOS, stop/release, real HWUI frame commit, screenshot and physical Back; no leaked stack. libmedia_jni is registered under the existing JVMTI adapter; loader completes deferred MediaCodec/List static initializers before Application startup. Corrected AndroidRuntime::mJavaVM mangled length 7 (8 silently left VM null and crashed release). Binder NDK callback pool is started. sys.use_memfd=true selects actual Linux shared memory for FMQ/BLOB; no ashmem shim. BT gains checked pwrite64 from existing fortified write; existing CFI no-op policy also covers diagnostic entrypoint. Minimal XML advertises only the available VP8 component with software-codec attribute: Android vendor store classification reports softwareOnly=false, hardwareAccelerated=false. No hardware claim. Description now uses base Application with APK resources, avoiding app lifecycle execution during install. AppEnvironment/AppMetadata split startup and metadata out of AppHost. Native video Surface presentation, MediaPlayer JNI, PipeWire audio HAL, broader codecs and hardware import remain unfinished.
- Transfer warning: Orb stdout binary data truncated large shared libraries to 512000 bytes in this environment. Copy artifacts to Orb home, scp via /Users/erfanabdi/OrbStack/ubuntu22-x86/home/erfanabdi/<stage>, and verify SHA256. Current media JNI and mediaserver dependencies are staged/hash-verified on VM; Orb home staging dirs apklayer-media-jni-stage and apklayer-mediaserver-stage. Module-only mediaserver commands are /tmp/apklayer-mediaserver-commands.sh in Orb.

- B Surface presentation: opt-in native media + GPU selects an empty SurfaceView shadow inheriting ShadowView, so actual framework holder/attach/BLAST/layer lifecycle runs. Legacy HostSurfaces scanning is skipped in this mode. tests/vm/native-surface.sh decodes VP8 to its real Surface, checks all three rendered timestamps, SurfaceFlinger child layer, screenshot >50000 red pixels (actual 131674), real Back/destroy/release/depth0 and cleanup. First displayed green/chroma stripes: packed single R8 resource cannot be reinterpreted as NV12 on virgl despite successful EGL import. Local AOSP Mesa virgl sources show classic-resource import discards supplied plane offset metadata; a focused GLES probe confirmed separate R8/GR88 resources sample red 254,0,0. Host graphics fallback now allocates distinct R/RG resources per YUV plane (packed=2; old ABI field shape preserved); physical offsets stay 0. Mapper uses new apklayer_gfx_map_linear for one contiguous staged YUV CPU view and reports cumulative CPU offsets. Unlock copies to the actual planes, then EGL imports those directly (no CPU RGBA conversion; software CPU mapping has an extra YUV staging copy). Native hardware YUV allocation remains preferred; hardware decoder import still unverified. NV12/NV21/NV16/I420/YV12/P010 tests verify markers survive unmap/remap and contiguous writes reach each plane. mapper.apklayer rebuilt with existing generated commands /tmp/apklayer-mapper-commands.sh, staged through Orb home and SHA verified. Native MediaCodec getOutputImage and remote Codec2 visible/hidden regressions pass with new layout.

- B VP9 profile 0: shared Vpx adapter is selected by a registry-derived descriptor table; XML adds only the real VP9 component. AOSP profile/level vocabulary reused, no higher bit depths advertised. Minimal libvpx-derived header/index checks (VpxHeaders.h, BSD license retained) distinguish hidden references and hidden+visible superframes. tests/vm/codec2.sh now covers native VP8/VP9 at 64x48 and 80x60, AIDL visible/hidden work for both, and a genuine indexed VP9 hidden+visible superframe. native-media.sh decodes both assets through getOutputImage and verifies YUV/PTS/EOS/stop/release. native-surface.sh reconfigures the same real Surface between VP8 and VP9, verifies VP9 rendered timestamps and red pixels, then real Back/depth0/cleanup. VP9 encoder test input is explicitly I420; unconstrained videotestsrc negotiated a higher-bit-depth profile outside this adapter, correctly rejected by the host raw-format path. Multiple displayed pictures in one input packet currently return C2_CANNOT_DO instead of dropping output; next implement cloneAndSend/multiple outputs, then other codecs and MediaPlayer/audio. No default native media enablement yet. Latest Codec2 output copied from intermediates (module command script ends there, not product/system/lib64), staged via Orb home and SHA verified.

- B multi-output VP9 verified: VpxHeaders parses indexed subframe offsets/sizes, adapter queues each compressed subframe separately to prevent host decoder packet coalescing from dropping pictures. SimpleC2Component.cloneAndSend emits intermediate pictures with FLAG_INCOMPLETE; final work alone carries EOS. CPU gralloc view is destroyed (publishing plane writes) before any cloned callback can expose the buffer. AIDL tests cover multiple visible pictures in first and final input, checking both output ordinals/PTS, flags and YUV. Native MediaCodec getOutputImage receives four pictures from three inputs (two with final duplicate PTS), drains EOS and releases cleanly. SurfaceView test also receives both final rendered callbacks at PTS 66666, checks red pixels and exits via Back. Next: other codecs/reordered output and native MediaPlayer/audio. MediaPlayer native_setup takes a real native Parcel carrying AttributionSourceState; current Java Parcel shadow has no native peer, so native player integration needs a thin real attribution/Parcel bridge (do not pass its fake pointer). Extractor service can reuse existing libmediaextractorservice and MediaExtractorService::instantiate; plugins are under the media APEX lib64/extractors (libmkvextractor exists). No player code changed yet.

- B reordered-output groundwork: host codec API now supports one immutable notification callback installed before input (sample/EOS/error), disableable with NULL; close disables and joins streaming callbacks. Error sync handler publishes atomic failure before notification because GstBus sync callback precedes message enqueue (otherwise a nonblocking drain could miss it). Callback schedules work only, never pulls/closes. tests/hals/codec-reorder-test.c uses real x264enc with bframes=2 and avdec_h264: 12 inputs in coded PTS order, all 12 raw YUV outputs in sorted presentation order, exact timestamps, delayed EOS and availability callbacks pass. Generates /tmp/apklayer-h264.au (A264 header: magic4,widthu16,heightu16,countu32; each packet sizeu32,PTS-us u64,payload). Original encoder timestamps start at 3600000000000000 ns; fixture preserves them in microseconds. H.264 is NOT exposed by Codec2 yet. tests/vm/codec2.sh includes this host test then existing VP8/VP9 AIDL regression.
- Next adapter design (researched, no Android source edits yet): retain existing Vpx synchronous adapter; implement a separate reordered decoder reusing SimpleC2Component pending work. process queues input and leaves workletsProcessed=0. Host sample/EOS/error callback calls drain_nb(DRAIN_COMPONENT_NO_EOS), which queues an event on SimpleC2Component's existing handler; drain then pulls samples nonblocking and finish(frameIndex, fill) by PTS. This avoids a separate worker racing base insertion of pending work (base inserts only after process returns). Base drain_nb marks a queue drain item and posts handler when queue empty; drain runs after current process/pending insertion. Retain an EOS work until actual backend EOS; if it produces a picture first, cloneAndSend FLAG_INCOMPLETE, then finish with empty EOS after all pictures. Need a shared frame-to-gralloc helper/params extracted from existing Vpx adapter; new source module requires Soong graph-only nothing regeneration then module-only build. AOSP C2SoftAvcDec params at frameworks/av/media/codec2/components/avc/C2SoftAvcDec.cpp include output delay default and AVC profile/level vocabulary. Do not claim support until AIDL and Java tests pass with B-frames.

- B H.264 reordered Codec2/app verified: new ReorderedDecoder uses existing SimpleC2Component pending work and drain_nb handler events (no own worker). Callback events run after base pending insertion. PTS maps back to original work index; the EOS input's picture is cloned as INCOMPLETE and its original work completes empty EOS only after backend EOS. DecoderParams/GraphicOutput share AOSP interface vocabulary and the existing YUV mapper copy; mapped views publish before callbacks. AVC profile/level and output delay 16 use AOSP params; XML adds real software AVC. tests/vm/codec2.sh passes all previous Vpx tests plus 12 actual AIDL reordered pictures (input indices 0,2,3,1,...,11,10) and final empty EOS, exact PTS/YUV. native-media.sh also queues 12 AVC inputs without EOS, flushes pending B-frames, replays with EOS, checks all 12 output images/timestamps and release. native-surface.sh plays VP8/VP9/multi then AVC on the same Surface, verifies 12 output PTS, last render callback 3600000366666, red screenshot pixels and physical Back. AvcCheck.java is shared via a source symlink between the test apps; generated red-h264.au asset is 1176 bytes. Capture must wait for actual final render callback, not decode/dequeue completion (an earlier capture saw the still-hidden initial transaction). No audio/player or other new codecs yet.
- AVC build: graph-only nothing regeneration completed (~1m40s CPU-busy Soong + 9s nothing), then exactly 7 generated module commands (4 compile + link/strip/copy). Regenerated /tmp/apklayer-codec2-commands.sh and the link rsp from the generated shard's explicit inputs + libFlags (4 objects now). Current output stage ~/apklayer-codec2-stage in Orb; VM installed SHA 802372266ece2815f57611ff54e874062a0889031704481b14c0644017b2e488 before whitespace-only formatting. All tool/build/test processes finished and test stacks clean. New ReorderProbe.h tests --reorder-probe; codec2-run.py optional 'reorder' chooses that mode. Next media work: error/drain edge cases as needed, other codecs, actual MediaPlayer/extractor/attribution Parcel bridge, PipeWire audio HAL and corpus playback.

- B native MediaExtractor callback-source MP4 verified (FD not tested yet): native-media.sh uses original libmedia_jni/framework JNI, a separate --extractor btload process running AOSP MediaExtractorService, and original libmp4extractor/libmkvextractor plugins. MediaDataSource.readAt crosses real binder back into the JVM (9 reads observed); MP4 track/CSD/dimensions, 12 samples, seek to keyframe, actual MediaCodec decode of extracted Annex-B samples/CSD, YUV/PTS/EOS/release pass. Source callback closes after extractor release. New ShadowHostNativeMediaExtractor only defers setup/library init; loader initializes MediaExtractor alongside Codec/List. MediaServices starts player/RM normally and extractor separately with --extractor. Original dependency libmediaextractorservice + libmkvextractor + libmp4extractor + libstagefright_flacdec and rebuilt entrypoint staged through ~/apklayer-extractor-stage (5 files) and SHA verified. Graph-only nothing analysis ~93s CPU-busy +6s; exact 4 module commands with regenerated rsp in /tmp/apklayer-mediaserver-commands.sh.
- Extractor plugin path fix: BT stripped /system/lib64/ even from nested plugin paths, yielding nonexistent extractors/libmp4extractor.so. Patch 9 preserves nested paths, applies apklayer_apply_path_overrides to absolute DSO paths before search, and bounds fullpath at 512; direct /system/lib64/libc.so-style aliases still strip/map normally. Standalone btload of /system/lib64/extractors/libmp4extractor.so now loads; native extractor test passes. BT default build + libdl_bio.so.0 copied N. Repo-owned Android-root plugin symlinks resolve via ../../../../android-libs/<plugin>; build-native ensures N/android-libs exists and copies the host media HAL too. gen-tree-files-lock also resolves extractor APEX origins. Full GPU dialog/resize/Back and final native-media regression using relative plugin links pass; native-surface rendering/callback/pixel/Back regression also passes. Lock refresh and commit complete this step. No native MediaPlayer or FD compatibility changes yet.
- FD compatibility research: libnativehelper's AFileDescriptor_getFd/create/setFd all call JniConstants, whose eager init assumes Android java.io.FileDescriptor.descriptor/setInt$ and Buffer._elementSizeShift/NIOAccess; HotSpot lacks these. Do not pass a fake Java field pointer or globally enable native Parcel. A thin file_descriptor_jni.c compatibility patch can use actual java.io.FileDescriptor class directly, read Android descriptor with fallback to JDK fd, use Android setInt$ if present else SetIntField(fd), and construct with the real no-arg constructor, bypassing unrelated Android-only JniConstants for FD APIs. Preserve Android behavior too; log/patch/build only the exact existing libnativehelper variant. This is next if an actual FD test confirms the gap. MediaPlayer setup separately requires a real attribution Parcel bridge (see earlier memory); no player code changed.
