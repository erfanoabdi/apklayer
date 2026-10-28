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
- TextureView renders black on the UTM VM (`texture-view.sh`; the quad is drawn, its texture is black; the host has the external-image extensions; not caused by the ES claim). `audio-aidl.sh`: the isolated monitor finds no clean left channel tone.
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
