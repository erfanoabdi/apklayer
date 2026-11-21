# ANGLE Android libraries

These x86_64 and x86 Android EGL/GLES libraries are built from Google ANGLE revision
`f0d596b6f9e3dfe7a068786790516c035b1dfde4` for Android API 37. See `docs/PATCHES.md` for the APKLayer patches applied to this revision.

For ANGLE's Vulkan backend, APKLayer requests GLES 3.2 exposure by setting `exposeES32ForTesting` in the host's `ANGLE_FEATURE_OVERRIDES_ENABLED` environment variable and Android's `debug.angle.feature_overrides_enabled` property.

ANGLE is packaged for x86 and x86_64 Android guests on x86_64 Linux hosts. On aarch64 hosts Meson installs the arm64 build from `runtime/angle/aarch64/{host,vendor/lib64/egl}` when those files exist (see "aarch64" below); without them the package has no ANGLE and `APKLAYER_GLES_ANGLE=on` stops with "ANGLE GLES libraries are not installed". ANGLE stays off by default there (`auto` selects it only on NVIDIA).

Meson packages the files under `runtime/angle/vendor` into `/usr/lib/apklayer/angle/vendor`. Runtime setup links them into `/system/lib` and `/system/lib64`, where Android 17's EGL loader looks for the `angle` suffix when `ro.hardware.egl=angle`.

When ANGLE is selected, APKLayer uses its Vulkan backend if Vulkan is enabled and presentation is available. Otherwise the app stays on ANGLE and uses its OpenGL ES backend, which opens APKLayer's native EGL shim and supports up to GLES 3.1. SurfaceFlinger and buffer transfers use native EGL in this mode. The GLES switch's Off setting selects the native driver directly for apps.

## Linux host libraries for GBM transfers

The x86_64 Linux ANGLE EGL/GLES libraries serve the host graphics HAL's CPU buffer transfers and SurfaceFlinger's compositor EGL and GLES when ANGLE uses Vulkan. Android app GLES uses the Android-target libraries above. The host build uses the system Vulkan loader. The Vulkan backend imports Android DMA-BUFs through GBM/EGL, samples them on the GPU, and uploads CPU pixels through GLES.

Meson packages the host files under `/usr/lib/apklayer/angle/host`. The CLI passes the EGL path to the graphics HAL and the SurfaceFlinger process when ANGLE uses Vulkan. The compositor's Android EGL shim supplies Android native-buffer compatibility and forwards EGL/GL to Linux ANGLE. Linux ANGLE uses the same source revision with `patches/angle/0004-cache-linux-dma-buf-modifiers.patch` to share DRM modifier lists and `patches/angle/0005-fix-linux-rgb565-dma-buf-channel-order.patch` to preserve RGB565 channel order in DMA-BUF imports.

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
| x86_64 | `libGLESv2_angle.so` | `84d93594591d6072907444172ce8bfe23e702c990a52eca2eed2850313db391d` |
| x86 | `libEGL_angle.so` | `fd23048764fe33787f56cfc9927d7134e6a317c5924596785a57a347c36ebfc6` |
| x86 | `libGLESv1_CM_angle.so` | `e7af29754e1cd5038be6507e19968120ce2eaacf333fa63658ea18c513e9274a` |
| x86 | `libGLESv2_angle.so` | `57b0dd47d062f72248fd45136b4df5467d012125880921e9cffee894e335727f` |

Linux host SHA-256:

| Library | SHA-256 |
|---|---|
| `libEGL_angle.so` | `eee504738cc482eb4e3411af5dbc09b4a182dd453a89afbf4105258bd6e90936` |
| `libGLESv2_angle.so` | `eb17f2857b7d2444fb937106733fb2e906e7bac47313e941b25a75c383c222e2` |

## aarch64 (Linux host and arm64 Android guest)

