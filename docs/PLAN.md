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
- ANGLE on ARM64: the Android arm64 libraries (GN build with patch 0007) ship from the prebuilts (runtime 0.5.6); `gles-angle=on` works with `graphics = device` on the X23 (Mali) and on Venus (apps on ANGLE Vulkan, compositor native). Open: the Linux aarch64 host libraries (built, load, not shipped) need an arm64 desktop with a real Vulkan driver (VK_EXT_image_drm_format_modifier + dma-buf import: Panfrost/PanVK, Turnip, Honeykrisp or NVIDIA aarch64) to verify DMA-BUF import and composition; lavapipe lacks the modifier extension and Venus cannot import virgl buffers, so neither stands in. The X23 (libhybris Vulkan over Mali) runs the whole host-ANGLE stack since 2026-11-21 (branch hybridge: the path layer leaves libhybris's own /system and /vendor opens alone, vulkan.apklayer takes the sync-fd functions from vkGetDeviceProcAddr, the shell copies dmabuf frames into wl_shm without linux-dmabuf; UiBench, hello.vulkan, hello.hwbitmap; runtime/angle/README.md "ANGLE host libraries on the X23"). Open there: Lomiri-level screenshot of the copy path and its frame rate (needs an unlocked phone), shipping the host libraries (+ libapklayer_hostGLESv2.so symlink) for aarch64 packages, Sailfish.
- arm64 graphics HALs: X23 (click 0.5.5, runtime 0.5.5, 2026-11-21) UiBench, hwbitmap, Vulkan (Mali-G57, present 600 frames) pass; Parallels virgl groups pass (black-screen cause: direct dma-buf mmap on virtio-gpu, fixed). UTM (Venus GPU, 2026-11-21): gpu gpu-window vulkan vulkan-textures vulkan-select window-formats gfx-readback texture-view fences gles1 pass; vulkan-present passes there since 2026-11-21 (Venus allocates the swapchain memory and exports it over the gralloc fd; ANGLE on Vulkan runs in the apps with the compositor on virgl). Validation leftovers on NVIDIA: usage-02275 (emulated SRGB image views inherit STORAGE usage; a VkImageViewUsageCreateInfo would quiet it).
- Arknights: Endfield on NVIDIA runs past the login screen (owner, 2026-11-20); earlier Tencent ACE ended it ~60 s in (CHANGELOG 2026-11-12): check a longer session.
- Hardware bitmaps on NVIDIA (fix in tree, 2026-11-20: vulkan.apklayer's AHardwareBuffer import, uncompressed tiled gralloc buffers): in 0.5.4 (runtime 0.5.3): check YouTube's thumbnails (Venus/arm64: hwbitmap passes on UTM through GL; the Vulkan HardwareBuffer import is off on Venus). `apklayer screenshot` under ANGLE on NVIDIA still fails on the stock runtime (seen 2026-11-20). YouTube signed out: the EU consent page was wider than the window ("Inloggen" cut off).
- A package's main process ran twice in the owner's instance (2026-11-21): a component host (`com.android.vending`, started for a bound service) and
  the launch's JVM beside it; on Android a launch joins the running process.
- NativeActivity (2026-11-21, HostNativeActivity, libapklayer_nativeloader.so): no test app yet (a NativeActivity library needs the NDK: hello-nativeactivity
  for x86_64 and arm64, input through its InputQueue, finish); a full 3DMark benchmark through its UI on NVIDIA (each workload, results screen).

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
- JDK 25 on the phones (the only JDK since 0.5.2): X23 (click 0.5.5) groups pass, hello.vulkan starts 19 s with the stack up, 33 s cold; Quintus 4.5 s warm to first frame, AOT cache works; not measured against 21 (gone).
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
- Ubuntu Touch: turning by hand (the fake accelerometer did not reach sensorfwd). On the 0.5.5 click the Install dialog is compact (screenshot on the X23); open: UpdateBanner height (no update offered to force it), HubIncoming of a replaced Share (needs Lomiri's Content Hub).
- Bluetooth PIN dialog on a legacy device. A real USB serial adapter.
- Push: a real Telegram/WhatsApp message with microG; the unit starting the stack at login.
- By eye: window raise, text magnifier, portal file chooser, pkexec, Google sign-in, playback, audio, colours, IME look.
- Fullscreen: NewPipe player on GNOME and the X23; default insets of new windows; no configuration-changed broadcast.
- Apps: a Godot 4 game, JW Library thumbnails, a NewPipe download.

## Notifications on the phones
- Lomiri (X23): popups now live ~5 s (bridge/portal/notify-bus.py, one bus connection; `ut notify` PASS, `ut notify-bus` PASS in 2 of 3 runs, one early failure unexplained). Open: popup tap, action and dismiss (not driven; needs a Lomiri screenshot or a bubble position).
- Lipstick: swipe-away delete intent and action buttons (`x-nemo-remote-action-*`) are not driven by phone-test; group/summary stacking and a foreground-service entry need a human look at the events view.
- The phones carry old host classes until the next click/RPM: rerun `phone-test.sh sfos notify` and `ut notify` after installing 0.5.x.
