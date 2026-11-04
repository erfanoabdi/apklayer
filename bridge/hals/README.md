# apklayer HALs, host side

Normal Linux code (glibc, Meson) behind the Android-side HAL services in `aosp/hals/`. One directory per domain, each with
`include/apklayer/<domain>.h` (the plain C API: no C++, no Android types, buffers as fds, fences as sync_file fds, explicit
callbacks) and the implementation. Vendor-neutral by construction: GBM, EGL, Wayland, PipeWire, V4L2, D-Bus services.
See `aosp/hals/README.md` for the domain list and `docs/ARCHITECTURE.md`.

`media/` provides native GStreamer decoder samples for the planned Codec2 frontend. Build requires gstreamer-app/video/allocators >=1.24 development packages. `b/bridge/hals/media/codec-test` encodes real VP8 packets and verifies native YUV decode, PTS, EOS, empty-output timeout and retained sample ownership at two sizes. No hardware decoder was available for the dmabuf-output branch; it remains unverified.

## Camera backends
`camera/`: libapklayer-camera-<backend>.so behind the camera provider (aosp/hals/camera), the C ABI in `include/apklayer/camera.h` (probe, init with
key=value options, list, open/start/stop with frames on the backend's thread). `fake` (colour bars by default; `source=<image or video file>`
through GStreamer; `cameras=back,front`; `fps=`), `pipewire` (the desktop's Video/Source nodes with media.role Camera, raw formats; `role=any`).
The instance picks one in providers.toml: `apklayer providers set camera <backend> [key=value...]`.
`sensors/`, `gnss/`, `vibrator/`: the hardware providers' backends behind the AOSP-example fronts (`aosp/hals/<domain>`): each a library
`libapklayer-<domain>-<backend>.so` exporting the domain's C ABI (`include/apklayer/<domain>.h`, with `probe()`: <0 unusable here, 0 usable but
never a default, >0 quality). Only `fake` exists so far (fixed sensor values, a fixed point or GPX replay, a logged vibrator), configured per
instance in `providers.toml` (`apklayer providers set`); `meson test -C b providers-fake` checks the fakes' option parsing.