Same revision and patches (`patches/apply angle <checkout>` applied all seven cleanly). Google ships no Linux arm64 clang or gn, so the build runs on an x86_64 Linux machine and cross-compiles with Chromium's clang and the Debian bullseye arm64 sysroot (`python3 build/linux/sysroot_scripts/install-sysroot.py --arch=arm64`); `gclient sync --no-history` with `target_os = ["android"]`. Use the checkout's own `third_party/ninja/ninja` (1.12; Ubuntu 22.04's 1.11 cannot read the generated files) and `buildtools/linux64/gn gen`.

| Output directory | Additional GN arguments (plus the common ones above) | Ninja targets |
|---|---|---|
| `out/LinuxHost` | `target_os="linux"`, `target_cpu="arm64"`, `angle_use_custom_libvulkan=false`, `angle_build_tests=false` | `libEGL libGLESv2` |
| `out/Android64` | `target_os="android"`, `target_cpu="arm64"`, `android_ndk_api_level=37`, `angle_enable_context_mutex=false` | `libEGL libGLESv2 libGLESv1_CM` |

Build time on a 17-core x86_64 VM: LinuxHost 912 s (4139 steps), Android64 279 s. The files are copied to `runtime/angle/aarch64/host/` and `runtime/angle/aarch64/vendor/lib64/egl/` (not committed to git; the checksums below identify them). The arm64 Android set (the three `vendor` libraries, 7.2 MB) also ships in the prebuilts branch as `arm64/angle/vendor/lib64/egl/` (runtime 0.5.6): Meson takes it from there when `runtime/angle/aarch64/vendor` is absent (`-Dprebuilts`), so the deb and click installs carry it under `angle/vendor/lib64/egl` on aarch64 even without the 22 MB host libraries (those are not shipped; only a desktop with ANGLE on Vulkan needs them). The GN output directory is `apklayer-anglearm2/build/aarch64/android-gn/`. The Soong build of the tree's `external/angle` (branch anglearm) has no patch 0007 (the GLES backend), so it cannot run ANGLE on hosts without Vulkan presentation (virgl): use the GN build for the vendor libraries.

| ABI | Library | SHA-256 |
|---|---|---|
| aarch64 host | `libEGL_angle.so` (493200 B) | `b5a33d48820b3751685c7f39914482a73828d580961e63f7b03f1533859692a5` |
| aarch64 host | `libGLESv2_angle.so` (22287032 B) | `cdb095c33bb7ef6bc5686d588fce2ea6235f52d639a8f073c8eb3cd3a0cb76ec` |
| arm64 Android | `libEGL_angle.so` (311856 B) | `7308fc76e0aede088a884d5534f404a03b72007de9721c561498b56dcf74d9e7` |
| arm64 Android | `libGLESv1_CM_angle.so` (299792 B) | `36250a5be21ba813f58438107542108a3df9b1e7dc4ebb1db4c4b4e0a8617646` |
| arm64 Android | `libGLESv2_angle.so` (6587592 B) | `e3b49cf723ffb709d6116fb80e0eafded83e9e4656ace8135aae941343b3529f` |

Verified 2026-11-21 (`APKLAYER_GLES_ANGLE=on`, JDK 25, prebuilts 99acf395): Parallels (virgl) and UTM (virgl, Venus present but not used for presentation): groups gpu, gpu-window, gles1, window-formats, vulkan pass on ANGLE's OpenGL ES backend (`GL_RENDERER "ANGLE (Mesa, virgl, ...)"`). On a Venus host (UTM) the apps' ANGLE runs on its Vulkan backend (swapchain memory allocated by Venus, Driver.cpp) while SurfaceFlinger and the buffer transfers stay on the native EGL: the Linux ANGLE compositor (Vulkan) aborts on the virgl buffers the allocator makes. Verified 2026-11-21 on UTM: gpu-smoke, gpu-window, window-formats, gles1, vulkan with `APKLAYER_GLES_ANGLE=on`. The Linux host ANGLE on Vulkan (compositor path) is still unverified on a real aarch64 desktop GPU.


### arm64 ANGLE on a Halium GPU (Volla X23, Mali-G57 MC2, 2026-11-21)

`gles-angle=on` with `graphics = device` runs ANGLE's Vulkan backend straight on the vendor's Vulkan driver (Mali, Vulkan 1.1.177; `libGLES_mali.so` carries `VK_ANDROID_external_memory_android_hardware_buffer`, `VK_ANDROID_native_buffer`, `VK_KHR_external_memory_fd`, `VK_EXT_image_drm_format_modifier`). Only the Android-target libraries (GN `Android64`, patch 0007 build) are used: the device has no host libEGL/GBM path, and the Linux host libraries, `APKLAYER_ANGLE_HOST_EGL` and the compositor's host EGL are not involved (`APKLAYER_ANGLE_DEVICE`, cli/env.cpp). `auto` keeps the native Mali GLES. SurfaceFlinger runs ANGLE too (`ro.hardware.egl=angle` is one property for the instance): `ANGLE: Renderer (Vulkan 1.1.177 (Mali-G57 MC2))` in both.
Two bugs found there (both in the CLI's environment for ANGLE, fixed): the ANGLE block overwrote `BIONIC_VENDOR_LD_LIBRARY_PATH` (the VNDK directories the Mali driver's dependencies need: SurfaceFlinger's `failed to link libgralloc_extra.so`, ANGLE `vk_renderer.cpp initialize` error -3) and `BIONIC_VENDOR_SHARED_LIBS` (dropping `libhidlbase.so`: the driver's gralloc/HIDL client then crashed with a null pointer in the render thread's first frame, glbench/UiBench/hwbitmap alike).
Checked on the X23 (instance of its own, runtime copy with the GN libraries, click 0.5.5): UiBench list (PNG identical to native GLES), hello.hwbitmap (all checks pass, PNG identical), hello.gles1 (`GL_RENDERER "ANGLE (ARM, Vulkan 1.1.177 (Mali-G57 MC2 ...)"`, GLES 1.1 center ff00ff, PNG identical), hello.glbench (GLES 3.2; clear 56.6 fps against 59.9 native, the 719x1424 fill 4.2 fps on both: display-bound), hello.vulkan.arm64 (native Vulkan next to ANGLE) draws. One stall seen once (hello.gles1 started right after `stop` of the previous app: the shell connection was lost and the run never ended); the next run passed, not reproduced.

### ANGLE host libraries on the X23 (libhybris Vulkan, 2026-11-21)

The Linux aarch64 host libraries (sha256 above) run on the X23's libhybris Vulkan; the stack around them does not (yet).
- Vulkan on the phone: `/usr/lib/aarch64-linux-gnu/libvulkan.so.1` is libhybris's front end (no ICD json, no Mesa Vulkan ICD) over the Mali vendor driver: one device `Mali-G57 MC2`, Vulkan 1.1.177. Instance extensions: only `VK_KHR_surface`, `VK_KHR_wayland_surface` (no xcb/xlib/headless) and the 1.1 capability ones. Device extensions (68) include `VK_EXT_image_drm_format_modifier`, `VK_EXT_external_memory_dma_buf`, `VK_KHR_external_memory_fd`, `VK_KHR_swapchain`, `VK_ANDROID_external_memory_android_hardware_buffer`, `VK_KHR_external_semaphore_fd`, `VK_EXT_queue_family_foreign`. Hybris EGL (`libEGL.so.1` meta over `libEGL_libhybris`): Android EGL 1.4 with `EGL_ANDROID_image_native_buffer` and `EGL_HYBRIS_native_buffer2`, no `EGL_EXT_image_dma_buf_import`; irrelevant to the host ANGLE, which imports through Vulkan. GBM: Mesa's `libgbm.so.1` opens `/dev/dri/card0` (mediatek-drm, display only: no render node) and allocates linear dumb buffers (modifier INVALID); `/dev/dma_heap/*` is root-only.
- Result (`tests/vm/angle-host-probe.c`, built on Parallels, run on the phone): ANGLE's default Linux display asks for `VK_KHR_xcb_surface` and fails (`eglInitialize` 0x3001); with a GBM display (what the compositor's Egl.cpp passes) or the surfaceless platform (`EGL_PLATFORM_ANGLE_NATIVE_PLATFORM_TYPE_ANGLE` = `EGL_PLATFORM_SURFACELESS_MESA`) with `ANGLE_DEFAULT_PLATFORM=vulkan` it initializes: `GL_RENDERER ANGLE (ARM, Vulkan 1.1.177 (Mali-G57 MC2 ...))`, GLES 3.2, pbuffer clear and readback correct, `EGL_EXT_image_dma_buf_import(_modifiers)` advertised (AB24: linear plus 32 Arm AFBC/AFRC modifiers), a GBM dma-buf imports with and without an explicit linear modifier, binds as a texture, the framebuffer is complete and a GPU clear into it is read back through a second import. The same under the stack's environment (LD_PRELOAD, bionic_translation library path).
- The stack on the X23 (own instance, `providers set graphics apklayer`, `APKLAYER_GLES_ANGLE=on`, `APKLAYER_ANGLE_HOST_EGL`, 2026-11-21, branch hybridge): (1) the CLI never reached Vulkan: no `vulkaninfo` on a phone, so `hostVulkan` returned "" and ANGLE fell back to its GL backend on llvmpipe; without vulkaninfo the CLI now asks `libvulkan.so.1` itself (`apklayer vulkan-probe`, cli/vkprobe.cpp; hybris loader and Khronos loader alike; on the MacBook it gives 1.4 as vulkaninfo's path does). (2) SurfaceFlinger (a btload process) died with a null call (`pc=0`) in ANGLE's `eglInitialize` after hybris printed `libvulkan.so not found`. Cause (strace of the process): the LD_PRELOAD path layer (bridge/native/paths_preload.c) maps `/system` and `/vendor` into the runtime's android-root for every glibc caller, libhybris's linker included, so hybris's `open("/system/lib64/libvulkan.so")` hit the runtime's directory (no such file) instead of the phone's. Not a symbol clash (btload exports `android_create_namespace` & co., but hybris does not reach them: a forwarding build was tried and never called). Fix: opens whose caller is a libhybris object are not mapped (`hybris_open`, docs/PATCHES.md). SurfaceFlinger then logs `compositor EGL: Linux ANGLE (Vulkan)` and `OpenGL ES 3.2 (ANGLE ...) renderer ANGLE (ARM, Vulkan 1.1.177 (Mali-G57 MC2 ...))` and composes. (3) The app side (`vulkan.apklayer`, the Android loader's driver in the app process) crashed in `vkImportSemaphoreFdKHR`: libhybris's libvulkan.so.1 exports stubs for the extension functions that jump to null (checked: exported stub = SIGSEGV, `vkGetDeviceProcAddr`'s pointer works); Driver.cpp now takes the sync-fd and memory-fd functions from `vkGetDeviceProcAddr` first (`hostDeviceFd`). (4) Lomiri has no `zwp_linux_dmabuf_v1` (android_wlegl gralloc handles only): the shell maps a frame's dmabuf (the host allocator's GBM buffers on card0 are linear dumb buffers and map) and copies it into a wl_shm buffer (bridge/shell/dmabuf_shm.c; ABGR swapped to ARGB; only linear or unspecified modifiers; the zero-copy path stays wherever linux-dmabuf exists). Slower, correct.
- Evidence (X23, build/phone-shots/): UiBench on the host-ANGLE stack (compositor ANGLE on hybris Vulkan, apps ANGLE on Vulkan, 12 buffers imported, 13 frames copied to wl_shm): `hybridge-angle-hybris-uibench.png` (217 colours, the list drawn); hello.vulkan.Present presents solid frames through the Android swapchain (`hybridge-hello-vulkan-present*.png`, magenta 87%, then the next colour; 432 frames through the copy path); hello.hwbitmap passes its checks (HARDWARE copy, readback ff0000ff ffff8000, crops 50/50). `graphics = device` UiBench unchanged (frames reach the shell as gralloc buffers, same black `apklayer screenshot` as with the stock click: not a regression, not investigated). Needed beside the libraries when installed by hand: `libapklayer_hostGLESv2.so` (symlink to `libGLESv2_angle.so`, as meson.build installs it): without it SurfaceFlinger's GLES calls jump to null right after the GL version line.
- Not done: the Lomiri-level capture (screen state/PIN unknown: only SF's composed picture and the shell's frame counts were checked, not what Lomiri shows); frame rate of the copy path (not measured; card0's dumb buffers are probably write-combined, so reads are slow); hybris Vulkan is still not usable for the compositor on Sailfish (not tried); the host libraries are still not shipped (22 MB) and the CLI's `hostAngleEgl` default needs them under `angle/host`.
- Generic ICD hosts: the change touches only hosts without vulkaninfo; the x86_64 MacBook (Intel + AMD, Mesa) has it, so its path (vulkaninfo, dma-buf with modifiers, `APKLAYER_GLES_ANGLE=on`) is unchanged and the full groups were not rerun; an aarch64 generic-ICD desktop (Panfrost, Turnip, Honeykrisp) was not available. The new probe was run on the MacBook against the Khronos loader and agrees with vulkaninfo (1.4).
