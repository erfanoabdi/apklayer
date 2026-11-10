# Halium devices: survey and the host binder context

Plan: `docs/history/PLAN-2026-11-10.md` "Plan: hardware providers" (steps d and e). Owner's rule (2026-11-04): through the host context only the
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
- On a phone `/dev/binder` *is* the device's. apklayer's stack runs as a named instance on the userspace binder (docs/BINDER.md; with the
  kernel binder option its own `binder-<id>` etc. through `tools/host-setup/binder.sh <id>`, root once per boot), and the device is reached only
  through the host context (`/dev/binderfs/binder`, `/dev/binderfs/hwbinder`: the route leaves them to the kernel).
- The hosts are older than our libbinder (Android 16): Halium 12 and Android 13 write a binder's stability as a Category and number
  IServiceManager's methods differently (patch 0006 handles both, below).
- Sailfish OS (glibc 2.30) runs apklayer from RPMs built against Sailfish's own toolchain and libraries (below, "Building for Sailfish OS").
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
- Open: camera = device (cameraserver and HIDL provider listing, docs/history/PLAN-2026-11-10.md (e)); `sync_file_info returned NULL` (2026-11-05: these were the composer's present fences, eventfds without a render node; it now passes the composition's GPU fence, a Mali sync_file, and logs `present fences: ...` once; not yet checked on the phone, which was unreachable); the window on
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
| media | `device` (default on Halium; `providers set media apklayer` for GStreamer) | the host's Codec2 (30 MediaTek components, `hidltool codecs default`) and OMX stores found through the host contexts | works (2026-11-09; the default on Halium since 2026-11-09 when graphics = device too): `hello.nativeplayer` `red-h264-320x240.mp4` through `c2.mtk.avc.decoder` into the SurfaceView (`PLAYER PASS`, screenshot red; the HAL dequeues from the app's BufferQueue through the host context); NewPipe plays Big Buck Bunny 1280x720 through it; `hello.nativecodec -Dhello.device=true`: H.264 encode `c2.mtk.avc.encoder` 320x240 and its decode (`ENCODER PASS`, luma 81), AAC encode and decode through the device's software store (`c2.android.aac.*`: no MediaTek AAC); NewPipe 720p: 293% of a core for the whole instance (the HAL 37%) against 388% on the GStreamer path (libapklayer_codec2's openh264 122%) |
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
- With `providers set camera fake` a phone's portrait window gets the camera compat rotate-and-crop (cameraserver turns the bars a quarter, as for a portrait desktop window); hello.camera's Main checks the turned frame there, and the camera-reload check passes on both phones (2026-11-10). Since the rotation fix (2026-11-10, "Rotation on Ubuntu Touch") Lomiri's
  portrait phone gives the landscape-locked Main a landscape letterbox instead (no turn); the camera-reload check was not rerun there.

Other fixes of this pass:
- `bridge/hals/media/codec.c`: gst-droid's `amc*` decoders (`amcvideodec-c2mtkavcdecoder2`, the device's MediaCodec through libhybris) rank above the software ones and fail inside the stack's
  processes ("GStreamer encountered a general supporting library error"): they are ranked NONE (set `APKLAYER_GST_AMC=1` to keep them). H.264 then decodes in software (openh264).
