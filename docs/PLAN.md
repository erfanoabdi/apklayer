# apklayer: open work

Only what is still to do. Only the lead edits this file (agents report PLAN changes in their reports); done items are deleted when their branch is merged.
History: `CHANGELOG.md`, `docs/history/` (full old plans). Large tasks parked: `docs/NOTPLAN.md`. Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak bundles).
- No per-app hacks; the corpus (`tests/corpus/`) measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: sensors come through sensorfw, location through GeoClue (Sailfish) or Lomiri's location service; vibrator, camera and media use the device HALs.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- GApps sign-in: after local account creation on GMS 26.44 / Android 17, setup reports “There was a problem communicating with Google servers”; screenshot-time logs include a failed DroidGuard response fetch. Cronet Dynamite also failed to load `ImplVersion`; path-based DEX loader support is patched and covered by a regression check, but end-to-end sign-in is still unverified. Owner reports Waydroid Android 13 works without Integrity (GMS version unknown).
- NewPipe 0.x: Trending is blank (its extractor cannot parse today's YouTube page).
- Ubuntu Touch: relaunching a running app starts a pointless shell (needs the phone).
- Phone with the deb: `HubStartup.connect` hangs in `pingBinder` after 4-5 NewPipe launches.

## PGR (Punishing: Gray Raven) and the anti-cheat
- PGR colours are off and dark in the 3D lobby (also llvmpipe on the arm server and the NVIDIA tester PC; 2D UI is right). Ruled out: window alpha/opaque region, an sRGB window surface (Mesa gbm answers BAD_MATCH). Untested: ASTC/ETC2 decode, sRGB/FP16/R11G11B10 render targets through the wrapper, a reference lobby frame outside the tutorial dim. Agent working on it.
- Home scene on NVIDIA: block noise on 3D models. Recheck with `apklayer prop set berberis.mode.com.kurogame.gplay.punishing.grayraven.en lite-translate-or-interpret` (and unset for the reference); the ASTC fallback in `cli/env.cpp` is a separate lever.

## Graphics
- Vulkan presentation untested on AMD.
- KDE, needs a human eye: edge/corner drag resize, Alt+F8, snap by dragging, the look of KDE's title bar around the app, a real second monitor, fractional scale on the real display, `fullscreen-key` (F11) on a private session.
- `fit_size` reserves 46 px (`DECOR_H`) also under server-side decorations: the first window is 542 px high where 588 would fit.
- Lipstick: the pointer on the navigation-bar strip goes to the app.
- Lomiri: no keyboard inset while the app is turned. Rerun camera-reload on the X23.
- Sailfish keeps the letterbox; its rotation is unchecked.

## Camera
- Fossify Camera on Parallels is unchecked (no APK locally); Open Camera works on the MacBook camera. The `camera` group prints a harmless stray FAIL line from a test-app race (hello.camera recreates its activity).
- Halium: preview rotation, `getCallingUid`, rear-camera still, present fences.

## Input / IME
- KDE navigation bar (the mobile bar under server-side decorations, `navbar auto|on|off`): the manager Settings row for it is not compiled in any host build (build the manager once); pixel-check the light scheme; `haptic_feedback_enabled` is not modelled by the hub's vibrator.
- Wheel and stylus: written from the protocols and Android docs, never run on real hardware (touchpad, tablet); stylus slider and tool wheel are not delivered; AXIS_TILT/ORIENTATION follow a remembered InputReader formula, not a checked source.
- Non-Latin input: map the xkb layout to the app's `LocaleList` and IME subtypes; RTL cursor, selection and bidi in an EditText; layout-switch hotkeys (Alt+Shift, Super+Space); `KeyEvent.getUnicodeChar()` for non-Latin keys in apps that read key events.
- text-input-v3 has no show/hide request and no enter-key hint (labels are the keyboard's own).
- OTR2 in a mobile maximized window and FlorisBoard typing.
- Lomiri greeter: only the first uinput tap registers.

## Permissions and Android rules
- HIDL (`docs/HIDL.md`): needs a phone: the stack in a host-HAL instance (camera/media/graphics = device) starts the manager and writes the marker, forwarded names still work, an own service there. Rerun `tests/run hidl host-binder` on the x86 PC with the new libraries.
- Storage: `UNMOUNTABLE` not sent (udisks sends no failed-mount event and an unknown file system has no UUID to key a volume by); scoped storage does not see symlinks an app makes itself or `/proc/self/cwd`-relative opens.
- Call audio: native-stream route needs a real AAudio/OpenSL test and per-client playback configs (today the audio policy's `isStreamActive`/`isSourceActive` is instance-wide).

## Startup and memory
- A meson-installed dev tree's CLI that runs without `APKLAYER_NATIVE` relinks the shared `~/.local/share/apklayer/runtime/<ver>` to itself (`setUpBundle` / `setup-runtime.sh` use `ln -sfn`); deleting that tree breaks the owner's runtime (this broke the tester PC on 2026-11-15). Make it refuse, copy, or give dev trees their own runtime dir.
- Measure the JVM heap limits on the phones (and an app with a live heap above 500 MB).
- ~1 GB per app JVM on the Volla (UTM: idle PSS hub JVM 480-500 MB, spare JVM 280 MB; JVM flags already tuned); NewPipe ~21 s cold, ~12 s warm.
- Composer GL context ~80 MB: measure on a phone while an app draws.
- A PulseAudio-only sound server started later is used only from the next stack start (PipeWire is picked up per stream); needs an audio HAL hot-swap.

## Native bridge (x86_64)
- Diagnostics: `tools/ace/` (bpftrace on the translator and glibc, `pgr-netcut.sh`); translator notes in `docs/X86_64.md`.

## Sandbox (before it can be the default)
- NVIDIA sandbox: the broker and `nvidia-modprobe` at 0660 and for missing nodes need a Debian-style box (nodes are 0666 on the tester PC, no `nvidia-modprobe`).
- Each tree running the sandbox group needs `sudo sh tools/host-setup/sandbox.sh b/cli/apklayer` once.
- A release-candidate deb with the sandbox on for a day.

## Packaging and releases
- File associations (`docs/FILE-ASSOCIATIONS.md`, group `open-files`): needs a person: a real double-click of .apk/.apks/.xapk/.apkm in GNOME Files and Dolphin, and OpenVPN importing an .ovpn from the file manager. Open: the manager install dialog has no name/icon/permissions preview before Install; `apklayer open` under the sandbox; Flatpak (mime package needs an `xdg-data/mime:create` override and the host's `update-mime-database`), Sailfish and Ubuntu Touch variants; the manager's multi-file install queue.
- Click: Share/"Open with"; "Choose file…" from the manager; the click update install (`installClick`) is untested off a phone.
- Flatpak: launcher portal dialog.
- Flatpak: the `org.freedesktop.Sdk.Extension.openjdk25` extension and 26.08 runtime are unverified (manifest now names JDK 25).
- amd64 release leg, release guard's first tag run, PackageKit/pkexec install: untested.
- First command after install blocks ~75 s for runtime setup on a phone (10 s on Parallels with the parallel setup; the phone is not re-measured).
- System-app reinstall on runtime change: unchecked on a real upgrade or phone.

## Manager
- `manager/tests/ui-updater.sh` (called at the end of `ui.sh`) fails on the UTM VM, also on the unmodified manager: first-run labels not found at the 100 ms input time, and the VM's codename `resolute` has no package in the release fixture.

## Hardware
- VPN for apps, host-wide (owner decided 2026-11-15; the instance-only variant is dropped): `VpnService` with a real TUN device on the host and the default route through the app's VPN (WireGuard, OpenVPN for Android), the VPN in `ConnectivityManager`, the manager shows it and can disconnect it. Agent working.
- Vibrator: `composePwle` on HALs with PWLE capability instead of the 20 ms step fallback.
- iio-sensor-proxy gives only derived values: no accelerometer or compass type. On Parallels a source-tree CLI needs `--no-daemon` plus `APKLAYER_NATIVE`/`APKLAYER_BT`/`APKLAYER_BINDER=userspace` (the old 0.3.4 daemon takes over).
- USB: one chooser for several apps; sandbox node access.
- Bluetooth: pairing paths, Sailfish agent, truly simultaneous connects to one server socket (the mock keeps one client profile per UUID).
- Push: a real WorkManager app, constraints, resume after suspend, microG 0.3.x.

## Tests and tooling
- Password-ssh hosts (UTM VM, tester PC) intermittently refuse auth ("Permission denied" about every other connection): `tests/run` needs a retry in `vm.ssh`/`vm.copy`.
- `fullscreen.py` and `kde-window.py` compare integer sizes against KWin's fractional geometry (fail at 150% scale on the tester PC, also on main).
- `check-prebuilts.sh` does not catch stale `libvulkan.so`, `libapklayer_vulkan.so`, `vulkan.apklayer.so` (not in the lock; the tester PC's `~/apklayer-build/prebuilts` copy is ~25 libraries behind): add them to the lock.
- `tests/run` pushes the whole tree each time (4-8 min over the tester PC's link): push changed files only.
- UTM: delete `/etc/udev/rules.d/71-gpusel-vgem.rules` after its next reboot.

## Needs hardware or a person
- AMD: Vulkan presentation and host ES1 untested.
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
