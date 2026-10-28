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

## Current state (2026-10-28)
- The only route is the device-library route: Android's own arm64 libraries (SurfaceFlinger, HWUI, libandroid_runtime, media, binder) on
  bionic_translation, the framework on the JVM (unmodified `android-all` + Robolectric shadows), apklayer's HALs and `apklayer-shell` underneath.
  The old CPU-only route (x86/Rosetta, bundled Mesa llvmpipe, GStreamer MediaPlayer, WebKitGTK WebView, simulator CPU frames, the F-Droid
  corpus harness) was removed on 2026-10-28; its notes are in `docs/history/memory-cpu-route-2026-10-26.md`.
- Commands: `bin/apklayer install|run|list|uninstall|stop`; apps in `~/.local/share/apklayer/apps/<pkg>`. Build: `meson setup b && ninja -C b`,
  `scripts/build-tree-host.sh ~/arm64-native`. Tests: `tests/vm/*.sh` (run on a test VM/device, see below).
- Facts that still bite: dex2jvm reads the framework class hierarchy from ITS OWN classpath (run it with `android-all` on it, away from any Android
  SDK); its output needs `@DoNotInstrument` (`DenyInstrument`); SDK 36 (`android-all-instrumented-16-robolectric-13921718-i7`) needs the
  `runtime/host/jvm.args` `--add-opens`; `android-stub.jar` is the SDK `platforms/android-36.1/android.jar`; Robolectric reads `android_resource_apk`
  only from the classpath `/com/android/tools/test_config.properties`; `LazyApplication` is in `org.robolectric.annotation.experimental`.
- `pkill -f <pattern>` over ssh can kill the ssh shell itself; use `pkill -x` or a `[x]yz` pattern. macOS `._*` AppleDouble files: `COPYFILE_DISABLE=1 tar`.

## Test VM (UTM on this Mac, since 2026-10-28)
- `ssh utm@192.168.64.7` (password in CLAUDE.md), Ubuntu 26.04 arm64, 6 CPUs, 9 GB, GNOME Wayland (`wayland-0`, `XDG_RUNTIME_DIR=/run/user/1000`), kernel 7.0.
  GPU: Venus Vulkan 1.4 on the Apple M5 Max, virgl ES 3.0 (zink gives ES 3.1 but aborts in Venus). `binder_linux` is a module that cannot be unloaded and
  ignores `devices=` once loaded: create the binderfs nodes with the `binder-control` ioctl (see `scripts/vm-binder-setup.sh`).
- `/tmp` is tmpfs (RAM): Robolectric leaves ~120 MB per aborted JVM; clean `/tmp/robolectric-nativeruntime*` and never put big APKs there.
- Screenshots: `tests/vm/vmcapture.py` (flameshot in the guest session). Real input: `tests/vm/uinput-keys.c` (gcc on the VM, run with sudo).
- Layout on the VM: `~/apklayer-m1` (synced repo, build in `b/`), `~/arm64-native`, `~/android-arm64`, `~/bt-arm`, `~/.cache/apklayer/deps`.


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
- **OTR2 renders (2026-10-28):** title screen with real GPU frames (`tests/vm/otr2.sh`, `OTR2_APK=<apk>`; 98% of the window lit, 1176 colors). The last blocker was `eglGetConfigAttrib(EGL_NATIVE_VISUAL_ID)` returning the host's DRM fourcc: bgfx passes it to `ANativeWindow_setBuffersGeometry`, so every SurfaceView buffer was requested with format 0x34324241 and failed in `GraphicBufferAllocator` (mapped to the HAL pixel format in `libapklayer_egl`).
- OTR2 asks for an OpenGL ES 3.1 context (`EGL_CONTEXT_MINOR_VERSION_KHR=1`); virgl (both VMs) reports ES 3.0, so `eglCreateContext` fails with EGL_BAD_MATCH. `scripts/native-env.sh` probes the host once (`eglinfo`, cached in `$XDG_RUNTIME_DIR/apklayer-host-gles`) and, below ES 3.1, exports `MESA_GLES_VERSION_OVERRIDE=3.2` (logged; `APKLAYER_GLES_OVERRIDE=off` disables): features the driver truly lacks fail when used. zink on Venus gives a real ES 3.1 but aborts inside Mesa's Venus driver on the UTM VM. The game also probes Vulkan first; `apklayer.vulkan: Android presentation not enabled` keeps it on GL.
- Open: `AChoreographer: No JVM provided` (harmless so far); input and audio in the game are not checked.

