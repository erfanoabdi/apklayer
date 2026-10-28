# Changelog

Dated entries, newest first by section; this is where status history lives (STATUS.md stays short, docs/PLAN.md holds decisions and open work).
Builds in `~/los24` are logged here before they run (section "Build log"). Older history: `docs/history/`.

## 2026-10-28 night: hang fix, Keystore, system config, system providers, devices
- **App hang fixed**: `HardwareRenderer.nSetStopped` blocked forever when HWUI's CommonPool left a task queued while both workers slept; `bionic_pthread_cond_signal` now broadcasts (patch step 10): 25 of 25 runs pass, 2 hangs in 11 before. Dynamic receivers are delivered through the app thread (ordering across broadcasts).
- **Keystore**: RSA and EC key pairs with certificates next to the AES/HMAC keys, persistent (`tests/vm/keystore.sh`).
- **Permissions and system features from the build** (`HostSystemConfig`): framework permission levels, the privapp allowlist, default grants, the vendor feature file; DocumentsUI runs (`tests/corpus`).
- **System providers**: the tree's Contacts, Calendar, Download, UserDictionary, ExternalStorage, TelephonyProvider, SettingsProvider and DocumentsUI install (`scripts/install-system-apps.sh`); ContactsProvider answers another app (`tests/vm/sysprov.sh contacts`).
- **Known failures fixed**: audio HAL (PipeWire 1.6 `get_time_n` -EIO) so `native-player.sh` passes; WebView (rebuilt linker with IRELATIVE, setup script) so `webview.sh` passes; `hub-services.sh` passes.
- **Devices** (`tests/vm/devices.sh`): network from the default route (`ip monitor`), battery and power from sysfs and udev (published by the hub), USB from sysfs, location from GeoClue, the file chooser portal for document pickers (`tests/vm/portal.sh`).
- **Corpus**: `tests/corpus/run.py` installs, launches and screenshots each app in `apps.txt`; first results in `tests/corpus/results.md`.

## 2026-10-28: C++ tool, property readiness, test config
- `bin/apklayer` is a wrapper around the C++ tool in `cli/` (install/run/stop/hub/env, stack daemon with pidfd supervision; `stack` and `exec` for tests). `scripts/native-env.sh` and
  `scripts/arm64-stack.sh` are gone. Readiness is a system property of the instance (`apklayer.stack.ready`, `apklayer.hub.ready`) waited on with `aprop wait`; component hosts exec the JVM.
- Logs and markers moved from `/tmp/apklayer-stack` to `~/.local/state/apklayer/<instance>/`.
- Tests: VM host and password from the untracked `tests/vm/local.conf`, repo copy at `~/apklayer`, passwordless sudo on the test VM (`sudo -n`), patience limits of 60-120 s for log waits.
- Fixes: a hub start during a service's creation no longer makes a second instance (`Services.startFromHub`); the BinderProxy shadow rethrows the wrapped `DeadObjectException`
  so the provider retry works; `AppHost` text/clipboard moved to `HostTextBridge`; `vm-binder-setup.sh` creates nodes through binder-control.
- Docs: STATUS.md (short), CHANGELOG.md (this file), docs/PLAN.md (decisions and open work), docs/AOSP-BUILDS.md (how-to).

Older dated notes (GPU follow-up, TextureView, MC3, Vulkan checkpoints, OTR2, route cleanups): `docs/history/CHANGELOG-2026-10-27-notes.md`.

## Build log (los24, incremental builds; earlier ones in `docs/history/BUILD-LOG-2026-10-26_27.md`)

