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
- Per-app class loaders (2026-11-03): an app class placed in a framework package to reach package-private members gets IllegalAccessError now (another runtime package); none of the tested apps does it. Meet draws its welcome screen ("Failed to load accounts" until the microG sign-in works); its earlier exits were shells killed by other instances' stop (fixed).


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
- **Joplin**: with the bundled OpenSSL libraries renamed (`the removed rename script (replaced by the per-app linker namespace, bt patch 0026)`, not yet run by `install`) the JS bundle runs, then the process aborts: `libc++abi: Fatal error: glibc detected an invalid stdio handle` (a C++ terminate message written through a bionic FILE).
- **Conversations**: aborts at start; `NativeCrypto: could not find field descriptor` (a Conscrypt JNI lookup of `FileDescriptor.descriptor`, which is `fd` in the JDK).

## Adwaita window decoration (decision)
- libadwaita and GTK4 are not on the VM and a GTK window cannot take our dmabuf subsurface path without reworking it, so option (a): the shell draws the header bar itself.
- bridge/shell/decor_draw.c draws it with cairo (new dependency libcairo2-dev): 46 px bar, Adwaita colours, round buttons (Back left; minimize, maximize, close right), bold centred title, rounded top corners.
- Light/dark is read from gsettings color-scheme when a window is created (no live switch yet; the portal Settings would give that).
- Hit testing, drag, double-click maximize and content-relative input are unchanged (DECOR_H 32 -> 46).
- Title is the window title, or the package name when the title is the internal apklayer:pid:instance:pkg one.
## Splash screen (shell)
Until an app presents its first frame, `bridge/shell/splash.c` fills the window with the app logo (installed `icon.png`) on a colour derived from it. Tested with `tests/vm/splash.py` (stand-in client, private XDG_RUNTIME_DIR; a second full Android stack cannot run next to another because of the single /dev/binder). Not done: fade-in/out, re-draw on resize before the first frame, a real-app run (needs the default stack free).


