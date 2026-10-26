# apklayer HALs, Android side

One directory per HAL domain, named as AOSP names them. Each holds the AIDL frontend (a thin service/library built here by Soong
against the tree's real AIDL interfaces) that calls the host side through a small C API (`bridge/hals/<domain>/`). The Android
side translates Android calls and types; everything that touches the desktop lives in the host side.

| Directory | AOSP interface | Host side (`bridge/hals/`) | Status |
|---|---|---|---|
| `graphics/allocator`, `graphics/mapper` | `android.hardware.graphics.allocator`, stable-C `IMapper` | `graphics/` over libgbm, dmabuf | host side done and tested (`bridge/hals/graphics`); Android side next (P7.1) |
| `graphics/composer` | `android.hardware.graphics.composer3` | `graphics/` over Wayland `zwp_linux_dmabuf_v1`, real vsync | planned (P7.1) |
| `camera` | `android.hardware.camera.provider` | PipeWire / V4L2 | planned |
| `gnss` | `android.hardware.gnss` | GeoClue / gpsd | planned |
| `vibrator` | `android.hardware.vibrator` | force feedback (evdev FF), logind haptics | planned |
| `sensors` | `android.hardware.sensors` | iio-sensor-proxy / IIO | planned |
| `power`, `thermal`, `health` | optional answers for SurfaceFlinger and the framework | UPower, thermal zones | later |
| `input` | InputFlinger inputs | evdev / the shell's Wayland input | later |
| `audio` | `android.hardware.audio.core` | PipeWire | later (today: apklayer-media) |

## `graphics/vdisplay` (not a HAL: a per-app virtual display sink)
One process per app window: a SurfaceFlinger virtual display whose output BufferQueue it consumes, handing the finished dmabufs to
apklayer-shell through the host display HAL. Written new because nothing local fits: the composer is the primary display's HWC (a
service SurfaceFlinger calls, client composition only, no virtual displays by design), `servicetool` is a one-shot client, and AOSP's
`screenrecord` (the same virtual display + consumer pattern) is built around a video encoder. It is a `cc_library_shared` exporting
`main` only because that is how every Android-side process here is run (`btload --main`).