### 2026-10-28 — Vulkan host-loader frontend (incremental, authorized)
- Reuse original Android libvulkan and its WSI, adding vulkan.apklayer HAL which delegates device commands to host libvulkan.so.1. Keep both loaders' dispatch state separate through a host-only side table; preserve host handles/vtables. No new Vulkan renderer.
- Compile existing libvulkan sources affected by driver.h and relink its existing arm64 variant; compile the new HAL with the existing generated EGL toolchain flags (new Android.bp records its reproducible module). No graph regeneration, full dependency rebuild or cleaning. Test original loader enumeration, actual device commands/readback, repeated destruction and truthful lack of Android swapchain support on the current CPU-only driver. Android native-buffer import/sync and hardware presentation remain separate required steps, not claimed by this checkpoint.
- Result: original loader, vulkan.apklayer and libapklayer_vktest built and SHA-verified on VM. Loader uses a separate installed filename to retain the unmodified device artifact. Native probe and real APK/JNI probe pass six cycles of image operations/readback and explicit/implicit command-buffer cleanup; Vulkan CPU capabilities reported honestly. Final SHA values are recorded in deps/android-tree-files.lock; new tool artifacts originate in out/apklayer-vulkan and are reproducible with the checked-in build script.

### 2026-10-28 — libapklayer_egl: log eglCreateContext failures (incremental, authorized)
- OTR2's bgfx reports "Failed to create context"; log the attributes and EGL error of a failed eglCreateContext. `orb-build-module.py libapklayer_egl hals/graphics/egl Egl.cpp` (existing compile/link/strip/copy commands, no graph change, no clean).
- Result: built and installed on the VM (sha256 426b674df529d9ef...). It shows OTR2's request: ES 3.1 context with EGL_CONTEXT_FLAGS_KHR (BAD_ATTRIBUTE), then ES 3.1 alone (BAD_MATCH on the ES 3.0 virgl host).

### 2026-10-28 — libapklayer_egl: native visual id as a HAL pixel format (incremental, authorized)
- eglGetConfigAttrib(EGL_NATIVE_VISUAL_ID) returned the host's DRM fourcc; engines pass it to ANativeWindow_setBuffersGeometry, so OTR2's bgfx window buffers were requested with format 0x34324241 and every allocation failed. Map the fourcc to the HAL pixel format. `orb-build-module.py libapklayer_egl hals/graphics/egl Egl.cpp` (existing commands, no graph change, no clean).
- Result: built and installed on the VM (sha256 81ad2df1221f4545...). OTR2's SurfaceView buffers allocate; tests/vm/gpu.sh and tests/vm/texture-view.sh pass.

### 2026-10-28 — libapklayer_egl: eglSetDamageRegionKHR answered locally (incremental, authorized)
- HWUI aborts when eglSetDamageRegionKHR fails; the forwarded call returned EGL_BAD_SURFACE on the zink host driver (the handle is ours, not the host's). Every swap presents the whole buffer, so it returns EGL_TRUE. `orb-build-module.py libapklayer_egl hals/graphics/egl Egl.cpp` (existing commands, no graph change, no clean).

### 2026-10-28 — vulkan.apklayer: VK_ANDROID_native_buffer over the host driver (incremental, authorized)
- Implements the Android swapchain path in the Vulkan HAL frontend (Driver.cpp): dma-buf image import from our gralloc handles (explicit DRM layout), vkAcquireImageANDROID/vkQueueSignalReleaseImageANDROID through sync-fd semaphores, gralloc usage queries, device creation with the host extensions that carry it. Only vulkan.apklayer is recompiled/relinked with `scripts/orb-build-vulkan.py` (existing commands; libvulkan and libapklayer_vktest unchanged or rebuilt the same way, no graph change, no clean).
- Presentation test added: tests/vm/vulkan-present.sh (hello-vulkan's Present activity). libapklayer_vktest rebuilt for it with the same script.

### 2026-10-28 — libapklayer_servicetool: `wait NAME` (incremental, authorized)
- The C++ `apklayer` tool's stack supervisor needs event-driven readiness (no `sleep` loops): `wait NAME` blocks in `waitForService` until the service registers. `orb-build-module.py libapklayer_servicetool tools servicetool.cpp` (existing commands, no graph change, no clean).
- Result: built and installed on the UTM VM; `wait NAME` prints "found" (btload exit status is not the tool's, so the supervisor reads the printed line).