## CPU-only route removed (2026-10-28)
- Gone: the x86/Rosetta flavor and the arm64-Robolectric fallback in `bin/apklayer`, `scripts/{build-native,fetch-robolectric-arm64,check,corpus,fetch-corpus,vm-run,aosp-term}.sh`, the Meson host jar (the host is built by `scripts/build-tree-host.sh`), the NDK shims of that route (`bridge/ndk` keeps only `libselinux.c`), the GStreamer/WebKitGTK python backends, the simulator CPU frame path (`ShellFrameListener`, snapshots), the legacy shadows (`HostSurfaces`, `HostWebViews`, `ShadowHostMediaPlayer/SoundPool/MediaCodec/AudioTrack/SurfaceView/WebView`, `HostCodecs`, `HostAudio`) and the tests of that route (`p2-host`, `e2e`, `native`, `corpus`, `media`, `video`, `codec`, `audio-host`, `smoke`, `share`, `notify`, `hello-gl2/media/video/codec/webview`).
- Consequence: media, audio and WebView need the native stack (flags `nativemedia`, `nativeaudio`, `nativeparcels`, `nativeplayer`, the WebView provider APK); without them Robolectric's stock fakes apply. Collapsing those flags into one default mode is the next cleanup.
- Verified after the removal on the UTM VM: `tests/vm/gpu.sh`, `tests/vm/otr2.sh`; the meson build is clean. Not run: `tests/vm/webview.sh` (the VM has no WebView provider set up), the media/audio scripts. `tests/vm/hub-services.sh auto` fails on the UTM VM with "oneway native callback", also before the removal (not a regression; look at binder oneway on kernel 7.0). Several drivers still call `prlctl` for screenshots (need `vmcapture`).

## Runtime flags removed (2026-10-28)
- The feature flags `apklayer.gpu`, `realbinder`, `instancehub`, `nativemedia`, `nativeaudio`, `nativeparcels`, `nativeplayer`, `nativeattribution`, `nativeresources` and `nativelooper` are gone: the native stack is the only mode (real Binder and Parcel, the instance hub for every app, native media/audio/MediaPlayer, native resources, the native looper, real SurfaceView/TextureView). The selective native-Parcel shadow, the desktop notification follower (the hub shows notifications) and the CPU shm frame path of `ShellClient` went with them. Diagnostic switches stay (`logcat`, `perf`, `trace*`, `debug*`, `threads`, `runtimetrace`, `script`, `runms`, `hwbitmaps`).
- Services: `scripts/arm64-stack.sh` starts every staged service (codec2, mediaserver and extractor, audio HAL, effects, audioserver) and `scripts/native-env.sh` loads every staged host HAL library; the services are chosen by what is staged, not by env flags (`APKLAYER_CODEC2/AUDIO/...` are ignored).
- `scripts/install-shim.sh` needs `bridge/native/android_stubs.c` (generated, not tracked: copy `~/bt-arm/android_stubs.c`); gcc 15 (Ubuntu 26.04) makes implicit declarations errors (`app_native.c` needs `_GNU_SOURCE`).
- Verified on the UTM VM after this: gpu smoke, OTR2, binder, native-media, native-surface, services, broadcasts. Known failures that are not from this change: `texture-view.sh` (GL TextureView renders black on the UTM VM, same on the commit before), `native-player.sh` ("Native audio policy status 1": the audio HAL's first output stream open fails inside the audio service, `AHAL_Stream: initInstance: Worker start error: Failed to initialize the driver: -5`, with PipeWire 1.6.2 on the UTM VM; the VM does have a working sink and the standalone `tests/hals/audio-test.c` opens a stream, so the cause is open: look at the stream parameters/target the policy asks for and `bridge/hals/audio/audio.c`), `hub-services.sh auto` ("oneway native callback").
