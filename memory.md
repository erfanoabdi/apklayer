# apklayer — Project Memory

> Current authorization (2026-10-27): the user asked the assistant to build anything needed in OrbStack `~/los24` autonomously while they sleep. This supersedes older instructions below to ask them to build. Only incremental builds of `gsi_arm64 userdebug`; never clean or wipe `out/`.

Living document of facts and decisions told by the user. Keep this up to date.
The full chronological log up to the first Wayland frames is in `archive/HISTORY.md` at git tag `archive-last`.

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
- The AOSP tree is a reference for source and prebuilts (e.g. JDK); builds in it follow `CLAUDE.md` (incremental only).
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
  Java GLSurfaceView does not render (EGL10 classes are stubs in the sandbox; see docs/history/PLAN-phases-P1-P7.md P4.3).
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
- Milestones A1-A7 (GPU route fixes) and B (media) are done; the dated per-step log is in `docs/history/memory-gpu-followup-2026-10-27.md`. Facts that still bite: restart the stack per test (`ro.*` write-once); full SurfaceFlinger dumps abort in bionic_translation (use `--list`); `/tmp/apklayer-stack/ready` must be cleared before a replacement stack starts.
- Current work (milestone E, hub) follows.

- E hub directory foundation implemented/verified: `bin/apklayer hub` foreground daemon, instance-lifetime flock prevents duplicate JVMs; Main --hub creates sandbox with native jar and no APK/window. HubHost sets contextClassLoader to sandbox BEFORE AppEnvironment setup (missing it initially made ICU/build.prop resources invisible). AppEnvironment accepts null APK for host-only setup. No AOSP/native-shim rebuild needed.
- HubDirectory actualBinder service apklayer.instance owns installed-package/process registrations. HubPackages reuses ShadowPackageParser.callParsePackage to get original component info/intent filters from installedAPK; normalized package paths and APK package names validated. REGISTER requires proper endpointdescriptor, stores kernel Binder callingPID/UID, forbids changing registered package/endpoint, death removes exactpeer. HubClient attaches actualapp endpoint when instancehub flag; status test proves hubPID differs from both appPIDs and peers=2. Native kernel UID staysactualsharedhostUID; fullphone isolation remainsP9.
- tests/vm/hub-smoke.py passes headless readiness and singletonlock. tests/vm/binder.sh hub passes directory registration of2appJVMs, originalBinder echo/callback/descriptor/caller identities/serverSIGKILL/death, gracefulclientfinish and bothpeer removals. Controller owns/drains/reaps all3JVMgroups andstopsstack. Next actual Context.bindService/ServiceController endpoint dispatch, auto service-host launch, exported/permission checks, unbind/death/lifecycle tests using generated AIDL. Still no framework bindService/hub autostart completion claim.

- E1 framework binding verified: HubServices uses original LoadedApk.getServiceDispatcher/forgetServiceDispatcher and actual IServiceConnection (CURRENT tree connected signature has4args: ComponentName,IBinder,IBinderSession nullable,boolean). Do not hand-reimplement Android dispatcher/death logic. ShadowHostContextImpl intercepts cross-package binds (classic+Executor) and unbind; existing converter Services fallback reaches these shadows. HubEndpoint checks hubkernelPID, posts lifecycle to appmain, calls existing Services.java ServiceController logic. HubDirectory/HubServiceBindings own pending links, normal permission/exported checks, process launch/death/restart and client-death unbind. Serialized outbound command worker prevents bind/unbind cancellation reordering; no Binder call under directory locks.
- ComponentHost headless Main --component-host mode uses same Application/sandbox resources/looper without creating vdisplay or appwindow. bin/apklayer component-host <pkg> starts it; AppMetadata install supports service-only APK with app label/icon and empty launcher. Hub starts requested app lazily and keeps cached process after service destruction, like a cached Android app. Original PackageParser retains manifest service intent filters; normal declared/requested permissions enforced, other permissions currently deny (broader signature/runtime grants still need actual grant integration).
- HubStartup automatically starts detached instance daemon via setsid CLIhub and awaits actual Binder publication. RealBinder.await(name,timeoutMs) nativehelper uses NDK registration notifications + GLIBC monotoniccond, not polling. APIBORROWS instance-name constchar until notificationdelete; initial auto-start freedJNIUTFchars tooearly and aborted onServiceRegistration(comparegarbage). Fixed by keepingUTFcharsuntil unregister joins callbacks. Basic binder test checks actual missing-service30ms deadline; boundauto tests actual registration success path.
- tests/vm/hub-services.sh [bound|auto] builds SDKgenerated IEcho/IReply AIDL from tests/apps/aidl (build.sh supportsfixture aidl.list). ServerAPKhasNOactivity, declaredService returnsrealAIDL. Client ordinaryContext.bindService verifies private/missing-permissiondenial, missingcomponentfalse, nullbinder, requestednormalpermissiongrant, Unicode+Bundlearraypayload, actualnativecallerPID, oneway callbackPID0+actualUID (AOSP Binder.java explicitlydocumentsPID0foroneway; doNOTfakePID), mainthreaddispatcher, serverSIGKILL/linkToDeath/onServiceDisconnected, autoBIND_AUTO_CREATE restart/newPID, unbind and actualserveronDestroy callback beforeclientfinish.20immediatebind/unbinds cancelwithoutlatecallback/lifetimeleak. Both explicit-hub and automaticallystarted-hub runs pass; alltestprocessgroups/stackcleaned. Actualheadlessvdisplay-process assertion added. No AOSP build for E; host+shim install-shim updated.
- Remaining E service breadth: HubServiceBindings currently one Binding per IServiceConnection, and existing Services.java has one onBind record perclass, not Intent.FilterComparison. Extend multi-binding/filter-specific rebind and started-service lifecycle during E4; do not claim fullServicecompatibility. Actual E1 two-APK boundAIDL+death/reconnect milestone passes. Next E2 broadcasts: use original LoadedApk receiver dispatch and IIntentReceiver, preserve PendingResult/goAsync and ordered result/abort/permissions/sticky/manifest lifecycle; then E3 providers/observers, E4 activities/results/startService/PendingIntent, E5 notifications. User wants wholeMilestoneE, continue without stopping at E1.