- Audio: the phone has PulseAudio and no PipeWire daemon (the PipeWire HAL library had no server to talk to: `AHAL_Stream: Failed to initialize the driver`). `bridge/hals/audio/pulse.c` is the
  same `apklayer/audio.h` over libpulse-simple (`meson -Daudio_backend=pulse`, `auto` picks it when PipeWire's library is missing). The stack's `XDG_RUNTIME_DIR` of this test instance is not the
  session's: `PULSE_SERVER=unix:/run/user/32011/pulse/native` in `env.sh` (a real user's instance has the session's directory).
- `providers set media device`: new domain (`android.hardware.media.c2`, `android.hardware.media.omx` listed, the stack's own Codec2 store is not started and its vintf fragment not staged); the stack loads
  `media.c2.hal.selection=hidl` last (apklayer.prop says aidl; a Halium 12 vendor's Codec2 is HIDL) and serves the device's `/vendor/etc/media_codecs*.xml` from the instance's root (the shared root's name apklayer's own components).
  Two causes kept it from working (2026-11-09): (1) the fixture: `red-h264.mp4` is 64x48 and the device's `c2.mtk.avc.decoder` declares `size min="64x64"`, so
  MediaCodecList had no match (no `Allocating component` line: nothing was created; the device's `c2.android.avc.decoder` is not in its codec xml either):
  `red-h264-320x240.mp4` is the phone's fixture. (2) apklayer.prop's `debug.stagefright.c2-poolmask` (BLOB | BUFFERQUEUE: gralloc BLOB linear blocks) made
  the input a gralloc handle; the Android 12 HAL's `_C2BlockFactory::CreateLinearBlock` takes only an ION/dmabuf handle, read it as a graphic block and refused
  every work (device logcat: `Codec2-types: Invalid rect info in C2GraphicBlock`, our side `queueCSDInputBuffer failed w/ error -2147483648`).
  `media-device.prop` now also sets the pool mask to Android's default (ION | BUFFERQUEUE, 327680). Nothing else was needed: callbacks (IComponentListener,
  the output IGraphicBufferProducer, bufferpool) work over the host context. The device's logcat: `sudo lxc-attach -n android -- /system/bin/logcat -d -T '<UTC time>'`
  (pipe it through `cat`: lxc-attach makes a redirected file root's). OMX was not needed (found, not tried). The Quintus (Sailfish) works too (2026-11-09, below: it needed MediaTek's mms HAL started). Not checked: `graphics = apklayer`.
- AAC: faad (the host's AAC decoder here) keeps its last frame ("still 1 frames left after draining"); the Codec2 AudioDecoder ended such works as corrupted: now empty.

How to run each check (on the phone; `. ~/apklayer-dev-cam/env.sh` gives `$A`): `ut-cam.sh [app] [ms]` (camera = device, the app's `[cam]` lines and cameraserver's log tail), `ut-shots.sh <pkg>` (run, screenshot, stop),
`ut-player.sh red-h264.mp4|red-tone.mp4` (`PLAYER PASS`, `player.png`), `ut-audio.sh` (the audio app's lines), and `apklayer run hello.hardware` with the default providers for vibrator, gnss and sensors.
Test apps: `tests/apps/build.sh hello-camera hello-hardware hello-native-player hello-native-audio` on the Mac, `apklayer install` on the phone. Open Camera's API: `~/apklayer-dev-cam/ocprefs.py <camera id>` edits its preferences (camera2 or the old API).

## Rotation on Ubuntu Touch (2026-11-10)
- Lomiri turns its whole scene in QML (OrientedShell) when the focused app allows the orientation (`X-Lomiri-Supported-Orientations` of its
  desktop entry, all four when absent; "unknown.qtmir" windows allow all) and the user's rotation lock
  (`gsettings com.lomiri.touch.system rotation-lock`) is off. A Wayland client sees only its toplevel configures: maximized at first, then
  `[activated]` without maximized, then the turned size as a plain configure (1560x678 on the X23); wl_output stays 720x1560, transform 0.
  The shell takes every configure's size in the mobile style; the app relaunches on a turn (layout-land), a fixed-orientation activity keeps
  its shape (letterboxed at the window's top left).
- Turning the phone from a script (`tools/halium/ut-rotate.sh DIR PKG STEP...`, sudo password on stdin): `tools/halium/fake-accel.py` makes a
  uinput accelerometer, sensorfwd runs with its evdev accelerometer adaptor (`accelerometeradaptor`, `[accelerometer] input_match`) from a
  copied conf.d bind-mounted in a private mount namespace, the rotation lock off; each step sets the gravity (0,1000,0 portrait; 1000,0,0 and
  -1000,0,0 the two landscapes; 0,-1000,0 is upside down, which Lomiri does not take), Lomiri logs `qtmir.sensor: ... new orientation=...` in
  the user journal. Afterwards it puts Lomiri back to portrait, the lock as it was and the real sensorfwd back (Lomiri's QtSensors reconnects).
  Lomiri's sensor runs only while the display is on (the script wakes it and enters the PIN, then turns it off again). An app in the instance
  that asks sensorfwd for the accelerometer crashed this sensorfwd (a QList assert in the evdev adaptor): use `providers set sensors fake` there.
  No Lomiri screenshots (they leave an editor overlay): the app's own picture (`apklayer screenshot`) and the shell's WAYLAND_DEBUG lines.

## Ubuntu Touch: the click (2026-11-09, the way to install)
Ubuntu Touch 24.04 (arm64) installs apklayer as a click, `apklayer.erfanoabdi_<version>_arm64.click` from the release (docs/RELEASES.md "The click";
the OpenStore once it is accepted). No root, nothing on the read-only rootfs, and an OTA update leaves it alone (clicks live in
`/opt/click.ubuntu.com`, a bind mount of the user data partition). Install a test build without the store (UT 24.04 has no `pkcon`; the system
bus's click service installs for the user, no password):
```sh
gdbus call --system -d com.lomiri.click -o /com/lomiri/click -m com.lomiri.click.Install /home/phablet/apklayer.erfanoabdi_<version>_arm64.click
```
Then open Apklayer from the drawer (the manager runs `apklayer setup`: the runtime bundle into `~/.local/share/apklayer`, providers, system apps,
the always-on `ut` instance). The click brings its own Java (a JDK 21 image with class data sharing); nothing else from the rootfs beyond the image's
own packages (`zip` is not needed: setup uses the image's `jar`). The CLI for a terminal: `/opt/click.ubuntu.com/apklayer.erfanoabdi/current/usr/bin/apklayer`
(an alias is handy). Remove: `gdbus call --system -d com.lomiri.click -o /com/lomiri/click -m com.lomiri.click.Remove apklayer.erfanoabdi`
(the data in `~/.local/share/apklayer` stays; `apklayer remove-all --yes` first removes it, the launchers and units too).

What the click is (decided by the owner, docs/history/PLAN-2026-11-10.md "Plan: Ubuntu Touch click package"): unconfined (AppArmor template `unconfined`: the apps need
the device's binder and GPU nodes, the launchers in `~/.local/share/applications` and the always-on unit in `~/.config/systemd/user`), the JDK
inside, no per-app sandbox (it needs root's newuidmap setup), Ubuntu Touch 24.04 only (the noble build as it is).
Paths: the click's directory `/opt/click.ubuntu.com/apklayer.erfanoabdi/<version>/` is replaced and removed by each update, so the launchers, the
always-on unit and the runtime's links name `.../apklayer.erfanoabdi/current/...` (the click's own link); a launcher or unit naming a CLI that is
gone (the deb's, before the switch) is written again by the next `run` or `list` (the manager runs `list`).

From the deb to the click (done on the Volla X23, 2026-11-09; apps, data, runtime and the `ut` instance's settings stay as they are, all in `/home`):
1. `systemctl --user stop apklayer-instance@ut; apklayer --instance ut stop`
2. `sudo mount -o remount,rw / && sudo dpkg -r apklayer && sudo mount -o remount,ro /` (`apt remove` too; `systemctl disable apklayer-binder`
   if it was enabled: the userspace binder needs no unit)
3. Install the click (above). A `APKLAYER_NATIVE_JAVA=` line in `~/.config/apklayer/env` (the deb without its Java dependency) would still win over
   the click's JDK: remove it.
4. Open Apklayer once, or run `apklayer setup` (the runtime is set up again for the new package half, ~1 minute) and `apklayer list`: the launchers
   and the always-on unit then name the click's CLI. `systemctl --user start apklayer-instance@ut` (or the next login) starts the instance.

Checked on the Volla X23 (2026-11-09, 0.4.10 test clicks; the owner's apps and data in `~/.local/share/apklayer` from the 0.4.9 deb):
- install without root through `com.lomiri.click`; `apklayer setup --bundle apklayer-runtime-arm64-0.4.9.tar.zst` (the existing runtime set up again,
  1 min; and a fresh data home: bundle unpacked, runtime set up, 10 system apps, 3 min 48 s); every JVM runs the click's JDK image with CDS on;
- drawer launches (`lomiri-app-launch apklayer-<package>`) of NewPipe and Calculator: each in its own scope, from the click's CLI; SurfaceFlinger
  pictures of Calculator and NewPipe's start list (thumbnails from the network);
- the manager from the drawer: `aa-exec-click` with the click's profile, unconfined, and it reaches the click's CLI (a launcher made stale by hand was
  rewritten by its `list`);
- the always-on `ut` instance: across a reboot (it starts at login; the first two tries before Lomiri's compositor fail and the unit's Restart= brings
  it up 13 s later, as with the deb) and across two click updates with it running (0.4.10 -> 0.4.11 -> 0.4.12: the old directory is removed at
  once, the running hub stays up on its deleted files, the next drawer launch runs the new version and draws);
- graphics, camera and media = device: Open Camera's preview from both cameras (camera = device), a NewPipe video decoded by
  `c2.mtk.avc.decoder` with AAC through `c2.android.aac.decoder` (media = device), apps on the Mali GPU (graphics = device).

### Lomiri's app spread and audio from the click (2026-11-10)
- Which app a window is: qtmir (`ApplicationManager::authorizeSession`) gives a Wayland client to the app its pid was launched as (the
  lomiri-app-launch unit, else `DESKTOP_FILE_HINT` in its environment, else the catch-all `unknown.qtmir`, the spread's "Unknown app"). The
  shell is one client for every app: 0.4.14's shell was "Unknown app" (or, started by a drawer launch, tied to that first app) and every
  window landed on that card. qtmir has one way for a client of many apps, the Waydroid one: `DESKTOP_FILE_HINT=Waydroid` authorizes it
  without an app, and each surface goes to the app its xdg app id names (`findApplicationWithSurface`, which TopLevelWindowModel uses for
  the spread). The CLI starts the shell that way on Lomiri (XDG_CURRENT_DESKTOP or `$XDG_RUNTIME_DIR/mir_socket`), without the starter's
  APP_ID/DESKTOP_FILE_HINT, and the shell's app id there is the desktop entry's name (`apklayer-<package>`, `APKLAYER_APP_ID_PREFIX`).
- Closing from the spread: qtmir asks the window to close and, if it is still there a few seconds later (`onCloseTimedOut`), Mir's
  force_close SIGTERMs the client: the shell, with every app's windows (seen: hello.nativeaudio's card swiped away, the shell ended 4 s later).
  The shell now destroys a window as soon as the compositor closes it (`window_close`); the app ends on its CLOSE as before.
- Audio: the click is the noble deb's tree, and the deb was built without libpulse-dev, so it had no `libapklayer-audio-pulse.so`; the phone
  has no PipeWire daemon and `apklayer providers` showed audio `(none: off)` (audio.log: "no sound server answers ... playback is silent").
  debian/control builds it now. Checked with that library in a test runtime: hello.nativeaudio's sink-input (`btload`, "apklayer") on
  `sink.primary_output`, 3 s of its monitor at RMS 2778 (the 440/880 Hz tone), RATE PASS 47953 frames/s.
- Checking on the phone: Lomiri's launcher items list the apps with windows (`gdbus introspect --session -d com.lomiri.Shell -o
  /com/lomiri/Shell/Launcher`: `unknown_2Eqtmir` is the bug), and `com.lomiri.Shell.Debugging.SetLoggingFilterRules` with
  `qtmir.applications.debug=true` and `toplevelwindowmodel.debug=true` (rules separated by newlines, not `;`) logs each window's app to the
  journal. Take app pictures with `apklayer screenshot` (SurfaceFlinger), not Lomiri's Volume Up + Down: each Lomiri screenshot opens
  its screenshot editor over everything (one editor, `screenshotEditor` in Shell.qml). Close it with its trash icon at the top left
  (40,94 in portrait), or by locking the screen (the power key), which hides it. Several screenshots taken in a row while the phone was
  busy left it unresponsive (2026-11-10): the owner asked for no Lomiri screenshots from agents.

## The apt install on the phone (2026-11-05; replaced by the click)
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
| the `ut` instance's binder nodes (`binder-ut`, `hwbinder-ut`, `vndbinder-ut`, 0666) | none needed since 0.4.9 | the instance runs on the userspace binder (docs/BINDER.md); the kernel's is an option (`apklayer binder kernel`): then `apklayer-binder.service` (shipped disabled since 0.4.9; enabled by an older package and kept on upgrade) makes them for the ids in `/etc/apklayer/instances` (default `ut`); the device's own `/dev/binder` is never touched |
| another instance's nodes | kernel binder only | `echo <id> \| sudo tee -a /etc/apklayer/instances && sudo systemctl enable apklayer-binder && sudo systemctl restart apklayer-binder` (the command the CLI and the manager print); kept over reboots |
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

## The on-screen keyboard on both phones (2026-11-09)
Lomiri and Lipstick offer no Wayland text-input protocol (their globals: CHANGELOG 2026-11-09); both keyboards are maliit-server 2.3.0 plugins
reached over Maliit's D-Bus protocol, which the shell speaks without zwp_text_input_v3 (`bridge/shell/maliit.c`). Facts from the check:
- The server's peer-to-peer address comes from the session bus (`org.maliit.server`, `/org/maliit/server/address`, property `address`):
  `unix:path=/run/user/32011/maliit-server` on Ubuntu Touch, `/run/user/100000/maliit-server/dbus-socket` on Sailfish. gdbus's `--address`
  cannot talk to it (it says Hello to a bus); GDBus's peer connection (`G_DBUS_CONNECTION_FLAGS_AUTHENTICATION_CLIENT`) can.
- Lomiri's keyboard sends a word as preedit and commits it with the space; Backspace after a committed word is three Backspace key events and
  the word again as preedit. Sailfish's commits each letter and sends Backspace as key events. Enter is Qt's Key_Return on both.
- What the shell sends (2026-11-10), as Qt's Maliit context does: the whole widget state on every change (the server replaces its map):
  `contentType` (free text 0, number 1, phone 2, email 3, URL 4), `hiddenText`, `predictionEnabled`, `correctionEnabled`,
  `autocapitalizationEnabled` from the field's EditorInfo, `surroundingText`, `cursorPosition`, `anchorPosition`, `hasSelection` (not for a
  password), `toolbarId` 0; the Enter key's label through attribute extension 0 (`registerAttributeExtension(0, "")` once,
  `setExtendedAttribute(0, "/keys", "actionKey", "label", <s>)`): both keyboards read it as `actionKeyOverride`. Lomiri's Backspace after a
  committed word takes the word back into preedit from what it remembers: without the cursor position a Backspace after a cursor move or with a
  selection deleted the wrong text. Swiping the keyboard down calls `imInitiatedHide`; `showInputMethod` brings it back.
- hideSoftInputFromWindow (2026-11-10): `hideInputMethod` alone hides Lomiri's keyboard, but Lipstick's stays on screen (the server reports a
  0x0 area all the same); with `focusState` false first (as for a focus loss) both hide it. The shell keeps the field and sends the whole state
  again on the next show.
- The area (`updateInputMethodArea`): Lomiri animates it (about ten updates, 720x129 to 720x597, its y relative to the window's 1508 px
  height); Lipstick sends 1080x868 once.
  Apps get it as the IME's insets (2026-11-10), not the navigation bar's: CONFIGURE's keyboard field (geometry.c), `NavigationBarInset.setIme`,
  the IME source in every window's InsetsState (robolectric 0027); the window's softInputMode does the rest (adjustResize shrinks the
  content, adjustPan pans to the focused view). The navigation bar inset stays the bar's (84 px on the X23, 135 px on the Quintus) and the bar
  stays under the keyboard. Termux's extra-keys row sits right above the keyboard on both phones.
- Checking it: the screen on and unlocked first (the locked screen's keypad takes the taps, and Lipstick's screenshot of a locked phone is
  white). Ubuntu Touch: the power key through uinput (`phablet` is in plugdev), the PIN on Lomiri's keypad, a screenshot with Volume Up + Down
  (`~/Pictures/Screenshots`, then always the editor's trash icon at 40,94 in portrait: it stays on screen otherwise; prefer `apklayer
  screenshot`, see "Lomiri's app spread" above). Sailfish: `mce req_tklock_mode_change unlocked` brings the keypad, the code
  and its accept key through uinput as root (`devel-su`), Lipstick's `saveScreenshot` writes only below `~/Pictures`.
- Hardware keyboards (2026-11-10): Lipstick sends its XKB keymap in an fd whose offset is at the end (read() gives 0 bytes); the shell maps
  it (MAP_PRIVATE) and falls back to xkbcommon's default keymap when none comes. A uinput keyboard types into apps on both phones.
  A real Bluetooth keyboard too (2026-11-10): the x86 PC as an LE HID keyboard (HOGP, `tests/vm/bt-keyboard.py <seconds> <fifo>`, each line
  written to the fifo is typed), paired from the phone with `bluetoothctl --agent NoInputNoOutput pair <PC>` (Sailfish as root, Bluetooth
  powered through connman's `net.connman.Technology.SetProperty Powered`; Ubuntu Touch as phablet). Both phones' BlueZ (5.76, 5.85) make it a
  uhid input device (`Bus=0005`), reconnect it by themselves when the PC advertises again, and the compositors give it the seat's one keymap
  (no second keymap event). It also types the lock code into Lipstick's keypad and Lomiri's greeter (Lomiri needs the screen woken first:
  the power key through uinput).
  A shell of one's own beside the running `ut` one: `APKLAYER_BUILD=<dir with bridge/shell/apklayer-shell and the installed tree's other
  parts linked>` and an own `XDG_RUNTIME_DIR` (the shell is one per runtime directory), `WAYLAND_DISPLAY` by absolute path.

## apklayer on Sailfish OS (Volla Phone Quintus, 2026-11-07)
Route (2026-11-07, replaces the first sysroot tarball with its own glibc 2.39, which the owner rejected): real RPMs built against Sailfish OS's own
toolchain and libraries, below. The Android runtime bundle stays per user (`apklayer setup`), as with the deb.

### Building for Sailfish OS
How Sailfish software is built: the Sailfish SDK (`sfdk`, formerly `mb2`) and the Platform SDK both run `rpmbuild` on a spec in `rpm/` inside a
*target*, the release's rootfs for one CPU, entered through scratchbox2 from an i486 SDK chroot (sb2 runs the target's binaries under qemu). CI does
the same with community Docker images of the Platform SDK (coderus/sailfishos-platform-sdk with `mb2 -t <target> build`, the
`coderus/github-sfos-build` action, R1tschY/docker-sailfishos-sdk). OBS (build.sailfishos.org, where Jolla and Chum build) is a hosting and
distribution choice, not a requirement: **no OBS server is needed** to build RPMs; it would only matter to publish a repository (Chum) users add.

What we use: the Platform SDK's own aarch64 target (`Sailfish_OS-5.0.0.62-Sailfish_SDK_Target-aarch64.tar.7z` from releases.sailfishos.org/sdk/targets,
md5 checked, `rpm/sources`), imported as an aarch64 Docker image (`rpm/Dockerfile`, image `apklayer-sailfish-build:5.0.0.62`) and run natively: the
Mac (OrbStack) and GitHub's `ubuntu-24.04-arm` runners are aarch64, so neither sb2 nor qemu is needed; the compiler, rpmbuild and every library
come from Sailfish's own repositories (zypper in the container). Toolchain: gcc 10 (C++20 with `-std=c++20`), Meson 1.4, glibc 2.30, kernel headers
3.18. `sh tools/release/sailfish-rpm.sh <version> <prebuilts checkout> <out dir>` builds everything; downloads land once in
`~/.cache/apklayer/sailfish`, the Java libraries come from `~/.cache/apklayer/deps` when present. The container is `--rm` (nothing keeps running);
`docker rmi apklayer-sailfish-build:5.0.0.62` frees the image. CI: job `sailfish` in `.github/workflows/release.yml` (the `*.aarch64.rpm` release assets).

RPMs (`rpm/*.spec`): `apklayer` (meson `--prefix=/usr -Dandroid_runtime=false`, like debian/rules; no apklayer-manager: Slint's prebuilt needs a newer
glibc) and `apklayer-devel`, plus what Sailfish 5.0's repositories and Chum lack: `temurin-21-jdk` (Adoptium's aarch64 JDK, built against glibc 2.17,
repackaged unchanged at `/usr/lib/jvm/temurin-21-jdk`: building OpenJDK from source in the target is hours of work for the same bits),
`libmd`, `libbsd`, `libunwind` (built from source). Sailfish-specific changes the tree needed: libdl linked explicitly (glibc < 2.34), linux_compat.h
for the new mount API and pidfd_open, SYS_statx, bionic_translation compat patches (no `Elf64_Relr`, gcc 10's `-std=c++20`), and the runtime
bundle unpacked as `zstd -dc | tar -x` (BusyBox tar has no `--zstd`; gnu-tar conflicts with busybox-symlinks-tar).

Install (root): `pkcon install-local apklayer-*.aarch64.rpm temurin-21-jdk-*.rpm libbsd-*.rpm libmd-*.rpm libunwind-*.rpm` (unsigned local RPMs; zstd and
elfutils-libelf come from Jolla's repositories); `apklayer-binder.service` starts. As defaultuser: `apklayer setup` (no wget on Sailfish: `--bundle
<apklayer-runtime-arm64-*.tar.zst>`). Space (2026-11-09): the Quintus' home is on the 6.8 GB system partition (`/dev/mapper/system_a`), `/userdata`
(ext4, exec allowed, 225 GB) is separate, and a runtime takes ~1.6 GB per version (an update holds two plus the bundle). The RPM's `%post`
makes `/userdata/apklayer` (mode 1777, kept when it exists); the first `apklayer setup` on a home file system below 16 GB, with that directory
writable on another file system, moves `~/.local/share/apklayer` to `/userdata/apklayer/<user>/share` and leaves a symlink (cli/datamove.cpp).
An existing install is never moved by itself: an update that does not fit fails with the space it needs and `apklayer setup --move-data
/userdata/apklayer/<user>/share`. A layout made by hand (the 0.4.9 phone: `~/.local/share/apklayer` -> `/userdata/apklayer/share`) is used as it
is. Ubuntu Touch needs none of this: `/home` is on userdata already. Then `apklayer providers set camera device` and `vibrator device` (setup reported them automatic, but the instance's host_hals.conf
held only graphics until set: open). `cli/halium.cpp` detects the phone as on Ubuntu Touch (instance `ut`, PulseAudio from the session's runtime dir);
the providers probe sensorfw, geoclue1, PulseAudio, the device's camera and vibrator.

What differs from Ubuntu Touch, and what changed:
- Lipstick has no xdg-shell (wl_shell only), wl_compositor 3, wl_seat 3, no subcompositor: `bridge/shell/wlshell.c` makes a window a wl_shell toplevel laid out
  maximized at the output's size and active (Lipstick sends no configure); damage falls back to `wl_surface.damage` on v3. Without subsurfaces the
  navigation bar is a layer of the app's virtual display (2026-11-09): the shell draws it as before (navbar.c) and sends its pixels as a memfd
  (`NAVBAR_PIXELS`) to the display's owner, which offered it (`NAVBAR_LAYER`, `apklayer_display_set_navbar_callback`); libapklayer_vdisplay puts
  them in a layer on top of the app's layer stack at the bottom edge, so SurfaceFlinger composes it into every frame (and `apklayer screenshot`
  shows it). Touches on the window's bottom `nav_h` pixels are the bar's; the app gets the bar's inset as on other compositors. The pointer on that
  strip still goes to the app (Lipstick is touch only). Lipstick's own extensions give no second surface inside a window (qt_surface_extension and
  alien_manager, Jolla's AppSupport windows, are window management), but qt_surface_extension's `lower` is Home: jolla-home's
  `onWindowLowered` hides an application window to the home screen, and the app stops (minimized); `raise` (`apklayer run` of the running app)
  and its `onscreen_visibility` (shown again) resume it. Frames go as the device's gralloc buffers over `android_wlegl` (`[shell] gralloc buffer 1: 1080x2400 stride 1088`).
  The navigation bar has Back only since 2026-11-09 (Home is Lipstick's own gesture); checked on the Quintus with the tree's shell.
- Lipstick's switcher (2026-11-09): all apklayer windows are one client's (the session shell's pid), so Lipstick's usual match of a window to its
  launcher entry (the client's pid against the entry's Exec) finds nothing. Entries with `X-apkd-packageName` (Jolla's AppSupport key) are matched
  by the window's wl_shell class instead (lipstick's `isWindowForLauncherItem`; Switcher.qml's `windowIndexOf`): app entries carry it (the
  package), and the shell sets the class to the package and the title to the app's name. Side effects: Lipstick's uninstall of such an entry and
  its removal go to com.jolla.apkd, absent here (they fail or are ignored; uninstall did not work for these entries before either). Entries keep
  `X-Nemo-Single-Instance=no` (invoker's lock is per Exec binary). A cover's close sends qt_extended_surface.close (QtWayland 5.6
  destroySurface on a surface with an extended surface; without one Lipstick would destroy the surface by force, an error for the whole shell):
  the app gets the desktop title bar's close and ends (test input `TEST_CLOSE`). Needs a human check: the covers (titles, icons, one per app) and
  tapping and closing them, with the phone unlocked.
- The system's programs (glibc 2.30) cannot load apklayer's LD_PRELOAD (needs GLIBC_2.34): the device's `/usr/bin/getprop` (cli `hostToolEnv()`) and the hub's
  `setsid` (HubStartup) start without it now. Other host programs an app starts (`sh -c` overrides, Runtime.exec of a system binary) still inherit it: open.
- glibc 2.30 has no stat or mknod functions of its own (stat() is an inline `__xstat` call; 2.33 added them): the path layer's LD_PRELOAD wraps
  `__xstat`/`__lxstat`/`__fxstatat` too (the Temurin JDK reaches files through them: java.io.File on /data/data/<pkg>), and its stat, lstat,
  fstatat and mknod wrappers call the kernel where glibc has no symbol behind them (an Android program's stat64 jumped to 0). libhybris's
  linker prints each vendor library it does not find on stderr when btload's constructor looks up the GL extension stubs through libEGL.so.1:
  that stderr is muted for the lookups (it went into programs' output: Termux's `id -u 2>&1`). Termux installs its bootstrap and runs since
  2026-11-10 (CHANGELOG).
- `ro.hardware.egl` is `meow` (MediaTek's layer over Mali): libGLES_meow crashes in SurfaceFlinger during its dlopen (a jump to an unmapped address under
  libdl_bio's constructor call); the instance runs with `apklayer prop set ro.hardware.egl mali`: SurfaceFlinger reports Mali-G68 MC4, OpenGL ES 3.2. Open.
- The prebuilts (75a684a6) carry libraries older than the Halium work of 2026-11-05: libhidlbase without libhidl 0002/0003 ("gralloc-mapper is missing",
  SurfaceFlinger aborted), libbinder without 0007 (AudioPolicyService refused the hub, uid 100000), libapklayer_vdisplay without the android_wlegl path
  (frames went as shm), libapklayer_cameraserver. The phone runs the tree's current outputs of these four; the lock and the prebuilts branch need them
  (libapklayer_codec2, libapklayer_egl, libgui, libvulkan, libstagefright, libmedia_omx differ from the tree too).
- Lipstick's screenshot (`saveScreenshot`) needs the `privileged` group (`devel-su -p` works over ssh with the password on stdin); with the phone
  locked and its display off the picture is blank, otherwise the lock screen. App pictures are SurfaceFlinger's (`apklayer screenshot`); seeing the
  window on Lipstick unlocked needs a human check.
- media = device (the Halium default, 2026-11-09) works on the Quintus: hello.nativeplayer through `c2.mtk.avc.decoder` (`devel-su` then
  `/system/bin/logcat -d`), PLAYER PASS. It needed MediaTek's `vendor.mediatek.hardware.mms` HAL: Mali samples the decoder's YUV buffers through
  /vendor/lib64/libgpu_aux.so, which asks for IMms and crashed SurfaceFlinger without it. The HAL is `oneshot disabled` in the vendor's init
  (started on demand); graphics = device lists it for the host context, and the stack asks the device's init to start the declared HIDL HALs it
  lists (`setprop ctl.interface_start`, allowed for the desktop user here). Not tried: other videos, NewPipe, the encoder.

Results from the RPMs (2026-11-08, `pkcon install-local`, then `apklayer setup --bundle`: runtime unpacked, 10 system apps installed): UiBench
installs and draws (SurfaceFlinger's screenshot 1080x2400 of its list; the shell sends device gralloc buffers to Lipstick: `[shell] gralloc buffer 0:
1080x2400 stride 1088`); `sf-run.py` on the phone runs an app, takes the screenshot at its sixth frame log line and stops the instance. Fixes it took:
bionic_translation 0043 (glibc 2.30's sigemptyset wrote 128 bytes over bionic's 8-byte sigset_t: libandroidfw's stack guard aborted describe) and
compat/glibc-before-2.38-libc. Open: libdl_bio links the host libEGL.so.1, which on Sailfish is libhybris's, so every Android process maps
libhybris (its linker and TLS handling; "library libEGL.so not found" per unresolved gl* symbol); Lipstick's own screenshot returned an error.
The four libraries newer than the prebuilts (below) were again copied from the tree's los24 outputs into the runtime.

Earlier results (sysroot tarball, instance `ut`; `~/apklayer-sfos/sf-shots.sh <pkg> <secs>` on the phone: run, both screenshots, stop): UiBench draws (device gralloc buffers to
Lipstick); NewPipe draws its UI (Trending stayed empty); Open Camera opens the device's camera 0 through cameraserver over the HIDL provider (`ID:0`, no
CameraControllerException; the preview was dark, the phone lying on the desk: not judged). Not run on this phone: audio, sensors, GNSS, vibrator, media.
