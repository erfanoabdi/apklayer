# apklayer: plan

Decisions that hold, the roadmap, and what is open. History: `CHANGELOG.md`, `docs/history/` (the previous long plan is `docs/history/PLAN-2026-10-28-full.md`).
If a decision changes, change it here in the same commit as the code.

## Decisions
| Topic | Decision |
|---|---|
| Framework | Unmodified AOSP `android-all` SDK 36 on the JVM with Robolectric shadows; never edit android-all, Robolectric, dex2jvm or bionic_translation (thin, listed patches only: `docs/PATCHES.md`). |
| Native side | Android's own arm64 libraries from a LineageOS 24 `gsi_arm64 userdebug` build run under bionic_translation on kernel binderfs: servicemanager, SurfaceFlinger, HWUI, media, audio. apklayer's HALs (`aosp/`, `bridge/hals/`) back them with GBM/EGL/Vulkan, PipeWire, GStreamer. No CPU-only route, no stand-ins. |
| Apps | dex2jvm (upstream kksimp, Apache-2.0) converts DEX; one JVM per app; windows go to one per-session `apklayer-shell` (Wayland), one virtual display per app. |
| Cross-app | The instance hub (real Binder between app JVMs): bound/started services, broadcasts, providers, activities, notifications, pending intents. System services stay Robolectric shadows (no system_server) except where a process boundary exists. |
| WebView | The Chromium WebView provider APK, as on a device. |
| CLI and build | C++ tool in `cli/`; Meson for native code; pinned Java deps in `deps/deps.lock`. |
| Readiness | Properties of the instance (`apklayer.stack.ready`, `apklayer.hub.ready`) waited on with `aprop wait`; no polling, no timers. |
| License | AGPL-3.0-or-later for everyone plus a paid commercial license (`COMMERCIAL.md`, audit in `docs/THIRD_PARTY.md`). |
| Hosts | arm64 Linux today. x86_64 needs an x86_64 build of the Android libraries (a second product/out dir: ask first). |
| Distribution | Flatpak first, deb/rpm/AUR/AppImage later. |
| Verification | Every feature has a scripted check on the test VM with real input and a screenshot (`tests/vm/`); run only the groups for the code you changed. |
| Builds in `~/los24` | Incremental only, never clean `out/`; rules in `docs/AOSP-BUILDS.md`. |

## Storage volumes (done 2026-10-28; user's design)
Each instance has its own `/storage`: `$XDG_DATA_HOME/apklayer/<n>/storage` (n = 0 for the default instance, 1 for a second one; `Instance::storageDir`, passed to every JVM as `-Dapklayer.storage`). The storage service (`ShadowHostStorageManager`, `HostVolumes`) lists three volumes:
1. **Internal storage**: the emulated primary volume `<storage>/emulated/0` (the instance's `/sdcard`; `Environment.getExternalStorageDirectory()` returns it). DocumentsUI root id `primary`.
2. **Home**: the Linux user's home (stub volume `linux-home`; `home` is DocumentsUI's own Documents-folder id).
3. **Linux root**: `/` (stub volume `linux-root`); apklayer reads what its user may read and writes what it may.
The Android root is known to the runtime already. Open: which volumes ordinary apps (not DocumentsUI) get through the file APIs (today no app path reaches Home or `/` except through DocumentsUI and the grants it hands out); runtime-permission rules for storage (`READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`); the real MediaProvider (today `HostMediaStub` answers "nothing indexed").

