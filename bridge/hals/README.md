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
The instance picks one with `APKLAYER_CAMERA="<backend> [key=value...]"` until providers.toml carries a camera domain.
