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
- Audio HAL: the first output stream fails on the UTM VM (PipeWire 1.6.2, `Failed to initialize the driver: -5`), so `native-player.sh` fails.
- `hub-services.sh auto`: "oneway native callback". TextureView renders black on the UTM VM. WebView test needs the provider set up on the VM (`scripts/webview-setup.sh`, sudo).
- **Intermittent app hang**: the app's main thread blocks in `HardwareRenderer.nSetStopped` (thread dump 2026-10-28) when its window goes inactive/stopped, e.g. when another app's window appears. It makes the multi-app hub tests (`pendingintents`, `activities`, `broadcasts`) flaky; each phase passes alone. Same family as the old resize-with-dialog hang. Needs a root-cause fix (render thread waiting on a buffer that the sink does not release for an unmapped window).
- Vulkan presentation: opt-in, Venus aborts on imported virgl dmabufs on the UTM host; needs a real-GPU host or a Venus-allocated swapchain bridge.

**The user asked to revisit**
- The user's other test apps in `~/TestApps` (some fail: converter, classpath conflicts such as NewPipe's R8-merged `kotlin.math.MathKt`): record app/status/cause and fix by cause.
- Desktop notifications: popup policy is done; per-app mute, closing a popup when the app cancels, and notification actions are open.
- OTR2: input and audio in the game are unchecked; `AChoreographer: No JVM provided` (harmless so far). P2: 2x HiDPI output and a real IME were never exercised.

**Missing features**
- **Permissions and system features must match real Android**: read the build's `system/etc/permissions/*.xml` (features, privapp-permissions), `sysconfig` and default-permissions into `PackageManager` (features, which packages hold signature/privileged permissions, default grants) instead of ad hoc grants; replaces `-Dapklayer.grantall`. Comes before system apps, hardware and release work.
- DocumentsUI (tree APK `system/priv-app/DocumentsUI`, installs as "Files"): opens its window with `-Dapklayer.grantall=true` (grants the manifest's permissions, needed for HIDE_OVERLAY_WINDOWS), then crashes in `UserManager.getUserProperties()` (null `UserProperties`: a user-properties shadow is missing).
- System apps and providers on every instance (Settings, Contacts, Calendar, Download, Telephony, UserDictionary, DocumentsUI, MediaProvider) hosted in an instance system JVM.
- Hub gaps: several bindings per connection, filter-specific rebind, signature/runtime permission grants.
- **Keystore first** (`android.security.keystore`, used by many apps). Then hardware domains, last and one at a time (`aosp/hals/README.md`): camera, GNSS (GeoClue), battery/power (UPower, logind), connectivity (NetworkManager, ModemManager; honest "no SIM"), USB (udev), portals (files, screen capture). **Not this round** (user, 2026-10-28): Bluetooth (BlueZ bridge), NFC, biometrics, sensors, vibrator/haptics and game controllers. Decided: backlight and thermal are the host's; accounts the Android way.
- microG; per-app/instance sandbox (bwrap is blocked by Ubuntu's userns restriction); other codecs, higher-bit-depth VP9, hardware decoder dmabuf import.
- Compatibility corpus (P3), the proposed next task in `STATUS.md`.

**Before a public release**
- COMMERCIAL.md contact and terms, a CLA and ownership of the agent-written commits, NOTICE/legal files for everything shipped, the Android SDK `android.jar` terms if bundled.
- x86_64 build of the Android libraries (needs approval for a second out dir), Flatpak, replacing the ninja-replay build scripts, per-run test temp dirs.
- Files over 600 lines: `ShadowWindowManagerGlobal` (patched upstream overlay), `Egl.cpp` (needs a build-graph change).
