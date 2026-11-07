# Halium devices: survey and the host binder context

Plan: `docs/PLAN.md` "Plan: hardware providers" (steps d and e). Owner's rule (2026-11-04): through the host context only the
vibrator, camera and media (codecs) HALs; never the sensors or GNSS HAL (one client each, held by sensorfw and the OS's location
service; on Halium those come through the OS services). Listing services (lshal, service list) is fine.

## The owner's phones (surveyed 2026-11-04 and 2026-11-07, read only)
| | Ubuntu Touch 24.04 (`phablet@192.168.41.61`) | Sailfish OS 5.1.0.43 (`defaultuser@192.168.41.62`, retired 2026-11-07) | Sailfish OS 5.0.0.62 (`defaultuser@192.168.41.153`, surveyed 2026-11-07) |
|---|---|---| --- |
| Device | Volla Phone X23 (`vidofnir`, MediaTek) | Jolla C2 (Reeder S19 Max Pro S, Unisoc) | Volla Phone Quintus (`algiz`, MediaTek MT6877 Dimensity 1080, Mali-G68 MC4), 7.5 GB RAM, rootfs 6.8 GB (`/home` on it), `/userdata` 225 GB |
| Kernel | 5.10.209, aarch64 | 5.4.233, aarch64 | 4.19.191-halium-algiz, aarch64 |
| glibc | 2.39 (as Ubuntu 24.04: apklayer's Parallels builds run) | 2.30 (too old for apklayer's builds) | 2.30 (busybox userland: sh, cp, tar; zypper; Python 3.8; no Java; zip/unzip only after `zypper install zip unzip`) |
| Android base | Halium 12: system API 32, vendor API 31 | Android 13: API 33 (system and vendor) | Halium 13: API 33 (system and vendor), `ro.vndk.version` 33 |
| Container | LXC `android` (`/var/lib/lxc/android`, lxc-android-config) | none: droid-hal-init runs the Android services natively | LXC `android` (`/var/lib/lxc/android`; `/android/{system,vendor,data}`, `/system` and `/vendor` link there) |
| Binder | binderfs: `binder`, `hwbinder`, `vndbinder` (0666), `anbox-binder/-hwbinder/-vndbinder` (0600, Waydroid's) | binderfs: `binder`, `hwbinder`, `vndbinder` (0666), `puddlejumper`, `hwpuddlejumper`, `vndpuddlejumper` (0600, Jolla AppSupport) | binderfs: `binder`, `hwbinder`, `vndbinder` (0666), `anbox-binder/-hwbinder/-vndbinder` (0600, Waydroid's; no AppSupport) |
| Kernel binder | CONFIG_ANDROID_BINDERFS=y, devices from CONFIG_ANDROID_BINDER_DEVICES, no `features/` dir | the same | the same (CONFIG_ANDROID_BINDER_DEVICES lists the six) |
| Servicemanagers | servicemanager, hwservicemanager, vndservicemanager (uid system) | the same | the same |
| AIDL HALs (/dev/binder) | vibrator (`IVibrator/default`, `IVibratorManager/default`), lights, power, memtrack, keymint, gnss, NNAPI shims; 45 names | lights, power, health, usb, keymint, radio, drm, face, sprd vendor ones; 84 names; no vibrator HAL | vibrator (`IVibrator/default`), lights, power, gnss, lineage health; Sailfish's droidmedia `camera_service` registers `media.camera`, `media.audio_policy`, `android.media.ICameraRecordService` here; 34 names |
| HIDL HALs (/dev/hwbinder) | camera.provider@2.4 (internal/0), media.c2@1.x, omx, sensors@2.0, gnss, allocator@4.0, composer@2.1-2.3, bluetooth, nfc, drm; 264 entries | camera.provider@2.4 (legacy/0), media.c2@1.x, omx, audio@7.x, gnss, allocator@4.0, composer@2.1-2.4 | camera.provider@2.4 (internal/0, MediaTek's 2.6 impl), media.c2@1.0-1.2 (default, software), omx, sensors@2.0, gnss 1.0-2.1, allocator@4.0, composer@2.1-2.3; 262 entries |
| Compositor | Lomiri on Mir (lomiri-system-compositor), Wayland `wayland-0` | Lipstick (QtWayland) on `/run/display/wayland-0` | Lipstick 0.36 (QtWayland 5.6) on `/run/display/wayland-0` (the session's `WAYLAND_DISPLAY=../../display/wayland-0`) |
| Wayland buffers | `android_wlegl` v2, no `zwp_linux_dmabuf_v1` (globals: android_wlegl, qt_windowmanager, wl_compositor 4, wl_subcompositor, wl_seat 6, wl_output 3, wl_shell, zxdg_shell_v6, xdg_wm_base, wl_shm) | `android_wlegl` (libhybris `eglplatform_wayland`, server side in lipstick), no dmabuf | `android_wlegl` v2, no dmabuf, **no xdg_wm_base** (globals: wl_compositor 3, wl_data_device_manager 1, wl_shm, qt_hardware_integration, android_wlegl 2, qt_surface_extension, qt_touch_extension, qt_windowmanager, wl_seat 3, wl_output 2, wl_shell, lipstick_recorder_manager, alien_manager; no wl_subcompositor, viewporter, fractional scale) |
| GPU, EGL | Mali-G57 | | Mali-G68 MC4: `ro.hardware.egl` meow (`/vendor/lib64/egl/libGLES_meow.so`, MediaTek's layer over `libGLES_mali.so`), `ro.hardware.vulkan` mali, `ro.board.platform` mt6877, gralloc4 (allocator@4.0 service, mapper@4.0-impl-mediatek passthrough), ion (`/dev/ion`, no dma_heap), `/dev/mali0` 0666 |
| Audio | PulseAudio, no PipeWire daemon | | PulseAudio 14.2 (droid modules, `/run/user/100000/pulse/native`), no PipeWire |
| Vibrator, feedback | hfd-service | | ngfd (droid-vibrator plugin over the device's IVibrator); no hfd, no feedbackd |
| Screenshots | `apklayer screenshot` | | Lipstick's `saveScreenshot(path)` (`org.nemomobile.lipstick` `/org/nemomobile/lipstick/screenshot`, session bus), only for the `privileged` group: `devel-su -p` |
| Sensors, location | sensorfwd (hybris), lomiri-location-serviced | sensorfwd (hybris), geoclue providers | sensorfwd 0.15 (hybris binder plugin); GeoClue 1 (0.12.99) with the hybris-binder and MLS providers, no GeoClue 2 |

How: `getprop`, `/proc/config.gz`, `ls /dev/binderfs`, services listed with libgbinder (`binder-list` on Sailfish, a ctypes call of
`gbinder_servicemanager_list_sync` on Ubuntu Touch), Wayland globals with a ctypes registry listener. Both phones' `/dev/binder` and
`/dev/hwbinder` are world read-write: an ordinary user process reaches the device servicemanagers without root.

Consequences:
- On a phone `/dev/binder` *is* the device's. apklayer's stack must run as a named instance (its own `binder-<id>` etc. through
  `tools/host-setup/binder.sh <id>`, root once per boot), and the device is reached only through the host context.
- The hosts are older than our libbinder (Android 16): Halium 12 and Android 13 write a binder's stability as a Category and number
  IServiceManager's methods differently (patch 0006 handles both, below).
- Sailfish needs apklayer built against glibc 2.30 (or a sysroot of its own) before anything runs there.
- Graphics (step e): neither compositor offers linux-dmabuf; the HWC needs the `android_wlegl` path (`create_handle`/`create_buffer`
  with the gralloc handle's fds and ints) and the device's gralloc/EGL through the vendor namespace.

## Design: what our libraries need (from Waydroid's patch sets, `~/GitHub/waydroid/android_vendor_waydroid/waydroid-patches/base-patches-33`)
- **AIDL, libbinder** (Waydroid frameworks/native 0015 -> our `patches/aosp/frameworks/native/0006-libbinder-host-context.patch`, done):
  a second ProcessState on `/dev/host_binder`, per-context IPCThreadState (own TLS key and thread pool), BpBinder remembers its context,
  Parcels received through it (recognised by the buffer address in its mmap) or carrying its proxies use it, BC_FREE_BUFFER through
  the context the buffer came from; BackendUnifiedServiceManager asks the host's servicemanager for names listed in
  `/data/apklayer/host_hals.conf` when the instance's own has none, and reports them declared. Differences from Waydroid: the host
  lookup is a raw `getService` (transaction 1 in Android 11-16; `checkService` moved from 2 to 3 in Android 14) instead of the
  generated proxy; stability as Category for hosts that write it (seen in a host reply, then written so into what goes to the host);
  the allow-list is per instance (the instance's `/data`), not `/system/etc/hostaidls.conf`.
- **Path**: `bridge/native/paths.c` maps `/dev/host_binder` to `$APKLAYER_HOST_BINDER` (a phone: `/dev/binderfs/binder`; the test:
  another instance's binder). No variable: no host context.
- **Not needed now**: Waydroid 0009 (host uid as system uid) only matters when host processes call into our services; 0002/0003
  (SELinux off in servicemanager, no TXN_SECURITY_CTX) we already have (our 0001).
- **HIDL, libhwbinder + libhidl** (camera provider, media.c2 and OMX are HIDL on both phones; done 2026-11-05):
  `patches/aosp/system/libhwbinder/0001-host-hwbinder-context.patch` is libbinder's 0006 for `/dev/host_hwbinder` (second ProcessState,
  IPCThreadState per context, BpHwBinder and Parcel carry it, buffers freed by address; the host pool starts without waiting for a local
  hwservicemanager) plus Waydroid libhwbinder 0002 (no TXN_SECURITY_CTX). `patches/aosp/system/libhidl/0001-host-hals.patch`:
  getRawServiceInternal (every `IFoo::getService`) asks the host's hwservicemanager first for HALs listed in `host_hals.conf` (an
  fqName, or a package for every version), with IServiceManager 1.0's `get()` (every hwservicemanager since Android 8). It replaces
  Waydroid's libhidl 0001-0005, 0007 (they switch one ProcessState between drivers and wait for/re-new the service manager; apklayer runs
  no hwservicemanager, so a listed HAL goes to the host first) and 0008 (their `/system/etc/hosthals.xml`). Not ported: 0006 (vendor
  passthrough paths; ours will be bionic_translation's sphal namespace). The HIDL interface library ABI is unchanged.
- **Allow-list generation** (done 2026-11-05): `apklayer providers set <domain> device` (vibrator, camera; refused for sensors and gnss)
  records `provider = "device"`; the CLI (at once) and the stack (at start and reload) write `<instance data>/apklayer/host_hals.conf`
  with that domain's AIDL interfaces and HIDL packages and start no apklayer front for it; with that file present the CLI's
  environment points the host contexts at `/dev/binderfs/binder` and `/dev/binderfs/hwbinder` unless `APKLAYER_HOST_BINDER` /
  `APKLAYER_HOST_HWBINDER` say otherwise. Open: cameraserver and the codec2 client find HIDL providers through hwservicemanager's
  `listManifestByInterface`/notifications, which apklayer does not run: they need a host-aware listing next.

## Tests
- `tests/vm/host-binder.py` (group `host-binder`): a second instance (`<instance>-dev`) runs its servicemanager and registers
  `apklayer.test.IHostEcho/default`; this instance finds it only through the host context, transacts on it (dump), and does not
  ask the host for an unlisted name. HIDL: `hidltool device` is the device instance's hwservicemanager serving `atrace@1.0`
  (a small HIDL interface still in the tree: vibrator@1.0 is gone); this instance's `IAtraceDevice::getService` reaches it through
  the host hwbinder context and calls it (strings and vectors in both directions).
- Ubuntu Touch phone: an `apklayer exec` of `servicetool` in an instance with `APKLAYER_HOST_BINDER=/dev/binderfs/binder` calls the
  device's `android.hardware.vibrator.IVibrator/default` (`getCapabilities`, a 50 ms `on`).

## Running apklayer on the Ubuntu Touch phone (2026-11-04)
- The Parallels build (Ubuntu 24.04, glibc 2.39) runs unchanged: runtime copied to `~/apklayer-dev/rt` (native, android-arm64/lib64,
  bt, deps), the repo with its meson build in `~/apklayer-dev/repo`, the environment in `~/apklayer-dev/env.sh`. Needed bionic_translation
  0036: the linker's default `/system/lib` and `/vendor/lib` are the device's 32-bit Android there, and every program crashed on
  `/system/lib/libc.so`. Our binaries need GLIBC_2.38: Sailfish (2.30) needs its own build.
- The instance `dev` uses binderfs nodes `binder-dev`, `hwbinder-dev`, `vndbinder-dev` (made with sudo, 0666; gone at reboot).
- Results: the instance's own servicemanager does not have the vibrator; through the host context the Android 12 device's
  `IVibrator/default` is found, `getCapabilities` answers (0: no optional capabilities), and `on(50 ms, null callback)` succeeds once
  our binder stability goes as a Category (with the plain Android 14+ word the A12 HAL refused the parcel with BAD_TYPE). Run with the
  libbinder and servicetool built in ~/los24 (sha1 217d29ae... and c1018ac3...); the script is `~/apklayer-dev/ut-vibrator.py` there.
- HIDL (2026-11-05): `apklayer providers set camera device` wrote the instance's `host_hals.conf` and the CLI pointed the host contexts
  at the phone's binder devices; `hidltool cameras internal/0` got the device's HIDL camera provider@2.4 from its hwservicemanager and
  listed 3 cameras (`device@3.6/internal/0..2`, getCameraIdList only); with `android.hardware.media.c2` added by hand, `hidltool codecs`
  listed the device's 16 MediaTek Codec2 components (listComponents only). Libraries: libhidlbase, hidltool, atrace@1.0,
  camera.provider@2.4, hidl.manager@1.0 from ~/los24 (the phone's older runtime copy also lacked camera.device@1.0, copied from UTM).
- Not yet: an app (JVM) on the phone: the window shell needs `android_wlegl` (no dmabuf there) and a JDK in the user's directory;
  the Java path is the same native lookup (ShadowHostServiceManager -> AServiceManager_checkService).

## Graphics on the Ubuntu Touch phone (2026-11-05)
`apklayer providers set graphics device`: the device's gralloc (allocator@4.0 through the host hwbinder context, the passthrough mapper) and its
GPU (Mali-G57 through Android's libEGL) in apklayer's processes; the vendor libraries load in bionic_translation's vendor namespace (0039).
- The vendor namespace's VNDK is the vendor's (`ro.vndk.version` through the device's getprop: 31 on the X23, whose system is 32).
- The vendor libraries share apklayer's libhidlbase (0041, `BIONIC_VENDOR_SHARED_LIBS=libhidlbase.so`). Found with a stack scan of the hung
  RenderThread (`/proc/<pid>/task/<tid>/syscall` for sp and pc, the stack read from `/proc/<pid>/mem`, words in executable mappings printed as
  library+offset): libhwui -> libEGL -> libGLES_mali -> the VNDK's mapper@4.0.so (IMapper::getService) -> the VNDK's libhidlbase -> libbase
  WaitForProperty. Android 12's libhidl waits for `hwservicemanager.ready` and knows no `hwservicemanager.disabled` (hiding
  /system/bin/hwservicemanager does not help: it has no "installed" check either). The vendor closure of the driver and the gralloc impls imports
  149 libhidlbase symbols, all in ours; the VNDK 31 and 34 ABI dumps of libhidlbase have the same records, and libhwbinder 0001 keeps
  hardware::Parcel's size. The other choice, sending the vendor copy to the device's hwservicemanager, needs a per-caller /dev/hwbinder.
- The stack loads `device.prop` (cli/stack.cpp) from the device's live properties: ro.hardware.egl/vulkan, ro.board.platform, ro.vendor.*,
  vendor.*, debug.mediatek.*. MediaTek's mapper and allocator derive a buffer's allocation format the same way, from usage and
  `debug.mediatek.disp_decompress` (1 on the device): without it SurfaceFlinger's mapper refused the device allocator's AFBC buffers
  ("Buffer alloc format 0x61900000001 does not match descriptor (derived) alloc format 0x1"). Other debug.* stay apklayer's.
- Apps that draw (SurfaceFlinger screenshots, `apklayer screenshot`): UiBench, Calculator, Telegram, NewPipe, Shattered Pixel Dungeon and Anuto
  (libgdx), OTR2 (bgfx, GLES 3.1). Scripts on the phone (`~/apklayer-dev`): `ut-shots.sh <pkg>...` (run 25 s, screenshot at the run's end,
  stop), `stackscan.py <pid> <tid>` (the stack scan above, `tools/debug/stackscan.py`).
- Open: camera = device (cameraserver and HIDL provider listing, PLAN (e)); `sync_file_info returned NULL` (2026-11-05: these were the composer's present fences, eventfds without a render node; it now passes the composition's GPU fence, a Mali sync_file, and logs `present fences: ...` once; not yet checked on the phone, which was unreachable); the window on
  Lomiri needs a human check.

## Location on the phones (2026-11-05)
Never the GNSS HAL (its one client is the OS's location service): a GNSS backend over that service (bridge/hals/gnss, `providers set gnss <backend>`).
- Ubuntu Touch: no GeoClue (`org.freedesktop.GeoClue2` is not activatable; the geoclue backend's probe() is -1); `com.lomiri.location.Service`
  on the system bus (lomiri-location-serviced, dbus-cpp). The `lomiri` backend (probe 10) gets real fixes there as an unconfined client:
  CreateSessionForCriteria -> `/sessions/0`, StartPositionUpdates, then the service calls UpdatePosition (`ddbdbdbx`: altitude and horizontal
  accuracy present, vertical absent), UpdateVelocity and UpdateHeading (`dx`) about once a second; first fix ~10 s, 6.5-10 m. Checked with
  `tests/hals/gnss-backend-fixes` (built on Parallels) and dbus-monitor, in `~/apklayer-loc` (removed afterwards).
- Sailfish OS (read only): GeoClue 1 (geoclue 0.12) `org.freedesktop.Geoclue.Master` on the session bus, activatable, with the hybris and
  MLS providers; no GeoClue 2. The `geoclue1` backend's names and GetPosition signature come from the binaries' dbus-glib data
  (MasterClient.SetRequirements(iibi), PositionStart; Position.GetPosition -> `(iiddd(idd))`); tested only against a stand-in
  (tests/vm/geoclue1-mock.py): a Sailfish build is needed to run it there (glibc 2.30).

## 2026-11-05: Volla X23 display facts for the mobile window style (read only)
Read with this branch's `apklayer-shell --display-info` (built on UTM, copied to /tmp on the phone, run once against Lomiri's `wayland-0`, removed afterwards; nothing of `~/apklayer-dev` touched). Lomiri's wl_output: 720x1560 px, 65x140 mm, scale 1 (100%). Panel 6.0" (hypot 65,140 mm = 154 mm), 281 dpi -> density bucket 280 (`handheld_dpi()` is right; no fix needed). Cross-check: Lomiri's `GRID_UNIT_PX=14` (a grid unit is 8 dp: 14/8 = 1.75 = 280/160). `mobile 1`. Lomiri's environment (/proc/<lomiri>/environ): `XDG_CURRENT_DESKTOP=Lomiri`, `XDG_SESSION_DESKTOP=ubuntu-touch`, `DESKTOP_SESSION=ubuntu-touch`, `XDG_SESSION_TYPE=mir`, `QT_QUICK_CONTROLS_MOBILE=1`: `mobile_session()` matches the first two (case-insensitive) when the shell inherits them; started from ssh they are unset and the panel size alone gives mobile. Not checked on the phone (needs a window): Lomiri's maximize and top-bar area, touch on the navigation bar, Home's minimize.
## Hardware providers on the Ubuntu Touch phone (2026-11-05, Volla X23, instance `cam` in `~/apklayer-dev-cam`)
Every domain run end to end on the phone in its own instance (binderfs nodes `binder-cam` etc. from `tools/host-setup/binder.sh cam`, sudo once per boot; a copy of the
install in `~/apklayer-dev-cam/v2`, environment `env.sh`, Parallels builds copied over: the phone has no compiler). Start-check-stop in one command each, ending with `apklayer stop`.
| Domain | Provider | What ran on the phone | Result |
|---|---|---|---|
| graphics | `device` | UiBench, Calculator, Telegram, NewPipe, libgdx and bgfx games (the other agent's work, 2026-11-05), SurfaceView video (below) | draws on the Mali-G57 |
| camera | `device` | cameraserver over the device's HIDL `camera.provider@2.4` (`internal/0`: 3 cameras, real sizes up to 8000x6000); `hello.camera -Dhello.real=1` (camera2: list, open, preview + YUV + JPEG session, a 4608x3456 still); Open Camera with its camera2 and with its Camera1 API (live preview on screen, front and rear) | works |
| media | own Codec2 + the host's GStreamer (default) | `hello.nativeplayer` (original MediaPlayer, H.264 into a SurfaceView on the device's gralloc: `PLAYER PASS`, screenshot red) and `red-tone.mp4` (AAC through faad into the PulseAudio sink, 19456 frames delivered to the AudioTrack) | works |
| media | `device` (`providers set media device`) | the host's Codec2 (30 MediaTek components, `hidltool codecs default`) and OMX stores found through the host contexts | `hello.nativeplayer` H.264: the codec list now holds the device's components (`Available Codec2 services: "default" "software"`, 30 MediaTek ones from the device's own `/vendor/etc/media_codecs*.xml`), but creating a decoder fails without a log line ("Failed to create video/avc decoder"): not working yet |
| audio | PulseAudio (`-Daudio_backend=pulse`) | `hello.nativeaudio`: AudioTrack PCM 8 s at 48408 frames/s, 0 underruns, marker, stop/flush; capture of the microphone (120000 frames in 2.5 s) | works (the record-loopback assertion needs a null sink and is not run here) |
| vibrator | `hfd` (default) and `device` | `hello.hardware`: pattern and click through `Vibrator`/`VibratorManager`; with `device` the real AIDL `IVibrator/default` (`has=true`, no amplitude control) | works |
| gnss | `lomiri` (default) | `hello.hardware`: LocationManager gps fix (35.68, 51.56, accuracy 20 m, a real fix) | works |
| sensors | `sensorfw` (default) | `hello.hardware`: 14 sensors (accelerometer, gyro, magnetometer, light, pressure, proximity and the derived ones), a value each, 99 accelerometer events in 2 s at 50 Hz | works |

What was wrong with the camera and what changed:
- `providers set camera device` started no cameraserver (cli/stack.cpp started it beside an apklayer front only) and the Java side (`HostCameraShadows.declared()`) looked for an apklayer vintf fragment:
  both now also accept the device domain (`deviceDomain()`; the Java side reads `$APKLAYER_DATA/apklayer/host_hals.conf`). The vibrator manager of the hub (`HubVibrator`) the same.
- cameraserver finds HIDL providers through the default service manager's `listManifestByInterface` and `registerForNotifications`, which the instance's stand-in
  (`NoHwServiceManager`) answered with nothing: `patches/aosp/system/libhidl/0003` forwards them (and `getTransport`, `listByInterface`, `unregisterForNotifications`) for listed HALs to the host's
  hwservicemanager; `listManifestByInterface` falls back to `listByInterface` (the device's Codec2 answers only that).
- Camera1: libbinder 0007 reports the stack's own uid (phablet 32011) as AID_SYSTEM; cameraserver's trusted uid was the instance's own uid only
  (`patches/aosp/frameworks/av/0003` amended: AID_SYSTEM too). Without it every `android.hardware.Camera.open` died with "Untrusted caller".
- Open Camera (and the `hello.camera` check) draw rotated 90 degrees in the phone's landscape window (the sensor orientation is applied against a window that does not turn): a mobile-window-mode question, not the camera's.

Other fixes of this pass:
- `bridge/hals/media/codec.c`: gst-droid's `amc*` decoders (`amcvideodec-c2mtkavcdecoder2`, the device's MediaCodec through libhybris) rank above the software ones and fail inside the stack's
  processes ("GStreamer encountered a general supporting library error"): they are ranked NONE (set `APKLAYER_GST_AMC=1` to keep them). H.264 then decodes in software (openh264).
- Audio: the phone has PulseAudio and no PipeWire daemon (the PipeWire HAL library had no server to talk to: `AHAL_Stream: Failed to initialize the driver`). `bridge/hals/audio/pulse.c` is the
  same `apklayer/audio.h` over libpulse-simple (`meson -Daudio_backend=pulse`, `auto` picks it when PipeWire's library is missing). The stack's `XDG_RUNTIME_DIR` of this test instance is not the
  session's: `PULSE_SERVER=unix:/run/user/32011/pulse/native` in `env.sh` (a real user's instance has the session's directory).
- `providers set media device`: new domain (`android.hardware.media.c2`, `android.hardware.media.omx` listed, the stack's own Codec2 store is not started and its vintf fragment not staged); the stack loads
  `media.c2.hal.selection=hidl` last (apklayer.prop says aidl; a Halium 12 vendor's Codec2 is HIDL) and serves the device's `/vendor/etc/media_codecs*.xml` from the instance's root (the shared root's name apklayer's own components).
  Next step: the Codec2Client's creation status is not logged (a build with the failing call's status in the log).
- AAC: faad (the host's AAC decoder here) keeps its last frame ("still 1 frames left after draining"); the Codec2 AudioDecoder ended such works as corrupted: now empty.

How to run each check (on the phone; `. ~/apklayer-dev-cam/env.sh` gives `$A`): `ut-cam.sh [app] [ms]` (camera = device, the app's `[cam]` lines and cameraserver's log tail), `ut-shots.sh <pkg>` (run, screenshot, stop),
`ut-player.sh red-h264.mp4|red-tone.mp4` (`PLAYER PASS`, `player.png`), `ut-audio.sh` (the audio app's lines), and `apklayer run hello.hardware` with the default providers for vibrator, gnss and sensors.
Test apps: `tests/apps/build.sh hello-camera hello-hardware hello-native-player hello-native-audio` on the Mac, `apklayer install` on the phone. Open Camera's API: `~/apklayer-dev-cam/ocprefs.py <camera id>` edits its preferences (camera2 or the old API).

## The apt install on the phone (2026-11-05)
The Volla X23 runs Ubuntu Touch 24.04 (noble, glibc 2.39, arm64): the normal `apklayer` deb (`tools/release/build-deb.sh noble <version> <prebuilts> out/` on the Parallels VM, also noble arm64) installs on it as it is.

Install (needs root once, as any deb on Ubuntu Touch; the rootfs is read-only and 4 GB with ~540 MB free):
```sh
sudo mount -o remount,rw /
sudo apt install ./apklayer_<version>_arm64.deb     # pulls openjdk-21-jre-headless (~200 MB); `sudo apt-get clean` afterwards
sudo mount -o remount,ro /
```
The rootfs is `/dev/mapper/system_a`: an OTA update replaces it, and the deb (with its Java) must be installed again; `/home` is separate and keeps the
runtime, the apps and their data. Without the Java dependency (`dpkg -i --ignore-depends=openjdk-21-jre-headless`, as on the test phone) name a Java 21 in
`~/.config/apklayer/env` (`APKLAYER_NATIVE_JAVA=<jdk>/bin/java`); `apklayer setup` says when there is none.

What needs root, and where it happens (nothing at runtime):
| Step | Root? | Where |
|---|---|---|
| binderfs | no (the device's) | lxc-android-config's `mount-android-partitions` mounts `/dev/binderfs` before sysinit; `apklayer-binder.service` is ordered after it and never mounts a second one |
| the `ut` instance's binder nodes (`binder-ut`, `hwbinder-ut`, `vndbinder-ut`, 0666) | yes | `apklayer-binder.service` (enabled by the package; runs at install and every boot) for the ids in `/etc/apklayer/instances` (default `ut`); the device's own `/dev/binder` is never touched |
| another instance's nodes | yes | `echo <id> \| sudo tee -a /etc/apklayer/instances && sudo systemctl restart apklayer-binder` (the command the CLI and the manager print); kept over reboots |
| kernel modules | no | binder is built into the Halium kernel (CONFIG_ANDROID_BINDERFS=y) |
| AppArmor | no | apps from the drawer are unconfined legacy apps (lomiri-app-launch); unprivileged user namespaces work (no restriction sysctl in this kernel) |
| `/dev` permissions | no | the GPU, ion/dma-heap and the binder nodes the device opens to phablet already; the instance nodes are made 0666 by the unit |
| the WebView's switch file | no (before: pkexec) | the instance's own `/data/local/tmp/webview-command-line`, written by the CLI. The old step ran `webview.sh` through pkexec, which `chmod 1777`ed `/data` (on Ubuntu Touch `/data -> /android/data`, the device's Android userdata); it only did not because the phone has no pkexec |
| `apklayer setup` | no | everything under `~/.local/share/apklayer`, `~/.cache/apklayer`, `~/.local/share/applications` |

Ubuntu Touch has polkitd and Lomiri's polkit agent (`lomiri-polkit-agent`), but no `pkexec` binary: nothing in apklayer's first run asks for it now; the
manager's "Set up binder" (another instance, on a desktop) says what to run when pkexec is missing.

What went wrong on the phone with the 0a8d50ef deb (the user's report: the manager was tiny, setup failed and never asked for a password):
1. The runtime bundle the test install used was made from a prebuilts directory whose `android-root` and `system-apps` were symlinks into another
   checkout (`~/wt/ut-main/prebuilts` on Parallels); `runtime-bundle.sh` copied the links. Unpacked on the phone, `android-root` pointed at
   `/home/parallels/...` and `system-apps` was empty: `setup-runtime.sh` failed on `cp ... android-root/`, and since `Layout::detect` sets the runtime up
   for every command, every command died, `apklayer setup` included. Now the bundle script copies through links and checks its parts, unpacking refuses an
   incomplete bundle, an incomplete installed runtime counts as none (setup unpacks it again), and `setup` itself skips that automatic setup.
2. The manager talked to instance `default` (hard-coded), whose binder nodes a Halium phone never has (the CLI's own default there is `ut`): the first
   run showed "Binder devices: Missing for instance default" and offered "Set up binder", which runs pkexec (absent). `apklayer --version` now prints
   `defaultInstance` and the manager uses it.
3. The runtime setup and the installer ran `java` from PATH; the phone's Java is named in `~/.config/apklayer/env`: both now use the JVM apklayer resolves.
4. Scale: Lomiri leaves the Wayland output at scale 1 (720x1560) and tells its own apps the scale through `GRID_UNIT_PX=14` (a grid unit is 8 dp: 1.75);
   Slint's winit backend took scale 1, so the manager drew a desktop layout (navigation rail, 720 logical px wide) at desktop size. The manager sets
   `SLINT_SCALE_FACTOR=GRID_UNIT_PX/8` when Lomiri gives one (a user's own `SLINT_SCALE_FACTOR` wins): 411 logical px wide, the compact layout with the
   bottom navigation bar that the UI already had for widths under 600 dp.

First run: `apklayer setup` (docs/RELEASES.md): binder (checked), java (checked), runtime bundle from the release (`--bundle <file>` offline), providers
(graphics and camera = device), system apps. Desktop entries of installed apps carry `X-Lomiri-Touch=true`, which Lomiri's drawer needs to list a
non-click app (`liblomiri-app-launch` checks it), and the manager's entry too. Other automatic Halium pieces (formerly `env2.sh`): `cli/halium.cpp`
detects the device (`/android/system`, binderfs, `/vendor/lib64`), makes `ut` the default instance, seeds its `providers.toml`, sets `PULSE_SERVER` and
picks the PulseAudio audio HAL; `cli/binder.cpp` probes the host binder nodes.

## Choosing and sharing files on Ubuntu Touch (2026-11-05)
Ubuntu Touch has no xdg-desktop-portal FileChooser; apps take files from each other through Lomiri Content Hub (`lomiri-content-hub-service`,
`com.lomiri.content.dbus.Service` on the session bus). `bridge/portal/file-chooser.py` (the manager's "Choose file…", the Hardware page's file
options, `apklayer portal` and the apps' document pickers through HostFilePortal) falls back to it for `open` when the portal call fails:
- `content-hub.qml` beside it runs in Qt 5's `qmlscene` (`/usr/lib/qt5/bin/qmlscene`, package qmlscene-ubports; the Qt 5 `Lomiri.Content 1.3`
  module, qml-module-lomiri-content; the phone has no Qt 6 `qml` runner) with the stock ContentPeerPicker: handler Source, type All (File Manager,
  Morph Browser, Gallery, Camera, Music, Contacts on the X23). It prints the portal's `uri` lines, so no caller changed.
- Identity: the client library sends `$APP_ID` and the service trusts it from unconfined processes (it checks only AppArmor-confined ones against their
  profile, `Service::CreateImportFromPeer`). A manager started by lomiri-app-launch has `APP_ID=apklayer-manager`, inherited by the helper, which
  registers the import handler (`com.lomiri.content.handler.apklayer_manager`) the service delivers to; without APP_ID (a terminal) the helper uses
  `apklayer-manager`. On charge the service also calls `lomiri_app_launch_start_application(APP_ID)`: the manager's desktop entry is now
  `X-Lomiri-Single-Instance=true`, else every pick started a second manager window.
- Files: the source app's copy lands in `~/.cache/<APP_ID>/HubIncoming/<n>/<name>` (`Transfer::calculateStorePath`); the helper does not call
  `finalize()` (which deletes it). The manager removes that folder once the install ran; a file chosen for a hardware option stays (the option names it).
- The File Manager (filemanager.ubports, a click app) shows only Documents, Downloads, Music, Pictures and Videos without unlocking: put the .apk there.
- Share / "Open with" to apklayer: the deb installs the peer `/usr/share/lomiri-content-hub/peers/apklayer-share` (`{"destination": ["all",
  "documents"]}`; the file name is the receiving app's id) and the hidden Lomiri app `apklayer-share.desktop` (`apklayer-manager --share`), which
  opens the install dialog and runs `file-chooser.py import`: the same QML as the transfer's handler, printing the shared file.
  The manager's Wayland app id is `$APP_ID` when Lomiri sets one: Lomiri gives a window to the app its app id names, and a `--share` manager that
  said `apklayer-manager` left apklayer-share windowless, so Lomiri suspended it (SIGSTOP) before the transfer arrived. qmlscene takes its last plain
  argument as the file (others only warn), so the helper's words (`import`, `multiple`) go before the .qml file.
- Neither portal nor Content Hub (a desktop without a FileChooser backend): the install dialog lists the newest .apk files of ~/Downloads, ~/Documents
  and the home folder (one level), the Hardware page says to type the path. A desktop with a working portal is unchanged (the portal answers first).
