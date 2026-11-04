# apklayer: plan

What is decided, what is done, what comes next and what is open. Dated history: `CHANGELOG.md`; earlier plans: `docs/history/` (the full plan
before this rewrite is `docs/history/PLAN-2026-11-04-full.md`). If a decision changes, change it here in the same commit as the code.

## Decisions
| Topic | Decision |
|---|---|
| Framework | Unmodified AOSP `android-all` (the LineageOS tree's: Android 17, SDK 37) on HotSpot with Robolectric shadows. No system_server, no zygote. Upstream code (android-all, Robolectric, dex2jvm, bionic_translation, AOSP) changes only through thin patch files (`patches/`, `docs/PATCHES.md`). |
| Native side | Android's own arm64 libraries from a LineageOS 24 `gsi_arm64 userdebug` build under bionic_translation on binderfs: servicemanager, SurfaceFlinger, HWUI, media, audio, keystore2. apklayer's HALs (`aosp/hals`, `bridge/hals`) back them with GBM/EGL/Vulkan, PipeWire, GStreamer. No CPU-only route, no stand-ins. |
| Apps | dex2jvm converts DEX; one JVM per Android process, named as Android names it; per-app class loaders and linker namespaces; windows go to one per-session Wayland shell, one virtual display per app. |
| Cross-app | The instance hub (real Binder between app JVMs) implements services, broadcasts, providers, activities, notifications, pending intents, accounts, package installs, caller identity. System services stay Robolectric shadows except where a process boundary exists; system_server services are not ported into the hub (assessment in CHANGELOG 2026-11-03). |
| System apps | The tree's real providers and apps (SettingsProvider, MediaProvider, Contacts, Calendar, Downloads, DocumentsUI, WebView) installed as apklayer apps. |
| Instances | Each instance has its own binder devices, /data, /storage, app data, properties, shell session and hub (`APKLAYER_INSTANCE`). Tests run one instance per group, several at a time. |
| Readiness | Instance properties (`apklayer.stack.ready`, `apklayer.hub.ready`) waited on with `aprop wait`; no polling, no timers. |
| CLI and build | C++ CLI in `cli/`; Meson for native code; `tools/build`, `tools/stage`; pinned Java deps in `deps/deps.lock`, staged tree files in `deps/android-tree-files.lock`. |
| UI | `apklayer-manager` (`manager/`, Slint C++, Material 3) over the CLI's managing commands; phone and desktop layouts. |
| License | AGPL-3.0-or-later plus a paid commercial license (`COMMERCIAL.md`, audit in `docs/THIRD_PARTY.md`). |
| Hosts | arm64 Linux today; x86_64 staged (`docs/X86_64.md`), never run on hardware. Distribution: Flatpak first. |
| Verification | Every feature has a scripted check with real input and a per-instance SurfaceFlinger screenshot (`tests/run <group>`, `tests/groups.toml`); run only the groups for the code you changed. Compatibility is measured by the corpus (`tests/corpus/`) with current app versions. CTS strictly last. |
| Builds in `~/los24` | Incremental `gsi_arm64 userdebug` only, never clean `out/` (`CLAUDE.md`, `docs/AOSP-BUILDS.md`). |

## Phases (code comments cite them: "PLAN P7.1")
| Phase | State |
|---|---|
| P1 CLI, install cache, shell protocol | done |
| P2 windows and input (resize, wheel, touch, clipboard, IME, lifecycle, fractional scale) | done |
| P3 compatibility corpus (`tests/corpus/`) | running; rounds with current F-Droid apps |
| P4 native `.so` in apps (native loader, linker namespaces) | done |
| P5 platform services (WebView, media, audio, keystore, accounts, installer, location, network, portals) | done for these; camera, sensors, Bluetooth parked |
| P6 apps talking to each other (hub) | done |
| P7 GPU route (P7.0 btload, P7.1 our SurfaceFlinger/HWC/gralloc/EGL on dmabuf) | GL done; Vulkan presentation needs a real GPU |
| P8 ship (Flatpak, x86_64) | x86_64 staged, untested; Flatpak not started |
| P9 instances (own binder, data, storage, shell, hub) | done for tests and side-by-side apps; whole phones as a product deferred |
| P10 CTS | strictly last |

## Done (summary; details in CHANGELOG.md)
- **Runtime**: per-app class loaders and linker namespaces; processes carry their Android process name and run services in their declared process; Build values from the instance's property store before any app class (release identity: user/release-keys); one platform certificate per instance; a spare component host per instance (host start 1.2-1.3 s -> 0.46-0.6 s); hosts finish due work and their loopers' running work before they exit (settings survive `stop`).
- **Hub**: services, broadcasts, providers, activities, notifications, pending intents, accounts (AccountManager through the hub), package installer (sessions, preapproval, legacy intents, splits/bundles, update checks), caller identity checked against the hub's registry, system settings written at boot, audio permission feed and stream volumes for every audioserver, permission_checker for the native services.
- **System apps**: real SettingsProvider, MediaProvider, Contacts, Calendar, Downloads, UserDictionary, DocumentsUI, WebView; keystore2 with the software KeyMint (Android's AndroidKeyStore provider).
- **Graphics and windows**: our SurfaceFlinger route with dmabuf to Wayland at real vsync; EGL with Android's per-context hook tables; GLES1 through gfxstream's translator; hardware bitmaps, TextureView, SurfaceView; windows follow wp_fractional_scale_v1/wp_viewporter, first size fitted to the compositor's bounds; Adwaita-style header bar; splash until the first frame. Vulkan implemented (presentation opt-in).
- **Media and audio**: Codec2 software codecs (AVC, VP8/9, AAC, MP3, Opus...), MediaCodec into Surfaces, MediaPlayer, AudioTrack/AudioRecord/MediaRecorder through the real AudioFlinger/AudioPolicy to PipeWire; recording works.
- **Tooling**: `tests/run` (each group its own instance, UTM or Parallels, rebuilds the runtime when sources change), per-instance screenshots, scripted input into one instance's windows (`instance.inject`), corpus runner per instance, all upstream edits as patch files (bionic_translation 0001-0031, robolectric 0001-0015, aosp per project).
- **Manager**: apps (install, open, stop, clear data, uninstall, permissions with grant/revoke), instances (create, remove), properties, device info; CLI errors shown.
- **Compatibility** (tests/corpus/results.md, 2026-11-04): over 40 apps draw, among them current F-Droid versions of Feeder, Tusky, Breezy Weather, Auxio, KeePassDX, Tasks.org, Thunderbird, mpv, Organic Maps, OsmAnd, LocalSend and FluffyChat (Flutter), Standard Notes (React Native), Element, VLC, LibreTube, Material Files, Nextcloud, Shelter, GPSTest, Unciv; also Telegram, NewPipe 0.29 (lists and plays), Fennec (pages, audio), OpenTTD (GLES1), Conversations, AnkiDroid, Joplin, Lemuroid, microG.
- Latest full suite (UTM, 2026-11-04): 40 of 42 groups; vulkan-present fails on both VMs (no Vulkan presentation: needs a real GPU); native-surface's failure (kernel keys beside other instances) is fixed.

## Next phase (in order)
1. **Fennec video** (in progress): YouTube audio plays, the video stays at 0:00 with a spinner although the AVC decoder renders 24 frames/s into the GPU process's SurfaceTexture. Done when fennec-media checks that video time advances and frames reach the GPU process, plus a test app decoding into a SurfaceTexture owned by another process.
2. **Media leftovers**: audio-aidl's left channel; fennec-media on a fresh profile (Fenix onboarding); m.youtube.com/watch cookie-consent dialog in the test; the HAL output ring (~100-140 ms behind the mixer against a reported 45 ms) measured and sized; native-audio without audio mode stalls after Back.
3. **Startup**: Robolectric's setUpApplicationState (1.3-1.5 s), the keystore2 probe (0.6-0.9 s), fonts/ICU copied out of jars on every start (0.4-0.7 s: port Robolectric's keyed shared cache), classes preloaded in the spare host (~0.13 s), a second spare for provider bursts, cold starts waiting ~2 s for the hub. Measured with `tests/vm/startup.sh`.
4. **Correctness gaps**: native mkdir/rename under /storage not mapped (the bionic path hook covers open/stat only); two instances in one data home share app data dirs; an unbind followed by an immediate rebind can get a host that is exiting; the hub accepts apps while it shuts down; the getAuthToken grant dialog (refused today); `ro.build.version.incremental` from the tree (a dev build's `eng.<user>` shows in the fingerprint).
5. **Installer leftovers**: tests for .xapk/.apkm and for dropping splits of another ABI; an installer updating itself; sessions and preapprovals across a hub restart; SessionCallback.onFinished; package broadcasts to manifest receivers (apps targeting below 26); unsigned updates accepted (shortcut).
6. **Cleanup**: the `shortcut:` markers (22: `grep -rn "shortcut:"`) fixed or confirmed as limits; files over ~600 lines (`ShadowWindowManagerGlobal`, `Egl.cpp`); dead code and flags.
7. **Corpus rounds**: more current F-Droid apps (tests/corpus/fdroid.py and the F-Droid index), both VMs, generic fixes only.

## Open by area (not in the next phase yet)
- **Graphics**: a plain RGB8 eglChooseConfig puts a 10-bit XR30 config first and the present path cannot read XR30/AB4H/XB4H back; the ES1 translator reports "Android Emulator OpenGL ES Translator" as renderer; ES1 on x86_64 needs a reserved TLS slot; the host-ES1 path is untested; OpenTTD's SDL logs `Invalid texture` endlessly after a focus loss when desktop audio is unreachable.
- **Windows**: xdg-decoration on non-GNOME desktops; a real-session scale switch check; landscape windows are not grown to the screen.
- **Storage**: which volumes ordinary apps (not DocumentsUI) reach through the file APIs; files carry no SELinux label (getfilecon ENODATA).
- **Settings**: a hanging GeoClue call can delay an app's first provider request up to 10 s; the IME choice applies at the next instance start and no IME binds to input yet; DEVICE_PROVISIONED/USER_SETUP_COMPLETE are 1 everywhere.
- **Input**: Back in a WebView text selection finishes the activity (Chromium's handling, not checked against a device); the IME target ignores FLAG_ALT_FOCUSABLE_IM windows; input-inject's long press uses app-clock ticks (a stalled app turns it into a tap).
- **Relaunch**: an intent queued for an app that has a run record but has not registered yet becomes its first launch (its own intent file is then ignored): no test.
- **Tests**: one tests/run per VM tree at a time (a lock refuses a second: the push and runtime rebuild replace files under running groups); the `view` group fails in a fresh private data dir (null cursor in hello.files).
- **Manager**: apps installed before 2026-11-04 need a reinstall before their permissions show; a grant reaches the app's own check at its next start; signature grants approximated from the build's allow-lists; ui.sh has no permission step; binder devices of a removed instance stay until reboot.
- **Notifications**: per-app mute, closing a popup when the app cancels, notification actions.
- **Keystore**: attestation chain checks, KeyInfo, auth-bound keys with a software Gatekeeper; all app JVMs share one Linux uid (keystore2 namespaces follow the carried uid).
- **dex2jvm**: Open Sudoku's `CREATE TABLE` repeats a column (suspected register reuse, unconfirmed); a per-class conversion failure blocks the install; JdkScan leads (`DatagramSocket.getFileDescriptor$`, SharedMemory's `DirectByteBuffer`, `Cipher/Mac/Signature.getCurrentSpi`, `sun.misc.Cleaner`).
- **Apps**: Go runtime apps (WireGuard, Rethink DNS: cgo TLS under the bionic TLS emulation); Godot 4 needs Vulkan presentation; Meet's Google sign-in through microG needs a human check.

## Needs real hardware or a person
- **x86_64 PC**: the staged x86_64 route (`tools/stage x86_64`, `docs/X86_64.md`) has never run.
- **A real GPU**: Vulkan presentation (vulkan-present, Godot); Fennec page text (virgl drops ints set in shader branches on UTM, `tests/vm/gles-int-branch.c`; report to virglrenderer).
- **A person**: the window raise of a relaunched app (the shell asks with the launcher's XDG_ACTIVATION_TOKEN; mutter refuses the shell's own token, no test has a launcher token); what the text magnifier draws; the portal file chooser and pkexec prompt in the manager; the Google sign-in in Meet/microG; playback quality by ear and eye.

## Before a public release
Flatpak; per-app sandbox with kernel-attested uids (a privileged launcher; bwrap is blocked by Ubuntu's userns restriction); COMMERCIAL.md terms and contact, a CLA and ownership of agent-written commits, NOTICE files for everything shipped.

## Parked (owner's decision)
- Camera over PipeWire: last priority.
- Not now: Bluetooth (BlueZ), NFC, biometrics, sensors, vibrator/haptics, game controllers, ModemManager ("no SIM" is the honest answer), screen capture portal. Backlight and thermal are the host's.
- Several phone instances as a product (P9) and SystemUI: yardsticks, not goals. CTS strictly last.

## Design notes that still hold
- **Storage volumes** (user's design, 2026-10-28): each instance has its own `/storage` (`$XDG_DATA_HOME/apklayer/<n>/storage`); volumes Internal storage (`<storage>/emulated/0`, `/sdcard`), Home (`linux-home`) and Linux root (`linux-root`).
- **Window decoration**: the shell draws an Adwaita-style header bar itself (`bridge/shell/decor_draw.c`, cairo): 46 logical px, Back on the left, minimize/maximize/close on the right; light/dark from gsettings when a window opens.
- **Splash**: until an app presents its first frame, `bridge/shell/splash.c` shows its icon on a colour taken from it.
