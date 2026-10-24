# apklayer — Architecture (M0 notes)

## The one-sentence plan
Boot real ART + real AOSP framework Java on a Linux distro, and replace only what Android
expects from *its* kernel/HALs with a thin bridge to standard Linux desktop APIs.

## Why Mac.apk's shape (and not ATL's)
- Mac.apk keeps the AOSP framework **as-is in Java** (~3k framework classes, thin
  `java_base_patch`/`jdk_unsupported_patch` overlays). Compatibility comes for free because
  apps talk to the real framework; maintenance is re-applying overlays on AOSP bumps.
- ATL re-implements framework classes natively (C++ `api-impl`), which caps app
  compatibility forever. Not our path.

## Process model (mirrors Mac.apk)
- `apklayer` CLI unpacks the APK → verifies → spawns `app_process` (one process group per app:
  app_process + zygote-like children; Mac.apk has `app_process_group.cpp`, 66 lines — that's
  the whole trick, no system_server initially).
- ART runs the app's dex with `framework.jar` on the boot classpath; `ActivityThread` is
  driven by our loader stub (Mac.apk: `com.macapk.loader.Boot.main(apkPath)`).

## Host bridge (the only code we really write)
| Android side          | Linux side                                   |
|-----------------------|----------------------------------------------|
| SurfaceFlinger/HWUI   | HWUI → EGL on host Mesa; present to Wayland (fallback X11) |
| Input (evdev via SF)  | libinput / wl_keyboard / wl_touch            |
| AudioFlinger          | PipeWire (or ALSA fallback)                  |
| /system partition     | our runtime dir (read-only) + per-app data   |
| bionic                | glibc (AOSP host builds already do this)     |
| SELinux, init, servicemanager | omitted; shims where a class insists |

No binder/SurfaceFlinger needed for the window path if we bridge HWUI directly — this also
sidesteps Ubuntu kernels lacking binder (M1/M2 need none).

## Build strategy ("write less, copy more")
- **Never build AOSP** (user rule). `runtime/` assembles **prebuilt** AOSP artifacts
  (candidate source: ATL release prebuilts; arm64 source to be decided with the user) into
  `runtime/out/<variant>/`.
- The los24 tree is read-only reference: copy code from it, never compile it, never touch
  its `out/`.
- Meson builds only apklayer's own glue (bridge/, cli/).

## ABI strategy
- ARM64 Linux hosts: arm64-v8a native libs load natively — zero translation. This is our
  primary target (the test VM is aarch64).
- x86_64 hosts: run pure-Java apps first; ABI translation later (own or berberis/box86-style).

## Milestones — see goal.md (M0..M4).