## Plan: the real Android keystore (decided 2026-10-28, not started)
Replace `HostKeyStoreProvider` (software JCE keys; no attestation, no `KeyInfo`, no enforcement of purposes/digests, no auth-bound keys) with Android's own stack on the device libraries:
1. **HAL services from AOSP, not rewritten**: build the reference software KeyMint (`hardware/interfaces/security/keymint/aidl/default`, backed by `libkeymint_fake_latest` / libkeymaster, already in the tree) and the software Gatekeeper (`hardware/interfaces/gatekeeper/aidl/default`) as vendor modules (`aosp/hals/security/`, thin Android.bp that points at those sources and the tree\'s libraries, like the other HALs). The tree already has `system/bin/keystore2` and `gatekeeperd`. Needs one build-graph regeneration (new modules): ask the user first.
2. **Stack**: the stack daemon starts `keystore2` (its database under the instance state dir through the path layer: `/data/misc/keystore`), the KeyMint HAL and the Gatekeeper HAL next to the other services; their VINTF fragments go in `bridge/native/android-root/vendor/etc/vintf`; `servicemanager` registers `android.system.keystore2.IKeystoreService/default`.
3. **Framework side**: drop the host KeyStore provider; Android\'s own `AndroidKeyStoreProvider` (keystore2 client in the framework jar) talks to the real service through the real Binder already used by the other services. Per-app uid: keys are owned by the calling Android uid (the work-source uid fix gives oneway calls an identity; keystore2 reads the kernel uid, which is one Linux uid for all apps, so keystore2 needs the hub\'s uid-of-pid mapping or one keystore2 namespace per app JVM).
4. **Attestation**: software KeyMint attests with its built-in test key: a real chain of the right structure and software security level, not Google-rooted (apps that need a Google-rooted chain stay out of reach).
5. **Tests**: `tests/vm/keystore.sh` keeps its AES/HMAC/RSA/EC checks; add `KeyInfo`, purpose enforcement, attestation chain, auth-bound key with the software Gatekeeper.
Open: how keystore2 learns the caller uid (point 3); gatekeeper password enrolment on a desktop (no lock screen: an empty-credential enrolment).

## Roadmap
| Phase | State |
|---|---|
| P1 CLI, install cache, shell protocol, SDK 36 | done |
| P2 window and input (resize, wheel, touch, clipboard, IME, lifecycle) | done |
| P3 compatibility corpus | **empty**: the old harness went with the CPU route; to be rebuilt on this route |
| P4 native `.so` in apps | done (OTR2 renders) |
| P5 platform services | WebView, media, audio done; microG, portals, camera, sensors, GNSS, Bluetooth open |
| P6 apps talking to each other (hub) | done for services, broadcasts, providers, activities, notifications, pending intents; system apps/providers on every instance open |
| P7 GPU route | GL done; Vulkan implemented, opt-in, blocked on the UTM host stack (`APKLAYER_VULKAN_PRESENT=1`) |
| P8 ship (Flatpak, x86_64) | not started; needs the x86_64 Android libraries |
| P9 several phone instances | deferred by the user |
| P10 CTS | strictly last |
| SystemUI | a yardstick, never a design driver |

## Parked: things to revisit (collected from the old plan and notes; details in `docs/history/`)
**Broken or failing today**
- TextureView renders black on the UTM VM (`texture-view.sh`; passed on the earlier Parallels VM). What is known (2026-10-28): the host stack is not the limit: `tests/vm/dmabuf-share.c` (one host context fills a gbm dmabuf, another imports it and samples it as an external texture) passes on virgl, linear and default modifier. The app's producer (UiBench `ImageFlipRenderThread`, a static image redrawn every 2 s) does render the canyon correctly into its window buffer (`APKLAYER_EGL_PROBE=1` logs the centre pixel before and after the copy in `presentToWindow`: 251,196,141), the consumer (HWUI) imports that buffer once, and the quad on screen is still solid black; not the fence of the copy (`APKLAYER_EGL_NOFENCE=1`: glFinish, no fence, same result); no EGL or consumer error is logged. More (same day): `APKLAYER_EGL_PROBE=1` also logs each native-buffer import with its thread and each `eglSwapBuffers`: the producer (Thread-4) swaps once and imports the 720x1216 buffer for its copy, SurfaceTexture's frame-available reaches Java once (a later frame replaces a queued one without another event), and the RenderThread imports the same buffer right after (so the consumer does get it) and then only draws window frames; no GL error appears with `MESA_DEBUG=1`; Skia's `glEGLImageTargetTexture2DOES` does not come through our `eglGetProcAddress` (a wrapper there never fired), so what HWUI binds and samples is still unobserved. A CPU copy into the buffer is impossible (its dmabuf refuses mmap, EACCES). Next: see the renderer's GL calls (a GL wrapper library like `libapklayer_egl`, or apitrace if it can interpose through the bionic translation), or check `DeferredLayerUpdater` (image creation, usage 0x300 = renderable so Skia binds it as GL_TEXTURE_2D), and why the producer presents only once. `audio-aidl.sh`: the isolated monitor finds no clean left channel tone.
- SettingsProvider: starts and migrates since the system-config fixes, then fails reading its own ABX settings files on the second start; `Settings.*` therefore stay Robolectric's in-memory fakes (`-Dapklayer.realsettings=true` is the opt-in). UserDictionaryProvider inserts return null.
- Vulkan presentation: opt-in, Venus aborts on imported virgl dmabufs on the UTM host; needs a real-GPU host or a Venus-allocated swapchain bridge.
- Camera: not implemented (no camera HAL; apps see no camera).
- `codec2.sh` passes its decoding checks but its last greps fail (`tool.log` is gone by then); `activities.sh` fails now and then in the `again` phase (the caller JVM exits at once with status 0, nothing printed); WireGuard aborts (SIGABRT) in its native code; OTR2 hits the app-side WebView glue (`android.webkit.WebViewFactoryProvider` not visible to the provider's classes) and takes longer than the corpus patience; Nextcloud: `NativeLoader.setLibraryPath` is not bound when the app's own Conscrypt loads in `Application.onCreate`; Telegram: converted jar lacks `ConnectionsManager` (a class dropped by the converter); WhatsApp: SoLoader cannot find `liblog.so`; drminfo: Firebase init throws.

**The user asked to revisit**
- The user's other test apps in `~/TestApps` (some fail: converter, classpath conflicts such as NewPipe's R8-merged `kotlin.math.MathKt`): record app/status/cause and fix by cause.
- Desktop notifications: popup policy is done; per-app mute, closing a popup when the app cancels, and notification actions are open.
- OTR2: input and audio in the game are unchecked; `AChoreographer: No JVM provided` (harmless so far). P2: 2x HiDPI output and a real IME were never exercised.

**Missing features**
- System apps and providers on every instance (Settings, Contacts, Calendar, Download, Telephony, UserDictionary, DocumentsUI, MediaProvider) hosted in an instance system JVM.
- Hub gaps: several bindings per connection, filter-specific rebind, signature/runtime permission grants.
- Keystore: key pairs exist; attestation, `KeyInfo` and user-authentication-bound keys do not. Hardware domains (`aosp/hals/README.md`): network, battery, USB, location (GeoClue) and the file chooser portal are host-backed Java shims now (`tests/vm/devices.sh`, `portal.sh`); camera, a real GNSS receiver, ModemManager ("no SIM" is the honest answer today), screen capture portal are open. **Not this round** (user, 2026-10-28): Bluetooth (BlueZ bridge), NFC, biometrics, sensors, vibrator/haptics and game controllers. Decided: backlight and thermal are the host's; accounts the Android way.
- microG; per-app/instance sandbox (bwrap is blocked by Ubuntu's userns restriction); other codecs, higher-bit-depth VP9, hardware decoder dmabuf import.
- Compatibility corpus (P3): runner and first results exist (`tests/corpus/`); triage and more apps are the next task in `STATUS.md`.

**Before a public release**
- COMMERCIAL.md contact and terms, a CLA and ownership of the agent-written commits, NOTICE/legal files for everything shipped, the Android SDK `android.jar` terms if bundled.
- x86_64 build of the Android libraries (needs approval for a second out dir), Flatpak, replacing the ninja-replay build scripts, per-run test temp dirs.
- Files over 600 lines: `ShadowWindowManagerGlobal` (patched upstream overlay), `Egl.cpp` (needs a build-graph change).
- Robolectric's instrumentation of `android.app.AuthenticationRequiredException` crashes in ASM (`Frame.merge` NegativeArraySizeException) when DocumentsUI first loads it (after a directory load that raised any exception). Not fixed: avoid the exception; find the instrumentation failure when it matters.
- Window size at open: new windows are un-maximized now (the shell undoes the compositor's automatic maximize once), but a 720x1280 phone window is taller than a laptop screen and its bottom is off-screen. The shell's first configure computes a phone shape at 85% of the output height (`xs_configure`, `fit_sent`) but the app still receives 720x1280: the configure at that moment does not take (the sink or the app is not connected yet); resending it with the first frame is the next try. Until then drag/scroll can miss and bottom buttons are off-screen on small screens.
- WebView provider class clash: an app that bundles Chromium/Cronet base classes (microG, Meet) and uses the WebView needs the provider in its own class loader (child-first for `org.chromium`, `org.jni_zero`, `WV`, `J`, `com.android.webview`; the shim's FindClass must try that loader first for those names). Today the provider's core classes come first for microG only (`APKLAYER_PROVIDER_FIRST=1` forces it for another app) and last for every other app.


## Open after the compatibility pass of 2026-10-28 (details per app in `tests/corpus/results.md`)
- **Black or half-drawn windows** (Unciv, TIC-80, Lemuroid, Logseq): the content rectangle is smaller than the window (Logseq: 460x816 inside a 720-wide frame) or never drawn; Klooni, Mindustry and Shattered Pixel Dungeon (also GLSurfaceView) draw. Look at the surface/window size handshake (`hostResize`, orientation requests) before anything app-specific.
- **Open Sudoku**: its `CREATE TABLE puzzles` string comes out with `rating_level` twice. The statement is built by `StringBuilder.append(Object)` of column enum values held in locals; suspected miscompiled register reuse in the vendored dex2jvm converter (not confirmed).
- **Go runtime apps** (WireGuard, Rethink DNS): cgo `inittls` cannot find the pthread key under the bionic TLS emulation.
- **Joplin**: React Native QuickCrypto bundles OpenSSL `libssl`/`libcrypto`, which clash with the device's BoringSSL `libcrypto.so` in the single linker namespace.
- **Fennec**: crash in `libmozglue` `GeckoLoader.putenv` (mozglue replaces libc symbols).
- **Godot 4 (Pomodorot)**: needs Vulkan presentation (`VK_KHR_android_surface`), blocked on the UTM host stack.
- **OpenTTD**: after the libc fortify fix, "Couldn't find any suitable video driver" from its SDL 1.2 Android port.
- **AnkiDroid**: crash after launch hidden by ACRA; **Puzzles**: anti-piracy redirect; **NewPipe**: empty Trending; **Sensors Multitool**: relaunches its MainActivity.
- Keystore, Google accounts: still waiting for the real keystore2 + software KeyMint plan above.

- **Fennec** (2026-10-28): past mozglue's putenv interposer (dlsym RTLD_NEXT fallback in the translated linker). GeckoThread runs, loads sqlite and nss, then "Could not find profile folder" and the runtime shuts down; no native message reaches the log. Next: Gecko's stderr/android log, its child-process services through the hub, the profile directory.
- **Joplin**: with the bundled OpenSSL libraries renamed (`scripts/rename-bundled-system-libs.py`, not yet run by `install`) the JS bundle runs, then the process aborts: `libc++abi: Fatal error: glibc detected an invalid stdio handle` (a C++ terminate message written through a bionic FILE).
- **Conversations**: aborts at start; `NativeCrypto: could not find field descriptor` (a Conscrypt JNI lookup of `FileDescriptor.descriptor`, which is `fd` in the JDK).

## Adwaita window decoration (decision)
- libadwaita and GTK4 are not on the VM and a GTK window cannot take our dmabuf subsurface path without reworking it, so option (a): the shell draws the header bar itself.
- bridge/shell/decor_draw.c draws it with cairo (new dependency libcairo2-dev): 46 px bar, Adwaita colours, round buttons (Back left; minimize, maximize, close right), bold centred title, rounded top corners.
- Light/dark is read from gsettings color-scheme when a window is created (no live switch yet; the portal Settings would give that).
- Hit testing, drag, double-click maximize and content-relative input are unchanged (DECOR_H 32 -> 46).
- Title is the window title, or the package name when the title is the internal apklayer:pid:instance:pkg one.
## Splash screen (shell)
Until an app presents its first frame, `bridge/shell/splash.c` fills the window with the app logo (installed `icon.png`) on a colour derived from it. Tested with `tests/vm/splash-shot.py` (stand-in client, private XDG_RUNTIME_DIR; a second full Android stack cannot run next to another because of the single /dev/binder). Not done: fade-in/out, re-draw on resize before the first frame, a real-app run (needs the default stack free).


## Task order (2026-10-29, set by the user before going offline)
1. **Firefox (Fennec) must work, whatever it takes.** State (2026-10-29): starts, shows onboarding and home page, loads pages over the network (a local http server sees the GET), content processes (GeckoChildProcessServices tabN) run through the hub and report back (page security state in the toolbar); the **page area stays white**: the parent's WebRender never gets a transaction (Renderer thread: Resume and one UpdateAndRender(0) only, WR worker threads idle), whichever compositor (EGL, SW-WR + GL, plain SW-WR). Fixed on the way: mozglue putenv (RTLD_NEXT), MOZ_APP_DATA, Gecko's SIGSEGV handler (bt step 13), own-package services in other processes (hub peers per child, `HubServices.ownProcess`), component hosts exit with their last service, rebinding a connection, `updateServiceGroup`, process name + skipped `Application.onCreate` in secondary processes (`HostProcessName`), browser role (`HostRoles`), `kill` from a service host is EPERM (bt step 14: a Gecko child SIGKILLed the app), readdir hides `.` and `..` (bt step 16: Classifier::ScanStoreDir recursed into `.` forever), `eglSwapBuffersWithDamageEXT` is ours (libapklayer_egl), profile `user.js` without a GPU process (a Surface sent to another JVM gives `ANativeWindow_fromSurface` = null, then MOZ_CRASH Fallback configurations exhausted). Tools: `tests/vm/fennec-view.sh` (VIEW intent through `APKLAYER_INTENT_FILE`, local web server), `tests/vm/fennec-nav.sh`, `tests/vm/egl-trace.bt` (uprobes on host libEGL and libandroid), Gecko logging through profile prefs (`logging.config.LOG_FILE`, `logging.config.sync`, `logging.<module>`: MOZ_LOG does not reach Gecko; modules that exist: RenderThread, nsRefreshDriver, DocLoader, PresShell, nsHttp, ipc, WebRender, Widget, GeckoViewSupport). Next: why the parent's WebRenderBridge gets no display list (chrome document paint of geckoview.xhtml, nsWindow size/visibility, compositor session), and whether a cross-process Surface can work (Surface parcel through the hub) so the GPU process could be used.
2. **The other broken apps**: Joplin (C++ terminate behind "invalid stdio handle"), Conversations (Conscrypt `FileDescriptor.descriptor`), Unciv / Lemuroid / Rocks 'n' Diamonds (black window; Unciv's GL thread idles), OpenTTD (SDL 1.2 video driver), Puzzles (anti-piracy redirect), Device Info HW, Sensors Multitool (relaunch loop). Ignored by the user: NewPipe (just needs waiting), Vulkan (Pomodorot), Rethink DNS, Open Sudoku (app bug).
3. **Real Android keystore**: keystore2 plus the AOSP software KeyMint and Gatekeeper HALs from `hardware/interfaces` (security/keymint/aidl/default, gatekeeper/aidl/default), as in the plan above. The user built Robolectric and the whole GSI for **x86_64** in OrbStack and says we may take what we need from it and build our own pieces; an EGL build there failed because its assembly is arm-only (the EGL module must stay arm64).
4. **Test the Google apps again** (microG sign-in, Meet accounts, Device Attestation) once the keystore exists.
5. **Proper tests of the preinstalled system apps and services**: `tests/apps/hello-system` (one APK, scenario per service: contacts, calendar, downloads, settings, media, documents, userdict, telephony, notifications, alarms, jobs) and its runner `tests/vm/system-apps.sh [scenario...]` (2026-10-29: 10 of 11 pass). What it found and fixed: other apps' activities and services were missing from an app's PackageManager (hub package info lacked the direct-boot match flags; filters were not registered) and carried uid -1 (AppOps checkPackage, DownloadProvider's userOwnsPackage), `getPackagesForUid`, `ContentResolver.getType` threw instead of answering null (calendar PROVIDER_CHANGED), jobs and alarms never ran (`HostJobs`, RTC alarms in the Robolectric shadow overlay), DownloadManager only recorded requests, the download thread needed a network in the job parameters and the JDK's connections, external files dirs were temp directories, `Application.getProcessName()` was null. **Open: media.** MediaProvider (installed from its APEX by `scripts/install-system-apps.sh`) now starts (Robolectric's fake media provider off for its host, DRM, statsd and xattr shadows, `/data/media/0` link, getCallingUid recursion) and takes inserts, but opening the new item fails ("No item at content://media/external/file/N", "Didn't find any volume for getVolume(/storage/emulated/0)": its volume cache is built before HostStorage adds the primary volume, or from a path that differs from the instance's real one). The hub test groups were rerun after the package-info and provider changes (2026-10-29): providers, broadcasts, pendingintents and activities all pass.
6. Finish the shell work: first-run check of the merged header bar, window size and splash with real apps; Alt-Tab/overview names; light/dark live switching; `rename-bundled-system-libs.py` into `install`; the VM-only files (android-system-stubs symlinks, libc_bio/libdl_bio deployments) into the build scripts.

### Additions from the user (2026-10-29, final)
- **x86_64 readiness**: take Robolectric (android-all jars) and the native libraries from the user's OrbStack tree `~/los24/` (x86_64 GSI and Robolectric builds) and make the x86_64 route as ready as the arm64 one (scripts, staging dir layout, bionic_translation for x86_64, the EGL/graphics HAL modules built for x86_64 from the arm64 sources minus arm assembly), so the user can try it on a real x86 PC later. Nothing can be run on x86 hardware here; verify by building, by file/ELF checks and by the staging scripts.
- **Leftover "check header bar, window size and splash with real apps"**: counted as done.
- **System apps and services**: instead of only scripts, write small test APPS (under tests/apps/) that use the providers and services (contacts, calendar, downloads, settings, media, documents, user dictionary, telephony, notifications, alarms, jobs) and a runner that installs and runs them.
- Order otherwise unchanged: Fennec first, other apps, keystore, Google apps, system-service test apps, x86_64.

### Fennec state (2026-10-29, later): pages draw, page text does not
- Fixed generically: the sandbox clock now follows CLOCK_MONOTONIC (`HostClock`; it ran at 4 s while native code read 107000 s); the launcher no longer sets `layout.frame_rate`. Content, gradients, shadows, images, boxes and the toolbar draw (`tests/vm/www/*.html`, `PAGE=c.html tests/vm/fennec-view.sh`).
- Open: **text fills in page content are missing** (stroked text draws). about:support: Compositing = WebRender on virgl, GLES 3.2. Text appears with `gfx.text.subpixel-position.force-disabled=true`, with `gfx.text.disable-aa=true`, or with software WebRender; desktop Firefox on the same VM and GPU draws it even with subpixel positioning forced. Not the cause: dual-source blending, PBO/batched uploads, optimized shaders, subpixel AA, WR multithreading, GLES version override. Next: why subpixel-positioned glyphs vanish in Android Gecko's GL WebRender (glyph keys / atlas lookup), not a pref.
- Exit criterion: remove the `lib/libxul.so` block of `cli/run.cpp` (profiles.ini, user.js with the GPU process off, prefs.js stripping) by fixing the causes (a Surface sent to another JVM must stay a window; Gecko makes its own profile).

### Keystore (2026-10-29): native services run, the Java side does not reach them yet
- Done: `libapklayer_keymint` (software KeyMint) and `libapklayer_keystore2` start in the stack and register (needed: bt step 18 for bionic's regcomp ABI, `ro.vendor.build.security_patch`, keystore2's argc/argv and database directory, a framework vintf declaration for `android.system.keystore2` and the system `manifest.xml` in the Android root). `tests/vm/keystore.sh` phases pass with the software provider.
- Open: the sandbox's `ServiceManager` is Robolectric's table (`BinderInternal.getContextObject` gives a proxy with no remote), so `AndroidKeyStoreProvider` cannot see keystore2. Needs a JNI hook in the runtime shim: `AServiceManager_getService` + `AIBinder_toJavaBinder` behind the shadowed `ServiceManager` for services the device's service manager declares. Then the caller-uid question (all app JVMs share one Linux uid).
- A stack service that exits ends the whole stack and its tmp directory (`runStackDaemon`): a crashing optional service takes the apps down; decide whether optional services (keymint, keystore2) should be restarted instead.
- Keystore namespaces: keystore2 keys apps by the kernel's calling uid, and every app JVM runs as the one Linux uid, so all apps would share a key namespace. The sender's app uid already travels as the work source of each transaction (`ShadowHostRealBinder.getCallingUid`); keystore2 reads the kernel's, so the mapping belongs in the patched libbinder (`IPCThreadState::getCallingUid` from the work source when the caller is an app JVM).