- Service-binding checkpoint requested by user: Markdown status consolidated so PLAN/HANDOFF no longer say the already-working hub/bind route is next. ARCHITECTURE and AOSP-BUILDS describe the opt-in route and actual remaining gaps. Final hub-services.sh auto passes the explicit no-server-vdisplay process assertion and 20 cancellation stress, native death/reconnect and onDestroy acknowledgement. GPU regression passes real dialog/resize/Back/clipboard with16px margins. No broadcasts/providers/activity/PendingIntent/notification implementation is claimed yet; resume at E2 after this commit.

- E2 broadcasts first part verified (tests/vm/broadcasts.sh manifest|basic|ordered|sticky, hub-services auto still passes): HubBroadcasts is the queue (one thread, ordered holds it, 10 s per ordered receiver), dynamic receivers register their framework IIntentReceiver binder with the hub, manifest receivers are reached by IApplicationThread.scheduleReceiver on the peer's registered application thread (REGISTER now carries it; the hub starts a component host when the package is not running); both finish through the application thread token (finishReceiver). Robolectric shadows that defeat this are overlaid in runtime/patches (PendingResult, BroadcastReceiver). Open: goAsync across apps, unregister/death cleanup tests, system sticky seeds, E3 providers next.

- WebView APK to use (user, 2026-10-27): /Users/erfanabdi/OrbStack/ubuntu22-x86/home/erfanabdi/los24/external/chromium-webview/prebuilt/arm64/webview.apk (read-only; copy to the VM for tests).

## UiBench GL TextureView (2026-10-27)
- Finished the user's TextureView WIP on the real GPU route. Original TextureView/TextureLayer/SurfaceTexture JNI, EGL10/GLImpl, GLES20 and GLUtils render the original canyon bitmap through a real buffer queue and HWUI; no UiBench source changes.
- Use the actual Android libnativedisplay (already staged), removing the obsolete whole-library mapping to weak stubs. Original SF supplies display/choreographer services. EGL retains its ANativeWindow through eglDestroySurface, including EGL10's temporary Surface wrapper.
- Sandbox acquires Android's javax.microedition.khronos classes instead of outer SDK stubs. GLES20 static initialization is deferred until JNI registration. libnativehelper's NIO helpers use public Buffer methods, element types and JNI direct addresses rather than Android-only fields/NIOAccess.
- `tests/vm/texture-view.sh` passes: two captures have >60k canyon pixels, >20k changed animation pixels, >80 real dmabuf frames, physical Back reaches depth 0, no leftover runtime processes. Capture briefly SIGSTOPs submissions to avoid Parallels partial scanout reads, then always resumes. GPU dialog/resize/Back/clipboard and original Chromium WebView JavaScript probe regressions pass.

## Modern Combat 3 launch check (2026-10-27)
- User supplied Modern-Combat-3-1.1.7g APK and `com.gameloft.android.ANMP.GloftM3HM` data directory. APK converted (4960 classes), installed as MC3, and launched on the ARM64 device-library route.
- Blocked before gameplay: `UnsatisfiedLinkError: no ModernCombat3 ... (ABI not in the APK?)` from GL2JNIActivity/onResume. APK contains libgenerator.so/libModernCombat3.so for armeabi, armeabi-v7a and x86 only; no arm64-v8a or x86_64. The ARM64 JVM/bionic linker cannot load these 32-bit engines. Java/Dex conversion does not translate native instructions or pointer ABI.
- Local data contains main.1120 and patch.11428 OBBs (1,424,225,001 bytes combined). Not copied to the VM because native engine loading fails before data use. No gameplay/render/input pass claimed; supporting this APK requires a separate 32-bit execution path (and hardware/translation support), or an ARM64 build of the engine. Full VM launch log: `/tmp/apklayer-mc3.log`. App and native stack stopped; no runtime processes left.

