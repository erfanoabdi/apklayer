# apklayer HALs, Android side

One directory per HAL domain, named as AOSP names them. Each holds the AIDL frontend (a thin service/library built here by Soong
against the tree's real AIDL interfaces) that calls the host side through a small C API (`bridge/hals/<domain>/`). The Android
side translates Android calls and types; everything that touches the desktop lives in the host side.

| Directory | AOSP interface | Host side (`bridge/hals/`) | Status |
|---|---|---|---|
| `graphics/allocator`, `graphics/mapper` | `android.hardware.graphics.allocator`, stable-C `IMapper` | `graphics/` over libgbm, dmabuf | host side done and tested (`bridge/hals/graphics`); Android side next (P7.1) |
| `graphics/composer` | `android.hardware.graphics.composer3` | `graphics/` over Wayland `zwp_linux_dmabuf_v1`, real vsync | planned (P7.1) |
| `media` | `android.hardware.media.c2` | `media/` over native GStreamer | VP8/VP9/AVC native, AIDL, app MediaCodec and SurfaceView tested; other codecs/audio next |
| `camera` | `android.hardware.camera.provider` (AIDL V3, `internal/1`) | `camera/`: backends `fake` (bars, image, video file), `pipewire` | Goldfish's provider (`camera/goldfish/`, copied) with `BackendCamera`; `camera/services` runs cameraserver; `tests/run camera` |
| `gnss` | `android.hardware.gnss` | GeoClue / gpsd | planned |
| `vibrator` | `android.hardware.vibrator` | force feedback (evdev FF), logind haptics | planned |
| `sensors` | `android.hardware.sensors` | iio-sensor-proxy / IIO | planned |
| `gnss` | `android.hardware.gnss` (AOSP's IGnss example, its fixes from a backend; `--client` feeds HostLocation's gps provider) | `gnss/`: fake (fixed point, GPX replay); GeoClue / gpsd next | fake tested (`tests/vm/providers.sh`, 2026-11-04) |
| `vibrator` | `android.hardware.vibrator` (AOSP's IVibrator example plus a backend; the hub's `vibrator_manager` plays app vibrations on it) | `vibrator/`: fake (logs every command); feedbackd / evdev FF next | fake tested (`tests/vm/providers.sh`, 2026-11-04) |
| `sensors` | `android.hardware.sensors` (AOSP's ISensors example with a backend's sensors); `sensors/services`: AOSP's SensorService as a stack process | `sensors/`: fake (fixed values); iio-sensor-proxy / sensorfw next | fake tested (`tests/vm/providers.sh`, 2026-11-04) |
| `power`, `thermal`, `health` | optional answers for SurfaceFlinger and the framework | UPower, thermal zones | later |
| `input` | InputFlinger inputs | evdev / the shell's Wayland input | later |
| `audio` | `android.hardware.audio.core` | PipeWire | host S16/F32 PCM and real AIDL/FMQ output verified; AudioFlinger/Policy and app JNI next |

## Hardware providers (sensors, gnss, vibrator)
Each front is AOSP's example HAL built from its own sources (hardware/interfaces patch 0002) with one subclass that asks a backend library through a
small C ABI (`bridge/hals/<domain>/include/apklayer/<domain>.h`, `probe()` for availability). The stack starts a front only for a domain configured in
`<instance root>/providers.toml` (`apklayer providers set <domain> <backend> [key=value...]`), with that backend on the front's `APKLAYER_HOST_LIBS`, and
declares its vintf fragment for that stack; a changed backend or option restarts only that front (SIGHUP to the stack daemon).

## `graphics/vdisplay` (not a HAL: a per-app virtual display sink)
One process per app window: a SurfaceFlinger virtual display whose output BufferQueue it consumes, handing the finished dmabufs to
apklayer-shell through the host display HAL. Written new because nothing local fits: the composer is the primary display's HWC (a
service SurfaceFlinger calls, client composition only, no virtual displays by design), `servicetool` is a one-shot client, and AOSP's
`screenrecord` (the same virtual display + consumer pattern) is built around a video encoder. It is a `cc_library_shared` exporting
`main` only because that is how every Android-side process here is run (`btload --main`).
The sink also registers its display token as `apklayer.vdisplay.<layer stack>`; `apklayer screenshot` runs `aosp/tools/screenshot.cpp`, which captures that display
through SurfaceFlinger's own `captureDisplay` (the screencap path; the capture buffer is CPU-readable through the mapper's GBM map) and writes the PNG.


