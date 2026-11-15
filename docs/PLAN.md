# apklayer: open work

Only what is still to do. Only the lead edits this file (agents report PLAN changes in their reports); done items are deleted when their branch is merged.
History: `CHANGELOG.md`, `docs/history/` (full old plans). Large tasks parked: `docs/NOTPLAN.md`. Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak bundles).
- No per-app hacks; the corpus (`tests/corpus/`) measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: device HALs only for vibrator, camera, media; never sensors or GNSS.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).
- Ubuntu Touch: relaunching a running app starts a pointless shell (needs the phone).
- Phone with the deb: `HubStartup.connect` hangs in `pingBinder` after 4-5 NewPipe launches.

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR colours are off and dark (also llvmpipe on the arm server and the NVIDIA tester PC): agent working on it.
- Home scene on NVIDIA: block noise on 3D models. Recheck with the lite-translate setting.

## Graphics
- Host ES1 path untested (needs NVIDIA or Mesa with `-Dgles1=enabled`).
- Vulkan presentation untested on AMD.
- OTR2 on the PC runs ~30 fps at 2448x1374.
- Windows: KDE untested; `window-geometry` and `decoration` groups need uibench and sway.
- Lipstick: the pointer on the navigation-bar strip goes to the app.
- Lomiri: no keyboard inset while the app is turned. Rerun camera-reload on the X23.
- Sailfish keeps the letterbox; its rotation is unchecked.

## Camera
- NDK camera: the first `getCameraIdList` can be empty for ~200 ms because cameraserver publishes `media.camera` before its provider listed cameras. Fix: construct `CameraService` by hand in `aosp/hals/camera/services/Service.cpp`, wait for `getNumberOfCameras() > 0`, then `addService` (needs a ~/los24 build and a re-stage).
- Portrait desktop monitor: the right camera mount degrees per monitor direction are unknown (no way to detect it).
- Parallels' MacBook camera: first frame black. Retest Open Camera, Fossify Camera.
- Halium: preview rotation, `getCallingUid`, rear-camera still, present fences.

## Input / IME
- Non-Latin input (group `input-lang` passes): map the xkb layout to the app's `LocaleList` and IME subtypes; RTL cursor, selection and bidi in an EditText; layout-switch hotkeys (Alt+Shift, Super+Space); `KeyEvent.getUnicodeChar()` for non-Latin keys in apps that read key events.
- text-input-v3 has no show/hide request and no enter-key hint (labels are the keyboard's own).
- OTR2 in a mobile maximized window and FlorisBoard typing.
- Lomiri greeter: only the first uinput tap registers.

## Permissions and Android rules
- HIDL (`docs/HIDL.md`): needs a phone: the stack in a host-HAL instance (camera/media/graphics = device) starts the manager and writes the marker, forwarded names still work, an own service there. Rerun `tests/run hidl host-binder` on the x86 PC with the new libraries.
- Storage: scoped storage hides other packages' `Android/data` and `Android/obb` only for the `/storage/emulated/0`, `/sdcard` and `/data/media/0` spellings; `UNMOUNTABLE` not sent (udisks sends no failed-mount event and an unknown file system has no UUID to key a volume by).
- Call audio: the 6 s new-requester grace and reports from native AAudio/OpenSL streams (the policy's active tracks).

## Startup and memory
- Measure the JVM heap limits on the phones (and an app with a live heap above 500 MB).
- ~1 GB per app JVM on the Volla (UTM: idle PSS hub JVM 480-500 MB, spare JVM 280 MB; JVM flags already tuned); NewPipe ~21 s cold, ~12 s warm.
- Composer GL context ~80 MB: measure on a phone while an app draws.
- A sound server started later is used only from the next stack start.
- Owner to decide: should Lomiri suspend an unfocused apklayer window?

## Native bridge (x86_64)
- Diagnostics: `tools/ace/` (bpftrace on the translator and glibc, `pgr-netcut.sh`); translator notes in `docs/X86_64.md`.
- Telegram has no x86_64 FOSS APK to test.
- Unity: `hpatchz`.

## Sandbox (before it can be the default)
- The NVIDIA tester's box with the INSTALL.md checklist (the broker now runs `nvidia-modprobe` for missing nodes; only the argument mapping is tested).
- UTM VM: restore `~/prebuilts` (arm64 and common, joined) so the shared tree's `inst` symlinks resolve; each tree running the sandbox group needs `sudo sh tools/host-setup/sandbox.sh b/cli/apklayer` once.
- A release-candidate deb with the sandbox on for a day.

## Packaging and releases
- Click: Share/"Open with"; "Choose file…" from the manager; the click update install (`installClick`) is untested off a phone.
- Flatpak: launcher portal dialog.
- Flatpak: the `org.freedesktop.Sdk.Extension.openjdk25` extension and 26.08 runtime are unverified (manifest now names JDK 25).
- amd64 release leg, release guard's first tag run, PackageKit/pkexec install: untested.
- First command after install blocks ~75 s for runtime setup on a phone (10 s on Parallels with the parallel setup; the phone is not re-measured).
- System-app reinstall on runtime change: unchecked on a real upgrade or phone.
- Parallels has an old `apklayerd` 0.3.4 that takes over hub starts. UTM's `~/prebuilts/common` is empty.

## Manager
- Apps installed before 2026-11-04 need a reinstall for permissions.
- `manager/tests/ui.sh` permission step is written but never run. Hardware page: no live GNSS/camera test.

## Hardware
- Vibrator: `View.performHapticFeedback(int)` is swallowed by Robolectric's `ShadowView` (haptic constants only reach the HAL for the hidden `Vibrator.performHapticFeedback`); ramp/PWLE segments of app effects are skipped in the hub's vibrator_manager.
- iio-sensor-proxy gives only derived values: no accelerometer or compass type. On Parallels a source-tree CLI needs `--no-daemon` plus `APKLAYER_NATIVE`/`APKLAYER_BT`/`APKLAYER_BINDER=userspace` (the old 0.3.4 daemon takes over).
- USB: one chooser for several apps; sandbox node access.
- Bluetooth: pairing paths, Sailfish agent, truly simultaneous connects to one server socket (the mock keeps one client profile per UUID).
- Push: a real WorkManager app, constraints, resume after suspend, microG 0.3.x.

## Tests and tooling
- A second sandboxed `services` run on the same instance data fails in the isolated service (`Settings.Secure`: Ssaid settings not accessible); fresh data passes.
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.

## Needs hardware or a person
- NVIDIA/AMD: Vulkan presentation, host ES1.
- Halium: vendor HALs, `media = device`, NewPipe network, the new native changes on the phones.
- Sailfish: geoclue fix, Lipstick switcher with two apps, a finger on the host dialog.
- Ubuntu Touch: manager on screen, navigation bar under the panel, drawer tap, shell window, maximize, turning by hand.
- Bluetooth PIN dialog on a legacy device. A real USB serial adapter.
- Push: a real Telegram/WhatsApp message with microG; the unit starting the stack at login.
- By eye: window raise, text magnifier, portal file chooser, pkexec, Google sign-in, playback, audio, colours, IME look.
- Fullscreen: NewPipe player on GNOME and the X23; default insets of new windows; no configuration-changed broadcast.
- Apps: a Godot 4 game, JW Library thumbnails, a NewPipe download.

## Parked (owner's decision)
- NFC, biometrics, game controllers, ModemManager telephony, screen capture portal.
- Bluetooth advertising/peripheral, A2DP/HFP, LE audio, OPP; USB accessory, tethering.
- Several phone instances as a product; a JRE for the Ubuntu Touch deb.