## Vulkan G1 checkpoint (2026-10-28): native execution works, presentation unfinished
- Original Android libvulkan now runs over a `vulkan.apklayer` frontend to the host libvulkan.so.1. Reuse AOSP's API/WSI implementation rather than duplicating its swapchain machinery. Host dispatchable handles retain their original vtables; Android private data lives in a guarded side table, retired on buffer/pool/device/instance destruction (`scripts/patch-vulkan.sh`).
- `scripts/native-env.sh` preserves system XDG data paths so the host loader finds actual ICD manifests. The optional renamed loader (`libapklayer_vulkan.so`) and HAL activate the host dispatch mode. `scripts/orb-build-vulkan.py` incrementally rebuilds the original loader and two small tools with existing generated arm64 commands; `scripts/install-vulkan.sh` stages and SHA-verifies them. No clean or product change.
- `tests/vm/vulkan.sh` runs a real APK through JNI: six Vulkan instance/device lifecycles, actual image clear/copy and 256 pixel checks per cycle, raw Android symbol and host PFN dispatch, alternating explicit command-buffer free/pool destruction, enumeration VK_INCOMPLETE handling, rejection of unsupported swapchain enablement, frame-commit screenshot, physical Back and process cleanup. Driver truthfully reports llvmpipe (CPU), Vulkan 1.4.318, dmabuf available, DRM modifiers absent, sync-fd semaphore features zero. The visible test UI uses HWUI OpenGL; it is not a Vulkan presentation demo.
- **G2/G3 remain required:** implement VK_ANDROID_native_buffer image import from our gralloc dmabufs (format/modifier/memory-type validation and FD ownership); vkAcquireImageANDROID/vkQueueSignalReleaseImageANDROID synchronization; Android AHardwareBuffer Vulkan memory import/export; verify original WSI acquire/present/resize/abandon and multiple swapchains; then enable and test HWUI skiavk. The HAL and loader deliberately withhold Android swapchain/presentation-dependent extensions and report no surface support until native-buffer support exists. No zero-copy, Vulkan gaming or hardware presentation claim.
- End-to-end G2/G3 verification **needs a real Vulkan GPU/driver** with compatible dmabuf/modifier import and sync-fd semaphore support. The current Parallels virgl VM cannot supply those capabilities. Do not mark P7.5/Milestone G complete. TextureView canyon pixels/animation/Back and Chromium WebView JavaScript regressions pass with the new loader installed.

- OTR2 1.3.2 ARM64 APK installs but fails before window creation: missing OpenSL ES `slCreateEngine` from libgame.so; native GLES3/EGL game, graphics not reached. See tests/corpus/results.md and VM /tmp/apklayer-otr2.out.

## OTR2 launch (2026-10-28)
- APK `~/Downloads/OTR-2-1.3.2-Mod(www.Farsroid.com).apk` (com.dogbytegames.otrnext, 984 MB, AGDK GameActivity + cocos2d-x JniHelper + bgfx). Test driver on the VM: `/tmp/apklayer-otr2-test.py` (not in the repo); needs `-Dapklayer.nativeresources=true -Dapklayer.nativemedia=true -Dapklayer.realbinder=true -Dapklayer.instancehub=true` (nativemedia is what turns SurfaceView into the real BLAST-backed one on the GPU route).
- Fixed: OpenSL ES from the tree, class-loader cast adapter (GameActivity), native UI looper prepare, native-resource mode ("No start tag found": the `@CriticalNative` methods of android.content.res are not in the javac shards, listed in `bridge/native/critical-natives-extra.txt`), slash binary names in `SandboxClassLoader.loadClass` (Paddleboat loads `a/b/C` the way ART allows). UiBench boots in native-resource mode and `tests/vm/gpu.sh` still passes.
- Also fixed: Swappy's embedded-dex helper class: `ShadowHostBaseDexClassLoader` converts `InMemoryDexClassLoader` dex with dex2jvm (now on the host classpath, `build-tree-host.sh` compiles against it) and defines classes on demand; "Using internal SwappyDisplayManager class from dex bytes" and its looper thread start. `libapklayer_egl` logs failed `eglCreateContext` attributes and error; `gbm_bo_create` failures are logged by the graphics HAL.
- bgfx first tries Vulkan (llvmpipe through the new libvulkan frontend), then GL: it asks for an ES 3.1 context (`EGL_CONTEXT_MINOR_VERSION_KHR=1`), the VM's virgl (Apple host, no compute) reports ES 3.0 only, so `eglCreateContext` fails with EGL_BAD_MATCH. With `MESA_GLES_VERSION_OVERRIDE=3.2` the context is created (diagnostic only, not a fix). The game needs a GPU with ES 3.1+.
- Open after that (seen with the override): (1) the SurfaceView's 1280x720 BLAST buffers fail in `GraphicBufferAllocator::allocate` (status UNKNOWN_ERROR, many times; not a gbm_bo_create failure, cause not found), screenshot shows the action bar and a black area; (2) WebView creation in native-resource mode: `WebViewDelegate.getPackageId` "Package not found: com.android.webview" (the provider-resources path was built for the legacy ApkAssets), the uncaught exception ends the app; (3) `AChoreographer: No JVM provided`. No game frames yet.