## Task order (2026-10-29, set by the user before going offline)
1. **Firefox (Fennec) must work, whatever it takes.** State (2026-10-29): starts, shows onboarding and home page, loads pages over the network (a local http server sees the GET), content processes (GeckoChildProcessServices tabN) run through the hub and report back (page security state in the toolbar); the **page area stays white**: the parent's WebRender never gets a transaction (Renderer thread: Resume and one UpdateAndRender(0) only, WR worker threads idle), whichever compositor (EGL, SW-WR + GL, plain SW-WR). Fixed on the way: mozglue putenv (RTLD_NEXT), MOZ_APP_DATA, Gecko's SIGSEGV handler (bt step 13), own-package services in other processes (hub peers per child, `HubServices.ownProcess`), component hosts exit with their last service, rebinding a connection, `updateServiceGroup`, process name + skipped `Application.onCreate` in secondary processes (`HostProcessName`), browser role (`HostRoles`), `kill` from a service host is EPERM (bt step 14: a Gecko child SIGKILLed the app), readdir hides `.` and `..` (bt step 16: Classifier::ScanStoreDir recursed into `.` forever), `eglSwapBuffersWithDamageEXT` is ours (libapklayer_egl), profile `user.js` without a GPU process (a Surface sent to another JVM gives `ANativeWindow_fromSurface` = null, then MOZ_CRASH Fallback configurations exhausted). Tools: a Fennec VIEW probe (removed) (VIEW intent through `APKLAYER_INTENT_FILE`, local web server), a Fennec navigation probe (removed), `tools/debug/egl-trace.bt` (uprobes on host libEGL and libandroid), Gecko logging through profile prefs (`logging.config.LOG_FILE`, `logging.config.sync`, `logging.<module>`: MOZ_LOG does not reach Gecko; modules that exist: RenderThread, nsRefreshDriver, DocLoader, PresShell, nsHttp, ipc, WebRender, Widget, GeckoViewSupport). Next: why the parent's WebRenderBridge gets no display list (chrome document paint of geckoview.xhtml, nsWindow size/visibility, compositor session), and whether a cross-process Surface can work (Surface parcel through the hub) so the GPU process could be used.
2. **The other broken apps**: Joplin (C++ terminate behind "invalid stdio handle"), Conversations (Conscrypt `FileDescriptor.descriptor`), Unciv / Lemuroid / Rocks 'n' Diamonds (black window; Unciv's GL thread idles), OpenTTD (SDL 1.2 video driver), Puzzles (anti-piracy redirect), Device Info HW, Sensors Multitool (relaunch loop). Ignored by the user: NewPipe (just needs waiting), Vulkan (Pomodorot), Rethink DNS, Open Sudoku (app bug).
3. **Real Android keystore**: keystore2 plus the AOSP software KeyMint and Gatekeeper HALs from `hardware/interfaces` (security/keymint/aidl/default, gatekeeper/aidl/default), as in the plan above. The user built Robolectric and the whole GSI for **x86_64** in OrbStack and says we may take what we need from it and build our own pieces; an EGL build there failed because its assembly is arm-only (the EGL module must stay arm64).
4. **Test the Google apps again** (microG sign-in, Meet accounts, Device Attestation) once the keystore exists.
5. **Proper tests of the preinstalled system apps and services**: `tests/apps/hello-system` (one APK, scenario per service: contacts, calendar, downloads, settings, media, documents, userdict, telephony, notifications, alarms, jobs) and its runner `tests/vm/system-apps.sh [scenario...]` (2026-10-29: 10 of 11 pass). What it found and fixed: other apps' activities and services were missing from an app's PackageManager (hub package info lacked the direct-boot match flags; filters were not registered) and carried uid -1 (AppOps checkPackage, DownloadProvider's userOwnsPackage), `getPackagesForUid`, `ContentResolver.getType` threw instead of answering null (calendar PROVIDER_CHANGED), jobs and alarms never ran (`HostJobs`, RTC alarms in the Robolectric shadow overlay), DownloadManager only recorded requests, the download thread needed a network in the job parameters and the JDK's connections, external files dirs were temp directories, `Application.getProcessName()` was null. **Open: media.** MediaProvider (installed from its APEX by `tools/stage sysapps`) now starts (Robolectric's fake media provider off for its host, DRM, statsd and xattr shadows, `/data/media/0` link, getCallingUid recursion) and takes inserts, but opening the new item fails ("No item at content://media/external/file/N", "Didn't find any volume for getVolume(/storage/emulated/0)": its volume cache is built before HostStorage adds the primary volume, or from a path that differs from the instance's real one). The hub test groups were rerun after the package-info and provider changes (2026-10-29): providers, broadcasts, pendingintents and activities all pass.
6. Finish the shell work: first-run check of the merged header bar, window size and splash with real apps; Alt-Tab/overview names; light/dark live switching; `rename-bundled-system-libs.py` into `install`; the VM-only files (android-system-stubs symlinks, libc_bio/libdl_bio deployments) into the build scripts.

### Additions from the user (2026-10-29, final)
- **x86_64 readiness**: take Robolectric (android-all jars) and the native libraries from the user's OrbStack tree `~/los24/` (x86_64 GSI and Robolectric builds) and make the x86_64 route as ready as the arm64 one (scripts, staging dir layout, bionic_translation for x86_64, the EGL/graphics HAL modules built for x86_64 from the arm64 sources minus arm assembly), so the user can try it on a real x86 PC later. Nothing can be run on x86 hardware here; verify by building, by file/ELF checks and by the staging scripts.
- **Leftover "check header bar, window size and splash with real apps"**: counted as done.
- **System apps and services**: instead of only scripts, write small test APPS (under tests/apps/) that use the providers and services (contacts, calendar, downloads, settings, media, documents, user dictionary, telephony, notifications, alarms, jobs) and a runner that installs and runs them.
- Order otherwise unchanged: Fennec first, other apps, keystore, Google apps, system-service test apps, x86_64.

### Fennec state (2026-10-29, later): pages draw, page text does not
- Fixed generically: the sandbox clock now follows CLOCK_MONOTONIC (`HostClock`; it ran at 4 s while native code read 107000 s); the launcher no longer sets `layout.frame_rate`. Content, gradients, shadows, images, boxes and the toolbar draw (probe pages (removed), `PAGE=c.html a Fennec VIEW probe (removed)`).
- Open: **text fills in page content are missing** (stroked text draws). about:support: Compositing = WebRender on virgl, GLES 3.2. Text appears with `gfx.text.subpixel-position.force-disabled=true`, with `gfx.text.disable-aa=true`, or with software WebRender; desktop Firefox on the same VM and GPU draws it even with subpixel positioning forced. Not the cause: dual-source blending, PBO/batched uploads, optimized shaders, subpixel AA, WR multithreading, GLES version override. Next: why subpixel-positioned glyphs vanish in Android Gecko's GL WebRender (glyph keys / atlas lookup), not a pref.
- Exit criterion: remove the `lib/libxul.so` block of `cli/run.cpp` (profiles.ini, user.js with the GPU process off, prefs.js stripping) by fixing the causes (a Surface sent to another JVM must stay a window; Gecko makes its own profile).

### Keystore (2026-10-29): native services run, the Java side does not reach them yet
- Done: `libapklayer_keymint` (software KeyMint) and `libapklayer_keystore2` start in the stack and register (needed: bt step 18 for bionic's regcomp ABI, `ro.vendor.build.security_patch`, keystore2's argc/argv and database directory, a framework vintf declaration for `android.system.keystore2` and the system `manifest.xml` in the Android root). `tests/vm/keystore.sh` phases pass with the software provider.
- Open: the sandbox's `ServiceManager` is Robolectric's table (`BinderInternal.getContextObject` gives a proxy with no remote), so `AndroidKeyStoreProvider` cannot see keystore2. Needs a JNI hook in the runtime shim: `AServiceManager_getService` + `AIBinder_toJavaBinder` behind the shadowed `ServiceManager` for services the device's service manager declares. Then the caller-uid question (all app JVMs share one Linux uid).
- A stack service that exits ends the whole stack and its tmp directory (`runStackDaemon`): a crashing optional service takes the apps down; decide whether optional services (keymint, keystore2) should be restarted instead.
- Keystore namespaces: keystore2 keys apps by the kernel's calling uid, and every app JVM runs as the one Linux uid, so all apps would share a key namespace. The sender's app uid already travels as the work source of each transaction (`ShadowHostRealBinder.getCallingUid`); keystore2 reads the kernel's, so the mapping belongs in the patched libbinder (`IPCThreadState::getCallingUid` from the work source when the caller is an app JVM).

- Keystore (later, 2026-10-29): DONE for the Java side. Apps get Android's own AndroidKeyStore provider over keystore2 and the software KeyMint (`HostKeyStore`, `RealBinder.await`, patched libbinder without kernel security contexts, bt steps 18-19, rebuilt btload/preload); `tests/vm/keystore.sh` phases pass with `AndroidKeyStore: keystore2`. Left: the shared-uid key namespace (above); the hub launch retries when the previous hub is going down (`HubStartup`).

- Document pickers (2026-10-29): ACTION_OPEN_DOCUMENT/_TREE/GET_CONTENT/CREATE_DOCUMENT go to the installed DocumentsUI through the hub (`AppHost`), the desktop's file chooser portal answers only when no installed app does; the launched activity gets the requester as its calling package (`HubActivityClient.CALLER`). Logseq: choose a folder -> DocumentsUI -> Allow -> its journal opens.

### Other apps re-checked (2026-10-29, after the clock/vsync fix)
- Fixed by it: Joplin (notes list), Lemuroid (splash), Rocks 'n' Diamonds (menu, SDL), microG add account (sign-in page; the crash was my provider-first detection, fixed).
- Unciv: libgdx in RENDERMODE_WHEN_DIRTY: 5 frames (clear only), then the GLThread waits forever (`mRequestRender=false`, surface valid, 804x452); the log stops after "Loaded 2 rulesets". Nothing asks for a redraw: find what Unciv's loading waits on (diag: `-Dapklayer.geckodiag=true` now prints the GLThread's fields).
- OpenTTD: pelya's libSDL, `SDL: Couldn't allocate a window to draw on` (Rocks 'n' Diamonds uses the same SDL and works): check the requested video mode.
- Sensors Multitool: opens on an empty "Battery" screen (no list); relaunch loop not reproduced.
- Fennec: page text still missing (above).

## Systematic compatibility (2026-11-03)
- Fix order comes from `tests/corpus/fakes.md` (fakes ranked by how many apps hit them), not from the app in front of us. Robolectric-shadowed managers (window, display) appear in every app: read them apart from binder-method hits.
- JdkScan leads still open, framework code that can run: `DatagramSocket.getFileDescriptor$` (VpnService.protect), `DirectByteBuffer.<init>(IJFileDescriptor,Runnable,Z)` (SharedMemory), `Cipher/Mac/Signature/KeyAgreement.getCurrentSpi` (keystore), `sun.misc.Cleaner` checkcasts. Out of reach on HotSpot: ART internals (`Thread.nativePeer`, `Method.dexMethodIndex`, `Class.accessFlags`).
- system_server services standalone in the hub: not worth it (AlarmManagerService assessment in CHANGELOG 2026-11-03); keep the hub's own implementations, bounded by CTS-derived scenarios.
- Open from this round: the late-install path (install while a component host runs) has no test; apps that read `Build` in static initialisers before `Application.onCreate` still see Robolectric's values; Build TAGS/TYPE are the build's `test-keys`/`userdebug` (root detectors flag them); a per-class dex2jvm failure now blocks the install.

## Startup (2026-11-03, `tests/vm/startup.sh`)
- Cold start: the hub JVM now boots alongside the app (UiBench cold 5.9 s -> 3.7 s, Telegram 6.9 s -> 4.5 s); warm starts unchanged (UiBench ~3.4 s, Telegram ~4.2 s). Stack and shell already stayed up between apps.
- Warm breakdown (UiBench): JVM to main 45 ms, sandbox 150 ms, Robolectric setUpApplicationState 1.3-1.5 s, keystore2 probe + lazy Application 0.6 s, runtime and hub attach 0.2 s, first activity 0.5-0.65 s, first frame 0.16 s.
- Next: (1) the overlay DefaultNativeRuntimeLoader copies fonts/ICU out of jars on every start (~0.4-0.7 s): Robolectric's `sharedAssetDirectory` has a keyed shared cache to port; its `maybeCopyFonts` also overrides the CLI's `-Drobolectric.nativeruntime.fontdir`. (2) the keystore2 probe (0.6-0.9 s). (3) CDS: 0.5 s possible, but JDK 21 dynamic archives of sandbox-loader classes crashed Telegram; a static archive of built-in-loader classes only is untested. Risk: a hub slower than HubStartup's 5 s wait means relaunches.
- Parallel instances: every stop used to `pkill -x apklayer-shell` (all sessions); now only this session's shell (SO_PEERCRED on its socket).

## After the 2026-11-03 round (graphics, system providers)
- Done: inactive windows stay resumed (they only lose window focus, as Android multi-window); the vdisplay installs its configure callback after the display exists (first window size takes); shell readiness is a flock plus a ready pipe (no `shell unavailable` race); hardware bitmaps work (the shadows that turned them off are gone; tests/apps/hello-hwbitmap); TextureView draws (black no longer reproduces; earlier cause not found). The real MediaProvider and SettingsProvider are the default; /data and /storage are per instance in the path layer; the hub writes boot settings as the system.
- Open: Unciv draws nothing into its own GL surface (app side: its frames are 0,0,0,0); Lemuroid's main thread blocks on an androidx.work lock; TIC-80 SIGSEGV in `lazy_find_class` during JNI_OnLoad; SurfaceFlinger SIGSEGV once in `scheduler::OneShotTimer::loop()`; a shell stuck before listen() now blocks `run`.
- Open (settings): android_id changes between launches (SettingsProvider saves 0.2-2 s later and `stop` kills its host first: component hosts must finish due handler work); the hub as system settings writer for LOCATION_MODE and ENABLED_INPUT_METHODS (GeoClue location regressed: network provider off; no Android IME can be enabled); two instances in one data home share app data dirs; the bionic path hook maps only open/stat (native mkdir/rename under /storage not mapped).
