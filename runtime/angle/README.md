# ANGLE Android libraries

These x86_64 and x86 Android EGL/GLES libraries are built from Google ANGLE revision
`f0d596b6f9e3dfe7a068786790516c035b1dfde4` for Android API 37. The source includes apklayer patches `patches/angle/0001-use-host-safe-tls-for-vulkan.patch`, `0002-disable-nvidia-android-deferred-swapchain.patch`, `0003-disable-nvidia-android-present-fences.patch`, and `0006-use-saved-egl-thread-during-cleanup.patch`. See `docs/PATCHES.md`.

APKLayer requests GLES 3.2 exposure by setting `exposeES32ForTesting` in the host's `ANGLE_FEATURE_OVERRIDES_ENABLED` environment variable and Android's `debug.angle.feature_overrides_enabled` property.

ANGLE is packaged for x86 and x86_64 Android guests on x86_64 Linux hosts. There is no ARM64 ANGLE build or runtime payload.

Meson packages the files under `runtime/angle/vendor` into `/usr/lib/apklayer/angle/vendor`. Runtime setup links them into `/system/lib` and `/system/lib64`, where Android 17's EGL loader looks for the `angle` suffix when `ro.hardware.egl=angle`.

APKLayer selects ANGLE's Vulkan backend when Vulkan presentation is available. Vulkan off, an unavailable presentation driver, and a missing Vulkan device all select APKLayer's native GLES path.

## Linux host libraries for GBM transfers

The x86_64 Linux ANGLE EGL/GLES libraries serve both the host graphics HAL's CPU buffer transfers and, in ANGLE mode, SurfaceFlinger's compositor EGL and GLES. Android app GLES continues to use the Android-target libraries above. The host build uses the system Vulkan loader. The Vulkan backend imports Android DMA-BUFs through GBM/EGL, samples them on the GPU, and uploads CPU pixels through GLES.

Meson packages the host files under `/usr/lib/apklayer/angle/host`. The CLI passes the EGL path to the graphics HAL and the SurfaceFlinger process when ANGLE is selected. The compositor's Android EGL shim supplies Android native-buffer compatibility and forwards EGL/GL to Linux ANGLE. Linux ANGLE uses the same source revision with `patches/angle/0004-cache-linux-dma-buf-modifiers.patch` to share DRM modifier lists and `patches/angle/0005-fix-linux-rgb565-dma-buf-channel-order.patch` to preserve RGB565 channel order in DMA-BUF imports.

ANGLE is licensed under the BSD 3-Clause license. The binaries are built from the Google ANGLE source, not copied from the Waydroid NVIDIA Android 13 image.

## Build settings

Sync a Google ANGLE checkout at `patches/angle/REVISION` with its Android dependencies, then run APKLayer's `patches/apply angle <ANGLE checkout>` before generating the GN builds. Use these arguments in each output directory's `args.gn`.

```gn
is_debug = false
is_component_build = false
angle_enable_vulkan = true
angle_enable_gl = true
angle_expose_non_conformant_extensions_and_versions = true
angle_libs_suffix = "_angle"
```

Add the platform settings for each build.

| Output directory | Additional GN arguments | Ninja targets |
|---|---|---|
| `out/Android64` | `target_os="android"`, `target_cpu="x64"`, `android_ndk_api_level=37`, `angle_enable_context_mutex=false` | `libEGL libGLESv2 libGLESv1_CM` |
| `out/Android32` | `target_os="android"`, `target_cpu="x86"`, `android_ndk_api_level=37` | `libEGL libGLESv2 libGLESv1_CM` |
| `out/LinuxHost` | `target_os="linux"`, `target_cpu="x64"`, `angle_use_custom_libvulkan=false`, `angle_build_tests=false` | `libEGL libGLESv2` |

Run `gn gen` and `autoninja -C` for each output directory. Copy the resulting `lib*_angle.so` libraries to `vendor/lib64/egl`, `vendor/lib/egl`, and `host`, respectively.

## SHA-256

| ABI | Library | SHA-256 |
|---|---|---|
| x86_64 | `libEGL_angle.so` | `ae78e0ac613c94f3426dd55b46429c5d68fe662c70ea89f7f4c56b9908ad1a06` |
| x86_64 | `libGLESv1_CM_angle.so` | `63c2e858fb1d99aba9e896bfa1f7c31b789ec6d20da71fec74a64751bd213999` |
| x86_64 | `libGLESv2_angle.so` | `4a6b70d4e4e225c21b09407382bb1fcd53a381440a0747383f2a48cb0c281394` |
| x86 | `libEGL_angle.so` | `fd23048764fe33787f56cfc9927d7134e6a317c5924596785a57a347c36ebfc6` |
| x86 | `libGLESv1_CM_angle.so` | `e7af29754e1cd5038be6507e19968120ce2eaacf333fa63658ea18c513e9274a` |
| x86 | `libGLESv2_angle.so` | `ba3c2633a017d70d70a384808c30bb448dfc7bbdf2347a2d36c6bbebfa4ad4f9` |

Linux host SHA-256:

| Library | SHA-256 |
|---|---|
| `libEGL_angle.so` | `eee504738cc482eb4e3411af5dbc09b4a182dd453a89afbf4105258bd6e90936` |
| `libGLESv2_angle.so` | `eb17f2857b7d2444fb937106733fb2e906e7bac47313e941b25a75c383c222e2` |
