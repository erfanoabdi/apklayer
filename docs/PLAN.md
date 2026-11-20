# apklayer: open work

Only what is still to do; done items are deleted. Only the lead edits this file. History: `CHANGELOG.md`, `docs/history/`. Parked: `docs/NOTPLAN.md`. Closed ART work: `docs/ART-DEFERRED.md`. Rules: `CLAUDE.md`.

## Ground rules
- GitHub releases only (debs, Sailfish RPM, Ubuntu Touch click, Flatpak).
- No per-app hacks; `tests/corpus/` measures compatibility. CTS last.
- Hardware only through Android's own interfaces (AIDL/HIDL HALs, framework classes).
- Halium: sensors via sensorfw, location via GeoClue (Sailfish) or Lomiri's location service; vibrator, camera, media use the device HALs.
- Replace an installed library by temp file + `mv`/`install`/`rsync`, never `cp`/`scp` onto a library a running JVM has mapped (it faults at exit); `tests/vm/exit-clean.py` shows both.
- No cached app processes; always-on only for push/background.
- An Android IME stays optional. No Recents in the mobile navigation bar.

## Bugs
- PGR's Main story chapter menu slows down on ANGLE and native NVIDIA while the Battle Screen stays smooth at the same size (2836×1188: ~27 ms between frame-ready timestamps when slow vs 16 ms, ~3.5 ms ready-to-present in both; the `d721767a` queue rewrite made Home worse and was rolled back). glbench reaches 180 Hz natively and on ANGLE, so it does not reproduce it. Compare sustained Home and chapter-menu timing at a fixed size with the 2026-11-18 monitor pacing, separately from resize (acquire errors, commit timeouts); Android ANGLE has no `EGL_ANDROID_get_frame_timestamps`: check whether the game's pacing depends on it.
- ANGLE GLES on x86_64 (Google Android 17 build, Vulkan): the compositor-side fixes of 2026-11-18 (tiled NVIDIA compositor buffers, SurfaceFlinger on Linux ANGLE, ANGLE patch 0003 for AIDA64's `vkGetFenceStatus` device loss) and the 2026-11-18 EGL shim are in runtime 0.5.2: retest PGR, AIDA64, Azur Lane and 3DMark (black virtual display, six-acquired-buffer limit, sync timeouts). GLES 3.2 needs a dedicated probe.
- ANGLE on ARM64: build Android arm64 ANGLE and Linux aarch64 host EGL/GLES from the Google source, then verify Vulkan DMA-BUF import and SurfaceFlinger composition before packaging it.
- arm64 graphics HALs of runtime 0.5.2 (egl, vdisplay, mapper of 2026-11-18; vulkan.apklayer with ASTC/ETC2 emulation and bind-at-create, Venus keeps the bind at acquire) never ran on an arm64 host: run groups `gpu gpu-window vulkan vulkan-textures` on UTM and a Halium phone check. Validation leftovers on NVIDIA: usage-02275 (emulated SRGB image views inherit STORAGE usage; a VkImageViewUsageCreateInfo would quiet it).
- Arknights: Endfield on NVIDIA runs past the login screen (owner, 2026-11-20); earlier Tencent ACE ended it ~60 s in (CHANGELOG 2026-11-12): check a longer session.
- Play services sends a reply of exactly 8388608 bytes (`Large reply transaction`, code 46); since 2026-11-15 its caller gets BR_FAILED_REPLY as on the kernel. Which call it is and whether the caller copes is unknown: read hub.log for `Large reply transaction of 8388608` at the next one.
- Play services' `:persistent` exits with 1 now and then ("Instance hub disconnected" for binds meanwhile); no System.exit caller seen: run with `APKLAYER_EXIT_TRACE=1 APKLAYER_JOPTS=-Dapklayer.exittrace=true` and read its component log at the next one.
- YouTube 21.45: check the signed-in feed and a video in the owner's instance with the ApklayerBuffers.array fix (2026-11-16); signed out, the EU consent page was wider than the window ("Inloggen" cut off).

## Input / IME
- Wheel and stylus (touchpad, tablet) never ran on real hardware; the stylus slider (AXIS_WHEEL) and tool wheel (AXIS_SCROLL) go through the test input only.
- Non-Latin input: RTL cursor, selection, bidi in an EditText (needs a test app and a human look); layout hotkeys on a real compositor; `getUnicodeChar()` of a non-Latin key has no meta variants.
- text-input-v3: no show/hide request or enter-key hint, never run against a real compositor (GNOME/KWin/Phosh OSK); fcitx stops composing while hidden. F1-F12 have no Android keycode in `keysym_to_android`.

## Permissions and Android rules
- Storage: scoped storage follows symlinks only in the process that made one (or its children) and cwd-relative paths only under the instance's Android/; earlier links and `openat(dirfd, ...)` into another package's directory are not seen (needs a fd-based check).
- Call audio: the hub polls the audio policy each second while two communication requesters compete; a native client carries uid 1000, so another process of the same app does not count; run `audio-uid` and `audio-backend-swap` on the x86 PC.

## Native bridge (x86_64)
- PGR's home-scene noise is gone with the heavy tier's loop and local passes off (binary_translation 0081); the bug in those passes is not found: capture the game's regions with BERBERIS_HEAVY_DUMP and write the replay test (0082), then fix and turn them back on.

## Sandbox (before it can be the default)
- NVIDIA sandbox: broker and `nvidia-modprobe` (0660, missing nodes) run only on a real Debian-style NVIDIA box.
- A release-candidate deb with the sandbox on for a day.

## Packaging and releases
- File associations (`docs/FILE-ASSOCIATIONS.md`, group `open-files`): needs a person: double-click of .apk/.apks/.xapk/.apkm in GNOME Files and Dolphin, OpenVPN importing an .ovpn. Open: `apklayer open` under the sandbox for a running app's `onNewIntent`; adaptive/vector icons in the install preview; Flatpak, Sailfish, Ubuntu Touch variants; the multi-file install queue.
- Flatpak: launcher portal dialog.
- JDK 25 on the phones (the only JDK since 0.5.2): Quintus runs it 1-2 s slower than 21 + CDS had; X23 untested on 25.
- Release: PackageKit/pkexec install of the deb untested; the Flatpak `openjdk25` extension and 26.08 runtime are only built in CI.
- System-app reinstall on runtime change: unchecked on a phone.

## Hardware
- NFC (`docs/NFC.md`, group `nfc`): PC/SC against a real reader unrun; neard backend; NFC permission check, `ACTION_ADAPTER_STATE_CHANGED`, tag-removal callbacks, a chooser for several apps. HCE and a phone's NFC HAL are not planned.
- Sailfish phone: Maliit's sideways keyboard in landscape; the manager's text field has no keyboard under Lipstick; Bluetooth and scoped storage not run.
- Sailfish location: needs `/var/lib/location/location.conf` enabled and connman gps `Powered=true`; no real fix outdoors yet; sensorfw stops sensors with the screen off (zeros).
- VPN for apps (`docs/VPN.md`, group `vpn`): OpenVPN's data channel (`libovpnexec` sits in `ppoll`, never reads the TUN; try `APKLAYER_RAW_SYSCALL_VIEW=off`); Disconnect button and pkexec dialog on a real desktop; per-app sandbox; Sailfish via connman; instances share `/1` routes; no kill switch; the libc SIGSYS handler corrupts Go threads (only `fast_syscall` numbers are safe; make it libc-free); needs an OpenVPN server without OAuth2 and the owner's WireGuard `wesley.conf`.
- USB: sandbox path (`usb-sandbox`) unrun: the UTM VM lacks `tools/host-setup/sandbox.sh`.
- Bluetooth: pairing on real devices; Sailfish `ensure_agent` leaves `agent` set when RegisterAgent fails (harmless); our agent is not the default one; BlueZ's choice between two profiles of one UUID unchecked.
- Push: no `hello-work` test app (needs a work-runtime AAR from the owner); job constraints, periodic runs and resume after suspend untested; microG 0.3.x.

## Needs hardware or a person
- Quintus: re-run `phone-test.sh sfos` groups with the current RPM (`tools/halium/unlock.sh sfos`).
- Halium: vendor HALs, `media = device`, NewPipe network; wheel/stylus, text-input and the layout subtype cannot run under Lomiri: Sailfish/KDE check only.
- Sailfish: a finger on the host dialog (Open Camera with CAMERA revoked was denied at once, no prompt seen); a geoclue fix outdoors.
- Ubuntu Touch: turning by hand (the fake accelerometer did not reach sensorfwd); the manager's Install dialog (path field fills half the screen); the update banner is half a screen tall while it runs (`manager/ui/setup.slint` `UpdateBanner`); `HubIncoming/<n>` of a replaced Share stays.
- Bluetooth PIN dialog on a legacy device. A real USB serial adapter.
- Push: a real Telegram/WhatsApp message with microG; the unit starting the stack at login.
- By eye: window raise, text magnifier, portal file chooser, pkexec, Google sign-in, playback, audio, colours, IME look.
- Fullscreen: NewPipe player on GNOME and the X23; default insets of new windows; no configuration-changed broadcast.
- Apps: a Godot 4 game, JW Library thumbnails, a NewPipe download.

## Notifications on the phones
- Lomiri (X23) drops every org.freedesktop.Notifications notification within ~30 ms: find why (notifyplugin `pruneExpired`/QML, a setting, a clock/zone problem) or deliver through the click Postal route (`com.lomiri.Postal`); until then no popup, tap, action or dismiss can be checked there.
- Lipstick: swipe-away delete intent and action buttons (`x-nemo-remote-action-*`) are not driven by phone-test; group/summary stacking and a foreground-service entry need a human look at the events view.
- The phones carry old host classes until the next click/RPM: rerun `phone-test.sh sfos notify` and `ut notify` after installing 0.5.x.
