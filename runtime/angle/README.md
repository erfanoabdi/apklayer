# ANGLE Android libraries

These x86_64 and x86 Android EGL/GLES libraries are built from Google ANGLE revision
`f0d596b6f9e3dfe7a068786790516c035b1dfde4` for Android API 37. The GN build enables Vulkan and uses the `_angle` library suffix. The source includes apklayer patches `patches/angle/0001-use-host-safe-tls-for-vulkan.patch`, `0002-disable-nvidia-android-deferred-swapchain.patch` and `0003-disable-nvidia-android-present-fences.patch`; see `docs/PATCHES.md`. ANGLE's Android GLES 3.2 exposure is enabled at runtime with `ANGLE_FEATURE_OVERRIDES_ENABLED=exposeES32ForTesting`.

ANGLE is packaged for x86 and x86_64 Android guests on x86_64 Linux hosts. There is no ARM64 ANGLE build or runtime payload.

Meson packages the files under `runtime/angle/vendor` into `/usr/lib/apklayer/angle/vendor`. Runtime setup links them into `/system/lib` and `/system/lib64`, where Android 17's EGL loader looks for the `angle` suffix when `ro.hardware.egl=angle`.

## Linux host libraries for GBM transfers

The x86_64 Linux ANGLE EGL/GLES libraries serve both the host graphics HAL's CPU buffer transfers and, in ANGLE mode, SurfaceFlinger's compositor EGL and GLES. Android app GLES continues to use the Android-target libraries above. These host libraries are built from the same revision with `target_os="linux"`, `target_cpu="x64"`, `angle_enable_vulkan=true`, `angle_enable_gl=false`, and `angle_use_custom_libvulkan=false`; the last setting makes ANGLE use the system Vulkan loader rather than looking for a private loader beside ANGLE. The Vulkan backend imports Android DMA-BUFs through GBM/EGL, samples them on the GPU, and uploads CPU pixels through GLES.

Meson packages the host files under `/usr/lib/apklayer/angle/host`. The CLI passes the EGL path to the graphics HAL and the SurfaceFlinger process when ANGLE is selected. The compositor's Android EGL shim supplies Android native-buffer compatibility and forwards EGL/GL to Linux ANGLE. Linux ANGLE uses the same source revision with `patches/angle/0004-cache-linux-dma-buf-modifiers.patch` to share DRM modifier lists and `patches/angle/0005-fix-linux-rgb565-dma-buf-channel-order.patch` to preserve RGB565 channel order in DMA-BUF imports.

ANGLE is licensed under the BSD 3-Clause license. The binaries are built from the Google ANGLE source, not copied from the Waydroid NVIDIA Android 13 image.

SHA-256:

| ABI | Library | SHA-256 |
|---|---|---|
| x86_64 | `libEGL_angle.so` | `ae78e0ac613c94f3426dd55b46429c5d68fe662c70ea89f7f4c56b9908ad1a06` |
| x86_64 | `libGLESv1_CM_angle.so` | `63c2e858fb1d99aba9e896bfa1f7c31b789ec6d20da71fec74a64751bd213999` |
| x86_64 | `libGLESv2_angle.so` | `699d08e3eefe00aee74753efee4813172d0cb9c0147e6e432c2f751b303b79c2` |
| x86 | `libEGL_angle.so` | `fd23048764fe33787f56cfc9927d7134e6a317c5924596785a57a347c36ebfc6` |
| x86 | `libGLESv1_CM_angle.so` | `e7af29754e1cd5038be6507e19968120ce2eaacf333fa63658ea18c513e9274a` |
| x86 | `libGLESv2_angle.so` | `0e85fc0b65759315280135995b29bf1e808849fd97dc1bad968f2659575ab13c` |

Linux host SHA-256:

| Library | SHA-256 |
|---|---|
| `libEGL_angle.so` | `eee504738cc482eb4e3411af5dbc09b4a182dd453a89afbf4105258bd6e90936` |
| `libGLESv2_angle.so` | `a108cdb97a90c2c9052623c77fd7a8f32863d51187a6f7326fd8d087e20dc02b` |