## Media decoder backend (in progress)
`bridge/hals/media` reuses the existing GStreamer decoding engine as native C because the Python/RGBA-file protocol cannot retain decoded YUV samples or dmabuf ownership across Codec2 output lifetimes. Its plain C API provides bounded compressed input, real decoder discovery, timestamps/EOS and reference-counted output samples. Hardware samples expose borrowed dmabuf planes/modifiers; software samples can be mapped for the necessary copy into gralloc. VP8/VP9 software decode, Codec2 AIDL transport and app MediaCodec buffer/Surface output are verified. Hardware decoder dmabuf import and wider media/audio remain incomplete.

`media/codec2` reuses AOSP’s AIDL ComponentStore and SimpleC2Component/Interface plumbing. The new adapter is necessary because the supplied vendor store is empty and the software store directly loads Android decoders. Integration advertises available host VP8, VP9 profile 0 and 8-bit AVC and copies software I420 output into the mapper’s real flexible-YUV gralloc layout. Hidden references and one-visible-picture VP9 superframes are supported. Multiple displayed pictures use cloneAndSend with final-picture EOS; the AVC adapter completes reordered pending work by PTS on the existing component handler; hardware buffer import and other codecs follow; the interface and three-frame real gralloc probe pass `tests/run codec2`; this frontend is not yet part of default startup.

`media/services` starts the existing AOSP MediaPlayerService and ResourceManagerService using the same registration calls as main_mediaserver.cpp. MediaCodec needs those process boundaries for resource ownership and codec discovery. A new btload entrypoint is required because the tree ships mediaserver as an executable; playback/policy stay in the original service libraries. The --extractor entrypoint runs the existing MediaExtractorService in a separate process with original MP4/WebM plugins; native callback-source MP4 metadata, seek and extracted-sample decoding are verified.

## Audio output
`audio` derives directly from AOSP Module and reuses StreamCommonImpl, StreamOut and the existing FMQ worker state machine in libaudioserviceexampleimpl. The only new driver delegates PCM and timing to the host PipeWire API. An unprivileged transfer-worker creator selects normal host priority; PipeWire owns graph scheduling. No stub stream is instantiated. Only tested synchronous 48k mono/stereo S16/F32 output profiles are advertised; capture, offload, MMAP and asynchronous output return errors. Unsupported hardware master controls return errors so AudioFlinger can apply software controls. The version script exports only main, allowing unused example implementations to be discarded. `tests/run audio-aidl` verifies binder configuration, real FMQ PCM capture, presentation/drain, standby, pause/flush/replay and complete teardown. The stack starts the HAL, AudioFlinger and AudioPolicy whenever their libraries are installed in the instance's Android library directory.

`audio/services` uses the original AudioFlinger and AudioPolicy archives and startup order. The original effects factory starts with an empty catalog until actual plugins are staged; original policy volume XML comes from the tree. The native service route probe passes, while AudioTrack sample playback and app JNI remain unfinished. `patches/aosp/hardware/interfaces/0001-audio-effect-factory-filegroup.patch`, `frameworks/av/0002-mediametrics-vendor-visibility.patch` and `frameworks/native/0002-no-system-server-clients.patch` make the original factory build-reusable and lets native clients return actual absence for system_server services intentionally kept in Java shadows.

Apply `patches/apply aosp <AOSP tree>` before generating Soong rules for audio services; the marked filegroup reuses original source and the client patches preserve upstream absent-service behavior in standalone mode.
