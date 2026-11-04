# Halium devices: survey and the host binder context

Plan: `docs/PLAN.md` "Plan: hardware providers" (steps d and e). Owner's rule (2026-11-04): through the host context only the
vibrator, camera and media (codecs) HALs; never the sensors or GNSS HAL (one client each, held by sensorfw and the OS's location
service; on Halium those come through the OS services). Listing services (lshal, service list) is fine.

## The owner's phones (surveyed 2026-11-04, read only)
| | Ubuntu Touch 24.04 (`phablet@192.168.41.61`) | Sailfish OS 5.1.0.43 (`defaultuser@192.168.41.62`) |
|---|---|---|
| Device | Volla Phone X23 (`vidofnir`, MediaTek) | Jolla C2 (Reeder S19 Max Pro S, Unisoc) |
| Kernel | 5.10.209, aarch64 | 5.4.233, aarch64 |
| glibc | 2.39 (as Ubuntu 24.04: apklayer's Parallels builds run) | 2.30 (too old for apklayer's builds) |
| Android base | Halium 12: system API 32, vendor API 31 | Android 13: API 33 (system and vendor) |
| Container | LXC `android` (`/var/lib/lxc/android`, lxc-android-config) | none: droid-hal-init runs the Android services natively |
| Binder | binderfs: `binder`, `hwbinder`, `vndbinder` (0666), `anbox-binder/-hwbinder/-vndbinder` (0600, Waydroid's) | binderfs: `binder`, `hwbinder`, `vndbinder` (0666), `puddlejumper`, `hwpuddlejumper`, `vndpuddlejumper` (0600, Jolla AppSupport) |
| Kernel binder | CONFIG_ANDROID_BINDERFS=y, devices from CONFIG_ANDROID_BINDER_DEVICES, no `features/` dir | the same |
| Servicemanagers | servicemanager, hwservicemanager, vndservicemanager (uid system) | the same |
| AIDL HALs (/dev/binder) | vibrator (`IVibrator/default`, `IVibratorManager/default`), lights, power, memtrack, keymint, gnss, NNAPI shims; 45 names | lights, power, health, usb, keymint, radio, drm, face, sprd vendor ones; 84 names; no vibrator HAL |
| HIDL HALs (/dev/hwbinder) | camera.provider@2.4 (internal/0), media.c2@1.x, omx, sensors@2.0, gnss, allocator@4.0, composer@2.1-2.3, bluetooth, nfc, drm; 264 entries | camera.provider@2.4 (legacy/0), media.c2@1.x, omx, audio@7.x, gnss, allocator@4.0, composer@2.1-2.4 |
| Compositor | Lomiri on Mir (lomiri-system-compositor), Wayland `wayland-0` | Lipstick (QtWayland) on `/run/display/wayland-0` |
| Wayland buffers | `android_wlegl` v2, no `zwp_linux_dmabuf_v1` (globals: android_wlegl, qt_windowmanager, wl_compositor 4, wl_subcompositor, wl_seat 6, wl_output 3, wl_shell, zxdg_shell_v6, xdg_wm_base, wl_shm) | `android_wlegl` (libhybris `eglplatform_wayland`, server side in lipstick), no dmabuf |
| Sensors, location | sensorfwd (hybris), lomiri-location-serviced | sensorfwd (hybris), geoclue providers |

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
- **HIDL, libhwbinder + libhidl** (camera provider, media.c2 and OMX are HIDL on both phones): the same shape for `/dev/host_hwbinder`:
  libhwbinder 0001 (ProcessState/IPCThreadState/BpHwBinder per context, as above), 0002 (no TXN_SECURITY_CTX: our libhwbinder needs it
  too); libhidl 0001-0003, 0007 (getService(useHostHwBinder) through the host hwservicemanager, no waiting on it), 0004 (always renew the
  service manager), 0005 (keep the ABI), 0008 (the allow-list: use the same `host_hals.conf`, fqnames), 0006 (vendor passthrough paths;
  ours: bionic_translation's sphal namespace). Not started.
- **Allow-list generation**: the stack writes `host_hals.conf` from `providers.toml` (a domain set to `device` lists its interfaces and
  does not start its apklayer front); until providers.toml exists the file is written by hand or by the test.

## Tests
- `tests/vm/host-binder.py` (group `host-binder`): a second instance (`<instance>-dev`) runs its servicemanager and registers
  `apklayer.test.IHostEcho/default`; this instance finds it only through the host context, transacts on it (dump), and does not
  ask the host for an unlisted name.
- Ubuntu Touch phone: an `apklayer exec` of `servicetool` in an instance with `APKLAYER_HOST_BINDER=/dev/binderfs/binder` calls the
  device's `android.hardware.vibrator.IVibrator/default` (`getCapabilities`, a 50 ms `on`).

## Running apklayer on the Ubuntu Touch phone (2026-11-04)
- The Parallels build (Ubuntu 24.04, glibc 2.39) runs unchanged: runtime copied to `~/apklayer-dev/rt` (native, android-arm64/lib64,
  bt, deps), the repo with its meson build in `~/apklayer-dev/repo`, the environment in `~/apklayer-dev/env.sh`. Needed bionic_translation
  0036: the linker's default `/system/lib` and `/vendor/lib` are the device's 32-bit Android there, and every program crashed on
  `/system/lib/libc.so`. Our binaries need GLIBC_2.38: Sailfish (2.30) needs its own build.
- The instance `dev` uses binderfs nodes `binder-dev`, `hwbinder-dev`, `vndbinder-dev` (made with sudo, 0666; gone at reboot).
- Results: the instance's own servicemanager does not have the vibrator; through the host context the Android 12 device's
  `IVibrator/default` is found, `getCapabilities` answers, and `on(50 ms, null callback)` succeeds once our binder stability goes as a
  Category (with the plain Android 14+ word the A12 HAL refused the parcel with BAD_TYPE).
- Not yet: an app (JVM) on the phone: the window shell needs `android_wlegl` (no dmabuf there) and a JDK in the user's directory;
  the Java path is the same native lookup (ShadowHostServiceManager -> AServiceManager_checkService).
