# apklayer HALs, host side

Normal Linux code (glibc, Meson) behind the Android-side HAL services in `aosp/hals/`. One directory per domain, each with
`include/apklayer/<domain>.h` (the plain C API: no C++, no Android types, buffers as fds, fences as sync_file fds, explicit
callbacks) and the implementation. Vendor-neutral by construction: GBM, EGL, Wayland, PipeWire, V4L2, D-Bus services.
See `aosp/hals/README.md` for the domain list and `docs/ARCHITECTURE.md`.
